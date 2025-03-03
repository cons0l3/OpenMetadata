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

import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.sqlobject.transaction.Transaction;
import org.openmetadata.catalog.Entity;
import org.openmetadata.catalog.entity.policies.Policy;
import org.openmetadata.catalog.resources.policies.PolicyResource;
import org.openmetadata.catalog.type.ChangeDescription;
import org.openmetadata.catalog.type.EntityReference;
import org.openmetadata.catalog.type.TagLabel;
import org.openmetadata.catalog.util.EntityInterface;
import org.openmetadata.catalog.util.EntityUtil;
import org.openmetadata.catalog.util.EntityUtil.Fields;
import org.openmetadata.catalog.util.JsonUtils;

import java.io.IOException;
import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Slf4j
public class PolicyRepository extends EntityRepository<Policy> {
  private static final Fields POLICY_UPDATE_FIELDS = new Fields(PolicyResource.FIELD_LIST,
          "displayName,description,owner,policyUrl,enabled,rules");
  private static final Fields POLICY_PATCH_FIELDS = new Fields(PolicyResource.FIELD_LIST,
          "displayName,description,owner,policyUrl,enabled,rules");
  private final CollectionDAO dao;

  public PolicyRepository(CollectionDAO dao) {
    super(PolicyResource.COLLECTION_PATH, Entity.POLICY, Policy.class, dao.policyDAO(), dao, POLICY_PATCH_FIELDS,
            POLICY_UPDATE_FIELDS);
    this.dao = dao;
  }

  public static String getFQN(Policy policy) {
    return (policy.getName());
  }

  @Transaction
  public void delete(UUID id) {
    if (dao.relationshipDAO().findToCount(id.toString(), Relationship.CONTAINS.ordinal(), Entity.POLICY) > 0) {
      throw new IllegalArgumentException("Policy is not empty");
    }
    dao.policyDAO().delete(id);
    dao.relationshipDAO().deleteAll(id.toString());
  }

  @Transaction
  public EntityReference getOwnerReference(Policy policy) throws IOException {
    return EntityUtil.populateOwner(dao.userDAO(), dao.teamDAO(), policy.getOwner());
  }

  @Override
  public Policy setFields(Policy policy, Fields fields) throws IOException {
    policy.setDisplayName(fields.contains("displayName") ? policy.getDisplayName() : null);
    policy.setDescription(fields.contains("description") ? policy.getDescription() : null);
    policy.setOwner(fields.contains("owner") ? getOwner(policy) : null);
    policy.setPolicyUrl(fields.contains("policyUrl") ? policy.getPolicyUrl() : null);
    policy.setEnabled(fields.contains("enabled") ? policy.getEnabled() : null);
    policy.setRules(fields.contains("rules") ? policy.getRules() : null);
    return policy;
  }

  @Override
  public void restorePatchAttributes(Policy original, Policy updated) {
  }

  @Override
  public EntityInterface<Policy> getEntityInterface(Policy entity) {
    return new PolicyEntityInterface(entity);
  }


  @Override
  public void prepare(Policy policy) throws IOException {
    policy.setFullyQualifiedName(getFQN(policy));

    // Check if owner is valid and set the relationship
    EntityUtil.populateOwner(dao.userDAO(), dao.teamDAO(), policy.getOwner());
  }

  @Override
  public void storeEntity(Policy policy, boolean update) throws IOException {
    // Relationships and fields such as href are derived and not stored as part of json
    EntityReference owner = policy.getOwner();
    URI href = policy.getHref();

    // Don't store owner and href as JSON. Build it on the fly based on relationships
    policy.withOwner(null).withHref(null);

    if (update) {
      dao.policyDAO().update(policy.getId(), JsonUtils.pojoToJson(policy));
    } else {
      dao.policyDAO().insert(policy);
    }

    // Restore the relationships
    policy.withOwner(owner).withHref(href);
  }

  @Override
  public void storeRelationships(Policy policy) {
    // Add policy owner relationship
    setOwner(policy, policy.getOwner());
  }

  @Override
  public EntityUpdater getUpdater(Policy original, Policy updated, boolean patchOperation) {
    return new PolicyUpdater(original, updated, patchOperation);
  }

  private EntityReference getOwner(Policy policy) throws IOException {
    return policy == null ? null : EntityUtil.populateOwner(policy.getId(), dao.relationshipDAO(), dao.userDAO(),
            dao.teamDAO());
  }

  public void setOwner(Policy policy, EntityReference owner) {
    EntityUtil.setOwner(dao.relationshipDAO(), policy.getId(), Entity.POLICY, owner);
    policy.setOwner(owner);
  }

  public static class PolicyEntityInterface implements EntityInterface<Policy> {
    private final Policy entity;

    public PolicyEntityInterface(Policy entity) {
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
    public EntityReference getOwner() {
      return entity.getOwner();
    }

    @Override
    public String getFullyQualifiedName() {
      return entity.getFullyQualifiedName();
    }

    @Override
    public List<TagLabel> getTags() {
      // Policy does not have tags.
      return null;
    }

    public List<Object> getRules() {
      return entity.getRules();
    }

    @Override
    public Double getVersion() {
      return entity.getVersion();
    }

    @Override
    public String getUpdatedBy() {
      return entity.getUpdatedBy();
    }

    @Override
    public Date getUpdatedAt() {
      return entity.getUpdatedAt();
    }

    @Override
    public URI getHref() {
      return entity.getHref();
    }

    @Override
    public List<EntityReference> getFollowers() {
      // Policy does not have followers.
      return null;
    }

    @Override
    public EntityReference getEntityReference() {
      return new EntityReference().withId(getId()).withName(getFullyQualifiedName())
              .withDescription(getDescription()).withDisplayName(getDisplayName()).withType(Entity.POLICY);
    }

    @Override
    public Policy getEntity() {
      return entity;
    }

    @Override
    public void setId(UUID id) {
      entity.setId(id);
    }

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
    public void setTags(List<TagLabel> tags) {
      // Policy does not have tags.
    }

    @Override
    public void setOwner(EntityReference owner) {
      entity.setOwner(owner);
    }

    public void setRules(List<Object> rules) {
      entity.setRules(rules);
    }

    @Override
    public Policy withHref(URI href) { return entity.withHref(href); }

    @Override
    public ChangeDescription getChangeDescription() {
      return entity.getChangeDescription();
    }
  }

  /**
   * Handles entity updated from PUT and POST operation.
   */
  public class PolicyUpdater extends EntityUpdater {
    public PolicyUpdater(Policy original, Policy updated, boolean patchOperation) {
      super(original, updated, patchOperation);
    }

    @Override
    public void entitySpecificUpdate() throws IOException {
      recordChange("policyUrl", original.getEntity().getPolicyUrl(), updated.getEntity().getPolicyUrl());
      recordChange("enabled", original.getEntity().getEnabled(), updated.getEntity().getEnabled());
      recordChange("rules", original.getEntity().getRules(), updated.getEntity().getRules());
    }
  }
}
