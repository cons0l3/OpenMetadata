/*
 *  Copyright 2021 Collate 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.openmetadata.catalog.jdbi3;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.openmetadata.catalog.Entity;
import org.openmetadata.catalog.entity.Bots;
import org.openmetadata.catalog.resources.bots.BotsResource;
import org.openmetadata.catalog.type.ChangeDescription;
import org.openmetadata.catalog.type.EntityReference;
import org.openmetadata.catalog.type.TagLabel;
import org.openmetadata.catalog.util.EntityInterface;
import org.openmetadata.catalog.util.EntityUtil.Fields;

import java.io.IOException;
import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class BotsRepository extends EntityRepository<Bots>{
  private final CollectionDAO dao;

  public BotsRepository(CollectionDAO dao) {
    super(BotsResource.COLLECTION_PATH, Entity.BOTS, Bots.class, dao.botsDAO(), dao, Fields.EMPTY_FIELDS,
            Fields.EMPTY_FIELDS);
    this.dao = dao; }

  public Bots insert(Bots bots) throws JsonProcessingException {
    bots.setHref(null);
    dao.botsDAO().insert(bots);
    return bots;
  }

  @Override
  public Bots setFields(Bots entity, Fields fields) {
    return entity;
  }

  @Override
  public void restorePatchAttributes(Bots original, Bots update) { }

  @Override
  public EntityInterface<Bots> getEntityInterface(Bots entity) {
    return new BotsEntityInterface(entity);
  }

  @Override
  public void prepare(Bots entity) { }

  @Override
  public void storeEntity(Bots entity, boolean update) throws IOException {
    dao.botsDAO().insert(entity);
  }

  @Override
  public void storeRelationships(Bots entity) { }

  public static class BotsEntityInterface implements EntityInterface<Bots> {
    private final Bots entity;

    public BotsEntityInterface(Bots entity) {
      this.entity = entity;
    }

    @Override
    public UUID getId() {
      return entity.getId();
    }

    @Override
    public String getDescription() {
      return entity.getDescription();
    }

    @Override
    public String getDisplayName() {
      return entity.getDisplayName();
    }

    @Override
    public EntityReference getOwner() { return null; }

    @Override
    public String getFullyQualifiedName() { return entity.getName(); }

    @Override
    public List<TagLabel> getTags() { return null; }

    @Override
    public Double getVersion() { return entity.getVersion(); }

    @Override
    public String getUpdatedBy() { return entity.getUpdatedBy(); }

    @Override
    public Date getUpdatedAt() { return entity.getUpdatedAt(); }

    @Override
    public URI getHref() { return entity.getHref(); }

    @Override
    public List<EntityReference> getFollowers() {
      throw new UnsupportedOperationException("Dashboard service does not support followers");
    }

    @Override
    public ChangeDescription getChangeDescription() { return entity.getChangeDescription(); }

    @Override
    public EntityReference getEntityReference() {
      return new EntityReference().withId(getId()).withName(getFullyQualifiedName()).withDescription(getDescription())
              .withDisplayName(getDisplayName()).withType(Entity.BOTS);
    }

    @Override
    public Bots getEntity() { return entity; }

    @Override
    public void setId(UUID id) { entity.setId(id); }

    @Override
    public void setDescription(String description) {
      entity.setDescription(description);
    }

    @Override
    public void setDisplayName(String displayName) {
      entity.setDisplayName(displayName);
    }

    @Override
    public void setUpdateDetails(String updatedBy, Date updatedAt) {
      entity.setUpdatedBy(updatedBy);
      entity.setUpdatedAt(updatedAt);
    }

    @Override
    public void setChangeDescription(Double newVersion, ChangeDescription changeDescription) {
      entity.setVersion(newVersion);
      entity.setChangeDescription(changeDescription);
    }

    @Override
    public void setOwner(EntityReference owner) { }

    @Override
    public Bots withHref(URI href) { return entity.withHref(href); }

    @Override
    public void setTags(List<TagLabel> tags) { }
  }
}
