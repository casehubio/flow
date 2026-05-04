/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.flow.loader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.casehub.api.model.AllOfGoalExpression;
import io.casehub.api.model.AnyOfGoalExpression;
import io.casehub.api.model.Binding;
import io.casehub.api.model.Capability;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.ContextChangeTrigger;
import io.casehub.api.model.Goal;
import io.casehub.api.model.GoalBasedCompletion;
import io.casehub.api.model.GoalExpression;
import io.casehub.api.model.GoalKind;
import io.casehub.api.model.Milestone;
import io.casehub.api.model.Trigger;
import io.casehub.api.model.Worker;
import io.casehub.api.model.evaluator.JQExpressionEvaluator;
import io.casehub.engine.spi.CaseDefinitionRegistry;
import io.casehub.model.CaseCompletion;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jboss.logging.Logger;

/**
 * Scans classpath for YAML case definitions and registers them with the engine.
 *
 * <p>Looks for *.yaml and *.yml files in:
 *
 * <ul>
 *   <li>src/main/resources/casehub/
 *   <li>src/main/resources/cases/
 * </ul>
 *
 * <p>YAML definitions are converted to {@link CaseDefinition} and registered via {@link
 * CaseDefinitionRegistry}.
 */
@ApplicationScoped
public class YamlCaseDefinitionLoader {

  private static final Logger LOG = Logger.getLogger(YamlCaseDefinitionLoader.class);
  private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
  private static final String[] SCAN_PATHS = {"casehub", "cases"};

  @Inject CaseDefinitionRegistry caseDefinitionRegistry;

  void onStart(@Observes StartupEvent event) {
    LOG.info("Scanning classpath for YAML case definitions...");

    int count = 0;
    for (String scanPath : SCAN_PATHS) {
      count += scanAndRegister(scanPath);
    }
    LOG.infof("Loaded %d YAML case definition(s)", count);
  }

  private int scanAndRegister(String resourcePath) {
    int count = 0;
    try {
      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
      URL resourceUrl = classLoader.getResource(resourcePath);

      if (resourceUrl == null) {
        LOG.debugf("No resources found at path: %s", resourcePath);
        return 0;
      }

      URI uri = resourceUrl.toURI();

      Path path;
      FileSystem fileSystem = null;

      if (uri.getScheme().equals("jar")) {
        fileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap());
        path = fileSystem.getPath(resourcePath);
      } else {
        path = Paths.get(uri);
      }

      try (Stream<Path> paths = Files.walk(path)) {
        List<Path> yamlFiles =
            paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
                .toList();

        for (Path yamlFile : yamlFiles) {
          try {
            String relativePath = resourcePath + "/" + path.relativize(yamlFile);
            loadAndRegister(relativePath);
            count++;
          } catch (Exception e) {
            LOG.errorf(e, "Failed to load YAML definition from %s", yamlFile);
          }
        }
      }

      if (fileSystem != null) {
        fileSystem.close();
      }
    } catch (IOException | URISyntaxException e) {
      LOG.debugf("No YAML definitions found in %s", resourcePath);
    }

    return count;
  }

  private void loadAndRegister(String resourcePath) throws IOException {
    try (InputStream is =
        Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
      if (is == null) {
        LOG.warnf("Resource %s not found on classpath", resourcePath);
        return;
      }

      io.casehub.model.CaseDefinition schema =
          YAML_MAPPER.readValue(is, io.casehub.model.CaseDefinition.class);
      CaseDefinition definition = convertToApiModel(schema);

      caseDefinitionRegistry
          .registerCaseDefinition(definition)
          .await()
          .atMost(Duration.ofSeconds(10));

      LOG.infof(
          "Registered YAML case definition: %s/%s v%s from %s",
          definition.getNamespace(), definition.getName(), definition.getVersion(), resourcePath);
    }
  }

  private CaseDefinition convertToApiModel(io.casehub.model.CaseDefinition schema) {
    CaseDefinition def =
        new CaseDefinition(schema.getNamespace(), schema.getName(), schema.getVersion());
    def.setDsl(schema.getDsl());
    def.setTitle(schema.getTitle());

    // Convert capabilities
    Map<String, Capability> capabilityMap = new java.util.LinkedHashMap<>();
    if (schema.getSpec().getCapabilities() != null) {
      for (io.casehub.model.Capability sc : schema.getSpec().getCapabilities()) {
        Capability cap = new Capability(sc.getName(), sc.getInputSchema(), sc.getOutputSchema());
        cap.setDescription(sc.getDescription());
        capabilityMap.put(sc.getName(), cap);
        def.getCapabilities().add(cap);
      }
    }

    // Convert workers
    if (schema.getSpec().getWorkers() != null) {
      for (io.casehub.model.Worker sw : schema.getSpec().getWorkers()) {
        List<Capability> workerCaps =
            sw.getCapabilities().stream().map(capabilityMap::get).collect(Collectors.toList());

        Worker worker = new Worker(sw.getName(), workerCaps, sw.getWorkflowAsEmbedded());
        worker.setDescription(sw.getDescription());
        def.getWorkers().add(worker);
      }
    }

    // Convert bindings
    if (schema.getSpec().getBindings() != null) {
      for (io.casehub.model.Binding sr : schema.getSpec().getBindings()) {
        Capability cap = capabilityMap.get(sr.getCapability());
        Trigger trigger = null;
        if (sr.getOn() != null && sr.getOn().getContextChange() != null) {
          trigger =
              new ContextChangeTrigger(
                  new JQExpressionEvaluator(sr.getOn().getContextChange().getFilter()));
        }
        Binding rule = new Binding(sr.getName(), cap, trigger);
        def.getBindings().add(rule);
      }
    }

    // Convert milestones
    if (schema.getSpec().getMilestones() != null) {
      for (io.casehub.model.Milestone sm : schema.getSpec().getMilestones()) {
        Milestone milestone =
            Milestone.builder()
                .name(sm.getName())
                .completionCriteria(new JQExpressionEvaluator(sm.getCondition()))
                .build();
        milestone.setDescription(sm.getDescription());
        def.getMilestones().add(milestone);
      }
    }

    // Convert goals
    Map<String, Goal> goalMap = new java.util.LinkedHashMap<>();
    if (schema.getSpec().getGoals() != null) {
      for (io.casehub.model.Goal sg : schema.getSpec().getGoals()) {
        Goal goal =
            new Goal(sg.getName(), new JQExpressionEvaluator(sg.getCondition()), GoalKind.SUCCESS);
        goal.setDescription(sg.getDescription());
        goalMap.put(sg.getName(), goal);
        def.getGoals().add(goal);
      }
    }

    // Convert completion
    if (schema.getSpec().getCompletion() != null) {
      CaseCompletion sc = schema.getSpec().getCompletion();
      GoalExpression successExpr = convertGoalExpression(sc.getSuccess(), goalMap);
      GoalExpression failureExpr = convertGoalExpression(sc.getFailure(), goalMap);
      def.setCompletion(new GoalBasedCompletion(successExpr, failureExpr));
    }

    return def;
  }

  private GoalExpression convertGoalExpression(
      io.casehub.model.GoalExpression expr, Map<String, Goal> goalMap) {
    if (expr == null) return null;

    if (expr.getAllOf() != null && !expr.getAllOf().isEmpty()) {
      List<Goal> goals = expr.getAllOf().stream().map(goalMap::get).collect(Collectors.toList());
      return new AllOfGoalExpression(goals);
    }

    if (expr.getAnyOf() != null && !expr.getAnyOf().isEmpty()) {
      List<Goal> goals = expr.getAnyOf().stream().map(goalMap::get).collect(Collectors.toList());
      return new AnyOfGoalExpression(goals);
    }

    return null;
  }
}
