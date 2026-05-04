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

import io.casehub.api.engine.CaseHub;
import io.casehub.api.model.CaseDefinition;
import io.casehub.engine.spi.CaseDefinitionRegistry;
import io.quarkus.arc.Arc;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Indexer;
import org.jboss.logging.Logger;

/**
 * Discovers concrete {@link CaseHub} subclasses available on the application classpath and
 * registers their definitions with the engine.
 *
 * <p>The engine's default registry already registers CDI {@code CaseHub} beans. This loader covers
 * the flow use case where a case definition class is present on the classpath but is not annotated
 * as a CDI bean.
 */
@ApplicationScoped
public class CaseHubClassPathLoader {

  private static final Logger LOG = Logger.getLogger(CaseHubClassPathLoader.class);
  private static final String JANDEX_INDEX = "META-INF/jandex.idx";
  private static final DotName CASE_HUB = DotName.createSimple(CaseHub.class.getName());

  @Inject CaseDefinitionRegistry caseDefinitionRegistry;

  void onStart(@Observes @Priority(20) StartupEvent event) {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    IndexView index = loadIndex(classLoader);

    List<ClassInfo> caseHubClasses =
        index.getAllKnownSubclasses(CASE_HUB).stream()
            .filter(this::isConcreteClass)
            .sorted(Comparator.comparing(info -> info.name().toString()))
            .toList();

    int registered = 0;
    for (ClassInfo classInfo : caseHubClasses) {
      if (registerClass(classInfo, classLoader)) {
        registered++;
      }
    }

    LOG.infof("Discovered and registered %d CaseHub classpath definition(s)", registered);
  }

  private IndexView loadIndex(ClassLoader classLoader) {
    List<IndexView> indexes = new ArrayList<>();
    indexes.addAll(loadJandexIndexes(classLoader));
    indexes.add(indexClasspathDirectories());

    if (indexes.isEmpty()) {
      return IndexView.empty();
    }
    return CompositeIndex.create(indexes);
  }

  private List<IndexView> loadJandexIndexes(ClassLoader classLoader) {
    List<IndexView> indexes = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();

    try {
      Enumeration<URL> resources = classLoader.getResources(JANDEX_INDEX);
      while (resources.hasMoreElements()) {
        URL resource = resources.nextElement();
        if (!seen.add(resource.toExternalForm())) {
          continue;
        }
        try (InputStream stream = resource.openStream()) {
          indexes.add(new IndexReader(stream).read());
        } catch (IOException e) {
          LOG.debugf(e, "Failed to read Jandex index %s", resource);
        }
      }
    } catch (IOException e) {
      LOG.debugf(e, "Failed to enumerate Jandex indexes");
    }

    return indexes;
  }

  private Index indexClasspathDirectories() {
    Indexer indexer = new Indexer();
    String classPath = System.getProperty("java.class.path", "");
    Set<Path> roots = new LinkedHashSet<>();

    for (String entry : classPath.split(java.io.File.pathSeparator)) {
      if (entry.isBlank()) {
        continue;
      }
      Path path = Path.of(entry);
      if (Files.isDirectory(path)) {
        roots.add(path);
      }
    }

    for (Path root : roots) {
      indexDirectory(root, indexer);
    }

    return indexer.complete();
  }

  private void indexDirectory(Path root, Indexer indexer) {
    try (Stream<Path> paths = Files.walk(root)) {
      paths
          .filter(Files::isRegularFile)
          .filter(path -> path.toString().endsWith(".class"))
          .filter(path -> !path.getFileName().toString().equals("module-info.class"))
          .forEach(path -> indexClass(path, indexer));
    } catch (IOException e) {
      LOG.debugf(e, "Failed to scan classpath directory %s", root);
    }
  }

  private void indexClass(Path path, Indexer indexer) {
    try (InputStream stream = Files.newInputStream(path)) {
      indexer.index(stream);
    } catch (IOException e) {
      LOG.debugf(e, "Failed to index class file %s", path);
    }
  }

  private boolean isConcreteClass(ClassInfo classInfo) {
    return !classInfo.name().equals(CASE_HUB)
        && !classInfo.isInterface()
        && !classInfo.isAbstract()
        && !classInfo.isSynthetic();
  }

  private boolean registerClass(ClassInfo classInfo, ClassLoader classLoader) {
    String className = classInfo.name().toString();

    try {
      Class<?> rawClass = Class.forName(className, false, classLoader);
      if (!CaseHub.class.isAssignableFrom(rawClass)
          || rawClass.isInterface()
          || Modifier.isAbstract(rawClass.getModifiers())) {
        return false;
      }

      Class<? extends CaseHub> caseHubClass = rawClass.asSubclass(CaseHub.class);
      if (Arc.container().instance(caseHubClass).isAvailable()) {
        LOG.debugf("Skipping CDI-managed CaseHub class %s", className);
        return false;
      }

      CaseHub caseHub = instantiate(caseHubClass);
      CaseDefinition definition = caseHub.getDefinition();

      caseDefinitionRegistry
          .registerCaseDefinition(definition)
          .await()
          .atMost(Duration.ofSeconds(10));

      LOG.infof(
          "Registered CaseHub class: %s -> %s/%s v%s",
          className, definition.getNamespace(), definition.getName(), definition.getVersion());
      return true;
    } catch (ClassNotFoundException | LinkageError e) {
      LOG.debugf(e, "Skipping unavailable CaseHub class %s", className);
    } catch (NoSuchMethodException e) {
      LOG.warnf("Skipping CaseHub class %s: no no-argument constructor found", className);
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
      LOG.errorf(e, "Failed to instantiate CaseHub class %s", className);
    } catch (RuntimeException e) {
      LOG.errorf(e, "Failed to register CaseHub class %s", className);
    }

    return false;
  }

  private CaseHub instantiate(Class<? extends CaseHub> caseHubClass)
      throws NoSuchMethodException,
          InvocationTargetException,
          InstantiationException,
          IllegalAccessException {
    Constructor<? extends CaseHub> constructor = caseHubClass.getDeclaredConstructor();
    if (!Modifier.isPublic(caseHubClass.getModifiers())
        || !Modifier.isPublic(constructor.getModifiers())) {
      constructor.setAccessible(true);
    }
    return constructor.newInstance();
  }
}
