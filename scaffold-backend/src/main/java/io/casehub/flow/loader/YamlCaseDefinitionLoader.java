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

import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.converter.CaseDefinitionYamlMapper;
import io.casehub.engine.common.spi.CaseDefinitionRegistry;
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
import java.util.Collections;
import java.util.List;
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
 * <p>YAML definitions are converted to {@link CaseDefinition} via {@link CaseDefinitionYamlMapper}
 * and registered via {@link CaseDefinitionRegistry}.
 */
@ApplicationScoped
public class YamlCaseDefinitionLoader {

  private static final Logger LOG = Logger.getLogger(YamlCaseDefinitionLoader.class);
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

      CaseDefinition definition = CaseDefinitionYamlMapper.load(is);

      caseDefinitionRegistry.registerCaseDefinition(definition);

      LOG.infof(
          "Registered YAML case definition: %s/%s v%s from %s",
          definition.getNamespace(), definition.getName(), definition.getVersion(), resourcePath);
    }
  }
}
