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
package io.casehub.flow.service;

import io.casehub.api.engine.CaseHub;
import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.api.model.CaseDefinition;
import io.casehub.engine.internal.model.CaseMetaModel;
import io.casehub.engine.spi.CaseDefinitionRegistry;
import io.casehub.flow.rest.dto.PagedResponse;
import io.casehub.persistence.jpa.CaseMetaModelEntity;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.panache.common.Page;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;

/**
 * Service for querying case definitions registered in the engine.
 *
 * <p>Provides access to case definitions from both CDI beans and YAML sources by querying {@link
 * CaseMetaModelRepository} and retrieving full definitions from {@link CaseDefinitionRegistry}.
 *
 * <p>All case definitions (CDI beans + YAML) are automatically registered at startup:
 *
 * <ul>
 *   <li>CDI beans extending {@link io.casehub.api.engine.CaseHub} via {@link
 *       io.casehub.engine.internal.engine.DefaultCaseDefinitionRegistry}
 *   <li>YAML files in classpath via {@link io.casehub.flow.loader.YamlCaseDefinitionLoader}
 * </ul>
 */
@ApplicationScoped
public class CaseDefinitionService {

  private static final Logger LOG = Logger.getLogger(CaseDefinitionService.class);

  @Inject CaseDefinitionRegistry caseDefinitionRegistry;
  @Inject Instance<CaseHub> caseHubs;
  @Inject CaseHubRuntime caseHubRuntime;
  private final Map<DefinitionKey, CaseHub> caseHubIndex = new ConcurrentHashMap<>();
  private volatile boolean cdiIndexed = false;

  private record DefinitionKey(String namespace, String name, String version) {}

  /** Simple CaseHub wrapper for definitions loaded from YAML or non-CDI classpath sources. */
  private class DefinitionOnlyCaseHub extends CaseHub {
    private final CaseDefinition definition;

    DefinitionOnlyCaseHub(CaseDefinition definition) {
      this.definition = definition;
      injectRuntime(this);
    }

    @Override
    public CaseDefinition getDefinition() {
      return definition;
    }
  }

  //TODO there must be a better aprouch than reflection to inject the runtime into the wrapper — maybe redesign CaseHub interface to separate definition from execution?
  private void injectRuntime(CaseHub hub) {
    try {
      Field runtimeField = CaseHub.class.getDeclaredField("runtime");
      runtimeField.setAccessible(true);
      runtimeField.set(hub, caseHubRuntime);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException("Failed to inject CaseHubRuntime into CaseHub wrapper", e);
    }
  }

  /** Index CDI CaseHub beans (called once on first access). */
  private void ensureCdiIndexed() {
    if (!cdiIndexed) {
      synchronized (caseHubIndex) {
        if (!cdiIndexed) {
          int count = 0;
          for (CaseHub hub : caseHubs) {
            io.casehub.api.model.CaseDefinition def = hub.getDefinition();
            DefinitionKey key = new DefinitionKey(def.getNamespace(), def.getName(), def.getVersion());
            caseHubIndex.put(key, hub);
            count++;
          }
          cdiIndexed = true;
          LOG.infof("Indexed %d CDI CaseHub instances", count);
        }
      }
    }
  }

  /**
   * List all registered case definitions.
   *
   * @return all case definitions from both CDI beans and YAML sources
   */
  public Uni<List<CaseDefinition>> listAll() {
    return Panache.withSession(
        () ->
            CaseMetaModelEntity.<CaseMetaModelEntity>listAll()
                .map(
                    entities ->
                        entities.stream()
                            .map(this::toCaseMetaModel)
                            .map(caseDefinitionRegistry::getCaseDefinition)
                            .collect(Collectors.toList())));
  }

  /**
   * List case definitions with pagination.
   *
   * @param pageIndex zero-indexed page number
   * @param pageSize number of items per page
   * @return paginated case definitions with metadata
   */
  public Uni<PagedResponse<CaseDefinition>> listAll(int pageIndex, int pageSize) {
    return Panache.withSession(
        () -> {
          Uni<Long> countUni = CaseMetaModelEntity.count();
          Uni<List<CaseMetaModelEntity>> entitiesUni =
              CaseMetaModelEntity.<CaseMetaModelEntity>findAll()
                  .page(Page.of(pageIndex, pageSize))
                  .list();

          return Uni.combine()
              .all()
              .unis(countUni, entitiesUni)
              .asTuple()
              .map(
                  tuple -> {
                    long totalElements = tuple.getItem1();
                    List<CaseMetaModelEntity> entities = tuple.getItem2();

                    List<CaseDefinition> definitions =
                        entities.stream()
                            .map(this::toCaseMetaModel)
                            .map(caseDefinitionRegistry::getCaseDefinition)
                            .collect(Collectors.toList());

                    int totalPages = (int) Math.ceil((double) totalElements / pageSize);

                    return new PagedResponse<>(
                        definitions, pageIndex + 1, pageSize, totalElements, totalPages);
                  });
        });
  }

  /**
   * Find all versions of a case definition by namespace and name.
   *
   * @param namespace the case namespace
   * @param name the case name
   * @return all versions matching the namespace and name
   */
  public Uni<List<CaseDefinition>> findByNamespaceAndName(String namespace, String name) {
    return Panache.withSession(
        () ->
            CaseMetaModelEntity.<CaseMetaModelEntity>find(
                    "namespace = ?1 and name = ?2", namespace, name)
                .list()
                .map(
                    entities ->
                        entities.stream()
                            .map(this::toCaseMetaModel)
                            .map(caseDefinitionRegistry::getCaseDefinition)
                            .collect(Collectors.toList())));
  }

  /**
   * Find a specific case definition by namespace, name, and version.
   *
   * @param namespace the case namespace
   * @param name the case name
   * @param version the case version
   * @return the matching case definition, or null if not found
   */
  public Uni<CaseDefinition> findByKey(String namespace, String name, String version) {
    return Panache.withSession(
        () ->
            CaseMetaModelEntity.<CaseMetaModelEntity>find(
                    "namespace = ?1 and name = ?2 and version = ?3", namespace, name, version)
                .firstResult()
                .map(
                    entity -> {
                      if (entity == null) {
                        return null;
                      }
                      CaseMetaModel metaModel = toCaseMetaModel(entity);
                      return caseDefinitionRegistry.getCaseDefinition(metaModel);
                    }));
  }

  private CaseMetaModel toCaseMetaModel(CaseMetaModelEntity entity) {
    CaseMetaModel metaModel = new CaseMetaModel();
    metaModel.setId(entity.id);
    metaModel.setName(entity.name);
    metaModel.setNamespace(entity.namespace);
    metaModel.setVersion(entity.version);
    metaModel.setTitle(entity.title);
    metaModel.setDsl(entity.dsl);
    metaModel.setCreatedAt(entity.createdAt);
    return metaModel;
  }

  /**
   * Find a CaseHub instance by definition key.
   *
   * @param namespace case namespace
   * @param name case name
   * @param version case version
   * @return Optional containing CaseHub if found, empty otherwise
   */
  public Uni<Optional<CaseHub>> findCaseHub(String namespace, String name, String version) {
    // Ensure CDI beans are indexed
    ensureCdiIndexed();

    DefinitionKey key = new DefinitionKey(namespace, name, version);

    // Check if it's a CDI bean (already indexed)
    CaseHub cachedHub = caseHubIndex.get(key);
    if (cachedHub != null) {
      return Uni.createFrom().item(Optional.of(cachedHub));
    }

    // Not a CDI bean - look up in database and create wrapper
    return Panache.withSession(
        () ->
            CaseMetaModelEntity.<CaseMetaModelEntity>find(
                    "namespace = ?1 and name = ?2 and version = ?3", namespace, name, version)
                .firstResult()
                .map(
                    entity -> {
                      if (entity == null) {
                        return Optional.<CaseHub>empty();
                      }
                      // Create and cache wrapper
                      CaseMetaModel metaModel = toCaseMetaModel(entity);
                      CaseDefinition definition =
                          caseDefinitionRegistry.getCaseDefinition(metaModel);
                      CaseHub wrapper = new DefinitionOnlyCaseHub(definition);
                      caseHubIndex.put(key, wrapper);
                      return Optional.of(wrapper);
                    }));
  }
}
