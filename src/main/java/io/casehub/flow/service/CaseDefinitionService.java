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

import io.casehub.api.model.CaseDefinition;
import io.casehub.engine.internal.model.CaseMetaModel;
import io.casehub.engine.spi.CaseDefinitionRegistry;
import io.casehub.flow.rest.PagedResponse;
import io.casehub.persistence.jpa.CaseMetaModelEntity;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.panache.common.Page;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

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

  @Inject CaseDefinitionRegistry caseDefinitionRegistry;

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
}
