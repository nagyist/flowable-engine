/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.flowable.cmmn.engine.impl.deployer;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.flowable.cmmn.converter.CmmnXmlConstants;
import org.flowable.cmmn.engine.CmmnEngineConfiguration;
import org.flowable.cmmn.engine.impl.parser.CmmnParseContext;
import org.flowable.cmmn.engine.impl.parser.CmmnParseResult;
import org.flowable.cmmn.engine.impl.parser.CmmnParser;
import org.flowable.cmmn.engine.impl.persistence.entity.CaseDefinitionEntity;
import org.flowable.cmmn.engine.impl.persistence.entity.CaseDefinitionEntityManager;
import org.flowable.cmmn.engine.impl.persistence.entity.CmmnDeploymentEntity;
import org.flowable.cmmn.engine.impl.persistence.entity.CmmnResourceEntity;
import org.flowable.cmmn.engine.impl.persistence.entity.deploy.CaseDefinitionCacheEntry;
import org.flowable.cmmn.engine.impl.util.CmmnCorrelationUtil;
import org.flowable.cmmn.engine.impl.util.CommandContextUtil;
import org.flowable.cmmn.model.Case;
import org.flowable.cmmn.model.CmmnModel;
import org.flowable.cmmn.model.ExtensionElement;
import org.flowable.cmmn.validation.CaseValidator;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.common.engine.api.repository.EngineDeployment;
import org.flowable.common.engine.api.repository.EngineResource;
import org.flowable.common.engine.api.scope.ScopeTypes;
import org.flowable.common.engine.impl.EngineDeployer;
import org.flowable.common.engine.impl.assignment.CandidateUtil;
import org.flowable.common.engine.impl.cfg.IdGenerator;
import org.flowable.common.engine.impl.el.ExpressionManager;
import org.flowable.common.engine.impl.persistence.deploy.DeploymentCache;
import org.flowable.eventsubscription.service.EventSubscriptionService;
import org.flowable.identitylink.api.IdentityLinkType;
import org.flowable.identitylink.service.IdentityLinkService;
import org.flowable.identitylink.service.impl.persistence.entity.IdentityLinkEntity;
import org.flowable.variable.service.impl.el.NoExecutionVariableScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO: many similarities with BpmnDeployer, see if it can be merged to the common module
 *
 * @author Joram Barrez
 */
public class CmmnDeployer implements EngineDeployer {

    private static final Logger LOGGER = LoggerFactory.getLogger(CmmnDeployer.class);

    public static final String[] CMMN_RESOURCE_SUFFIXES = new String[]{".cmmn", ".cmmn11", ".cmmn.xml", ".cmmn11.xml"};

    protected CmmnEngineConfiguration cmmnEngineConfiguration;
    
    protected IdGenerator idGenerator;
    protected CmmnParser cmmnParser;
    protected CaseDefinitionDiagramHelper caseDefinitionDiagramHelper;
    protected boolean usePrefixId;
    
    public CmmnDeployer(CmmnEngineConfiguration cmmnEngineConfiguration) {
        this.cmmnEngineConfiguration = cmmnEngineConfiguration;
    }

    @Override
    public void deploy(EngineDeployment deployment, Map<String, Object> deploymentSettings) {
        LOGGER.debug("Processing deployment {}", deployment.getName());

        CmmnParseResult parseResult = new CmmnParseResult(deployment);
        for (EngineResource resource : deployment.getResources().values()) {
            if (isCmmnResource(resource.getName())) {
                LOGGER.debug("Processing CMMN resource {}", resource.getName());
                parseResult.merge(cmmnParser.parse(new CmmnParseContextImpl(resource, deployment.isNew())));
            }
        }

        verifyCaseDefinitionsDoNotShareKeys(parseResult.getAllCaseDefinitions());
        copyDeploymentValuesToCaseDefinitions(parseResult.getDeployment(), parseResult.getAllCaseDefinitions());
        setResourceNamesOnCaseDefinitions(parseResult);

        createAndPersistNewDiagramsIfNeeded(parseResult);
        setCaseDefinitionDiagramNames(parseResult);

        if (deployment.isNew()) {
            Map<CaseDefinitionEntity, CaseDefinitionEntity> mapOfNewCaseDefinitionToPreviousVersion = getPreviousVersionsOfCaseDefinitions(parseResult);
            setCaseDefinitionVersionsAndIds(parseResult, mapOfNewCaseDefinitionToPreviousVersion);
            persistCaseDefinitions(parseResult);
            updateEventSubscriptions(parseResult, mapOfNewCaseDefinitionToPreviousVersion);

        } else {
            makeCaseDefinitionsConsistentWithPersistedVersions(parseResult);

        }

        updateCachingAndArtifacts(parseResult);
    }

    public static boolean isCmmnResource(String resourceName) {
        for (String suffix : CMMN_RESOURCE_SUFFIXES) {
            if (resourceName.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates new diagrams for case definitions if the deployment is new, the case definition in question supports it, and the engine is configured to make new diagrams.
     *
     * When this method creates a new diagram, it also persists it via the ResourceEntityManager and adds it to the resources of the deployment.
     */
    protected void createAndPersistNewDiagramsIfNeeded(CmmnParseResult parseResult) {
        for (CaseDefinitionEntity caseDefinition : parseResult.getAllCaseDefinitions()) {
            if (caseDefinitionDiagramHelper.shouldCreateDiagram(caseDefinition, parseResult.getDeployment())) {
                CmmnResourceEntity resource = caseDefinitionDiagramHelper.createDiagramForCaseDefinition(
                                caseDefinition, parseResult.getCmmnModelForCaseDefinition(caseDefinition));
                if (resource != null) {
                    CommandContextUtil.getCmmnResourceEntityManager().insert(resource, false);
                    ((CmmnDeploymentEntity) parseResult.getDeployment()).addResource(resource); // now we'll find it if we look for the diagram name later.
                }
            }
        }
    }

    /**
     * Updates all the case definition entities to have the correct diagram resource name. Must be called after createAndPersistNewDiagramsAsNeeded to ensure that any newly-created diagrams already
     * have their resources attached to the deployment.
     */
    protected void setCaseDefinitionDiagramNames(CmmnParseResult parseResult) {
        Map<String, EngineResource> resources = parseResult.getDeployment().getResources();

        for (CaseDefinitionEntity caseDefinition : parseResult.getAllCaseDefinitions()) {
            String diagramResourceName = ResourceNameUtil.getCaseDiagramResourceNameFromDeployment(caseDefinition, resources);
            caseDefinition.setDiagramResourceName(diagramResourceName);
        }
    }

    protected Map<CaseDefinitionEntity, CaseDefinitionEntity> getPreviousVersionsOfCaseDefinitions(CmmnParseResult parseResult) {
        Map<CaseDefinitionEntity, CaseDefinitionEntity> result = new LinkedHashMap<>();
        for (CaseDefinitionEntity newDefinition : parseResult.getAllCaseDefinitions()) {
            CaseDefinitionEntity existingDefinition = getMostRecentVersionOfCaseDefinition(newDefinition);
            if (existingDefinition != null) {
                result.put(newDefinition, existingDefinition);
            }
        }
        return result;
    }

    protected void setCaseDefinitionVersionsAndIds(CmmnParseResult parseResult, Map<CaseDefinitionEntity, CaseDefinitionEntity> mapNewToOldCaseDefinitions) {
        for (CaseDefinitionEntity caseDefinition : parseResult.getAllCaseDefinitions()) {
            int version = 1;
            CaseDefinitionEntity latest = mapNewToOldCaseDefinitions.get(caseDefinition);
            if (latest != null) {
                version = latest.getVersion() + 1;
            }
            caseDefinition.setVersion(version);
            if (usePrefixId) {
                caseDefinition.setId(caseDefinition.getIdPrefix() + idGenerator.getNextId());
            } else {
                caseDefinition.setId(idGenerator.getNextId());
            }

            Case caseObject = parseResult.getCmmnCaseForCaseDefinition(caseDefinition);
            if (caseObject.getPlanModel().getFormKey() != null) {
                caseDefinition.setHasStartFormKey(true);
            }
        }
    }

    protected void persistCaseDefinitions(CmmnParseResult parseResult) {
        CaseDefinitionEntityManager caseDefinitionManager = cmmnEngineConfiguration.getCaseDefinitionEntityManager();
        for (CaseDefinitionEntity caseDefinition : parseResult.getAllCaseDefinitions()) {
            caseDefinitionManager.insert(caseDefinition, false);
            addAuthorizationsForNewCaseDefinition(parseResult.getCmmnCaseForCaseDefinition(caseDefinition), caseDefinition);
        }
    }

    protected void updateEventSubscriptions(CmmnParseResult parseResult, Map<CaseDefinitionEntity, CaseDefinitionEntity> mapOfNewCaseDefinitionToPreviousVersion) {
        EventSubscriptionService eventSubscriptionService = cmmnEngineConfiguration.getEventSubscriptionServiceConfiguration().getEventSubscriptionService();
        for (CaseDefinitionEntity caseDefinition : parseResult.getAllCaseDefinitions()) {

            CaseDefinitionEntity previousCaseDefinition = mapOfNewCaseDefinitionToPreviousVersion.get(caseDefinition);
            if (previousCaseDefinition != null) {
                if (isManualCorrelationSubscriptionConfiguration(parseResult, previousCaseDefinition)) {
                    // for a dynamic event registry start event, we don't remove the manually registered subscriptions, but rather update them to the newest
                    // case definition, if required
                    String startEventType = getCaseModel(parseResult, previousCaseDefinition).getPrimaryCase().getStartEventType();
                    updateOldEventSubscriptions(previousCaseDefinition, caseDefinition, startEventType);
                } else {
                    // for a static event registry start event, we delete the old subscription and will later create a new one
                    eventSubscriptionService.deleteEventSubscriptionsForScopeDefinitionIdAndTypeAndNullScopeId(previousCaseDefinition.getId(), ScopeTypes.CMMN);
                }
            }

            // create new subscriptions, but only for static event registry start events
            Case caseModel = parseResult.getCmmnCaseForCaseDefinition(caseDefinition);
            String startEventType = caseModel.getStartEventType();
            if (startEventType != null && !isManualCorrelationSubscriptionConfiguration(parseResult, caseDefinition)) {
                eventSubscriptionService.createEventSubscriptionBuilder()
                    .eventType(startEventType)
                    .configuration(getEventCorrelationKey(caseModel))
                    .scopeDefinitionId(caseDefinition.getId())
                    .scopeType(ScopeTypes.CMMN)
                    .tenantId(caseDefinition.getTenantId())
                    .create();
            }
        }
    }

    protected void updateOldEventSubscriptions(CaseDefinitionEntity previousCaseDefinition, CaseDefinitionEntity caseDefinition, String eventType) {
        CommandContextUtil.getCmmnEngineConfiguration().getEventSubscriptionServiceConfiguration().getEventSubscriptionService().updateEventSubscriptionScopeDefinitionId(
            previousCaseDefinition.getId(), caseDefinition.getId(), eventType, caseDefinition.getKey(), null);
    }

    protected CmmnModel getCaseModel(CmmnParseResult parseResult, CaseDefinitionEntity caseDefinition) {
        CmmnModel caseModel = parseResult.getCmmnModelForCaseDefinition(caseDefinition);
        if (caseModel == null) {
            // if the case model is not contained in the parse result cache, load it manually
            caseModel = CommandContextUtil.getCmmnEngineConfiguration().getCmmnRepositoryService().getCmmnModel(caseDefinition.getId());
        }
        return caseModel;
    }

    protected boolean isManualCorrelationSubscriptionConfiguration(CmmnParseResult parseResult, CaseDefinitionEntity caseDefinition) {
        CmmnModel caseModel = getCaseModel(parseResult, caseDefinition);
        List<ExtensionElement> correlationCfgExtensions = caseModel.getPrimaryCase().getExtensionElements()
            .getOrDefault(CmmnXmlConstants.START_EVENT_CORRELATION_CONFIGURATION, Collections.emptyList());
        if (!correlationCfgExtensions.isEmpty()) {
            return Objects.equals(correlationCfgExtensions.get(0).getElementText(), CmmnXmlConstants.START_EVENT_CORRELATION_MANUAL);
        }
        return false;
    }

    protected String getEventCorrelationKey(Case caseModel) {
        return CmmnCorrelationUtil.getCorrelationKey(CmmnXmlConstants.ELEMENT_EVENT_CORRELATION_PARAMETER, CommandContextUtil.getCommandContext(), caseModel);
    }

    protected void makeCaseDefinitionsConsistentWithPersistedVersions(CmmnParseResult parseResult) {
        for (CaseDefinitionEntity caseDefinition : parseResult.getAllCaseDefinitions()) {
            CaseDefinitionEntity persistedCaseDefinition = getPersistedInstanceOfCaseDefinition(caseDefinition);
            if (persistedCaseDefinition != null) {
                caseDefinition.setId(persistedCaseDefinition.getId());
                caseDefinition.setVersion(persistedCaseDefinition.getVersion());
                caseDefinition.setHasStartFormKey(persistedCaseDefinition.hasStartFormKey());
                caseDefinition.setHasGraphicalNotation(persistedCaseDefinition.hasGraphicalNotation());
            }
        }
    }

    protected void verifyCaseDefinitionsDoNotShareKeys(Collection<CaseDefinitionEntity> caseDefinitionEntities) {
        Set<String> keySet = new LinkedHashSet<>();
        for (CaseDefinitionEntity caseDefinitionEntity : caseDefinitionEntities) {
            if (keySet.contains(caseDefinitionEntity.getKey())) {
                throw new FlowableException("The deployment contains case definitions with the same key (case id attribute), this is not allowed");
            }
            keySet.add(caseDefinitionEntity.getKey());
        }
    }

    protected void copyDeploymentValuesToCaseDefinitions(EngineDeployment deployment, List<CaseDefinitionEntity> caseDefinitionEntities) {
        String tenantId = deployment.getTenantId();
        String deploymentId = deployment.getId();

        for (CaseDefinitionEntity caseDefinitionEntity : caseDefinitionEntities) {
            if (tenantId != null) {
                caseDefinitionEntity.setTenantId(tenantId);
            }
            caseDefinitionEntity.setDeploymentId(deploymentId);
        }
    }

    protected void setResourceNamesOnCaseDefinitions(CmmnParseResult parseResult) {
        for (CaseDefinitionEntity caseDefinitionEntity : parseResult.getAllCaseDefinitions()) {
            String resourceName = parseResult.getResourceForCaseDefinition(caseDefinitionEntity).getName();
            caseDefinitionEntity.setResourceName(resourceName);
        }
    }

    protected CaseDefinitionEntity getMostRecentVersionOfCaseDefinition(CaseDefinitionEntity caseDefinitionEntity) {
        String key = caseDefinitionEntity.getKey();
        String tenantId = caseDefinitionEntity.getTenantId();
        CaseDefinitionEntityManager caseDefinitionEntityManager = CommandContextUtil.getCaseDefinitionEntityManager();
        CaseDefinitionEntity existingCaseDefinition = null;
        if (tenantId != null && !tenantId.equals(CmmnEngineConfiguration.NO_TENANT_ID)) {
            existingCaseDefinition = caseDefinitionEntityManager.findLatestCaseDefinitionByKeyAndTenantId(key, tenantId);
        } else {
            existingCaseDefinition = caseDefinitionEntityManager.findLatestCaseDefinitionByKey(key);
        }

        return existingCaseDefinition;
    }

    protected CaseDefinitionEntity getPersistedInstanceOfCaseDefinition(CaseDefinitionEntity caseDefinitionEntity) {
        String deploymentId = caseDefinitionEntity.getDeploymentId();
        if (StringUtils.isEmpty(caseDefinitionEntity.getDeploymentId())) {
            throw new IllegalStateException("Provided case definition must have a deployment id.");
        }

        CaseDefinitionEntityManager caseDefinitionEntityManager = cmmnEngineConfiguration.getCaseDefinitionEntityManager();
        CaseDefinitionEntity persistedCaseDefinitionEntity = null;
        if (caseDefinitionEntity.getTenantId() == null || CmmnEngineConfiguration.NO_TENANT_ID.equals(caseDefinitionEntity.getTenantId())) {
            persistedCaseDefinitionEntity = caseDefinitionEntityManager.findCaseDefinitionByDeploymentAndKey(deploymentId, caseDefinitionEntity.getKey());
        } else {
            persistedCaseDefinitionEntity = caseDefinitionEntityManager.findCaseDefinitionByDeploymentAndKeyAndTenantId(deploymentId, caseDefinitionEntity.getKey(), caseDefinitionEntity.getTenantId());
        }
        return persistedCaseDefinitionEntity;
    }

    protected void updateCachingAndArtifacts(CmmnParseResult parseResult) {
        DeploymentCache<CaseDefinitionCacheEntry> caseDefinitionCache = cmmnEngineConfiguration.getCaseDefinitionCache();
        CmmnDeploymentEntity deployment = (CmmnDeploymentEntity) parseResult.getDeployment();

        for (CaseDefinitionEntity caseDefinitionEntity : parseResult.getAllCaseDefinitions()) {
            CmmnModel model = parseResult.getCmmnModelForCaseDefinition(caseDefinitionEntity);
            Case caze = parseResult.getCmmnCaseForCaseDefinition(caseDefinitionEntity);
            CaseDefinitionCacheEntry cacheEntry = new CaseDefinitionCacheEntry(caseDefinitionEntity, model, caze);
            caseDefinitionCache.add(caseDefinitionEntity.getId(), cacheEntry);

            deployment.addDeployedArtifact(caseDefinitionEntity);
            deployment.addCaseDefinitionCacheEntry(caseDefinitionEntity.getId(), cacheEntry);
        }
    }
    
    public void addAuthorizationsForNewCaseDefinition(Case caze, CaseDefinitionEntity caseDefinition) {
        addAuthorizationsFromIterator(caze.getCandidateStarterUsers(), caseDefinition, "user");
        addAuthorizationsFromIterator(caze.getCandidateStarterGroups(), caseDefinition, "group");
    }

    protected void addAuthorizationsFromIterator(List<String> expressions,
            CaseDefinitionEntity caseDefinition, String expressionType) {

        if (expressions != null) {
            IdentityLinkService identityLinkService = cmmnEngineConfiguration.getIdentityLinkServiceConfiguration().getIdentityLinkService();
            ExpressionManager expressionManager = cmmnEngineConfiguration.getExpressionManager();

            for (String expressionStr : expressions) {
                Expression expression = expressionManager.createExpression(expressionStr);
                Object value = expression.getValue(NoExecutionVariableScope.getSharedInstance());

                if (value != null) {
                    Collection<String> candidates = CandidateUtil.extractCandidates(value);
                    for (String candidate : candidates) {
                        IdentityLinkEntity identityLink = identityLinkService.createIdentityLink();
                        identityLink.setScopeDefinitionId(caseDefinition.getId());
                        identityLink.setScopeType(ScopeTypes.CMMN);
                        if ("user".equals(expressionType)) {
                            identityLink.setUserId(candidate);
                        } else if ("group".equals(expressionType)) {
                            identityLink.setGroupId(candidate);
                        }
                        identityLink.setType(IdentityLinkType.CANDIDATE);
                        identityLinkService.insertIdentityLink(identityLink);
                    }
                }
            }
        }
    }

    public IdGenerator getIdGenerator() {
        return idGenerator;
    }

    public void setIdGenerator(IdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    public CmmnParser getCmmnParser() {
        return cmmnParser;
    }

    public void setCmmnParser(CmmnParser cmmnParser) {
        this.cmmnParser = cmmnParser;
    }

    public CaseDefinitionDiagramHelper getCaseDefinitionDiagramHelper() {
        return caseDefinitionDiagramHelper;
    }

    public void setCaseDefinitionDiagramHelper(CaseDefinitionDiagramHelper caseDefinitionDiagramHelper) {
        this.caseDefinitionDiagramHelper = caseDefinitionDiagramHelper;
    }

    public boolean isUsePrefixId() {
        return usePrefixId;
    }

    public void setUsePrefixId(boolean usePrefixId) {
        this.usePrefixId = usePrefixId;
    }

    protected class CmmnParseContextImpl implements CmmnParseContext {

        protected final EngineResource resource;
        protected final boolean newDeployment;

        public CmmnParseContextImpl(EngineResource resource, boolean newDeployment) {
            this.resource = resource;
            this.newDeployment = newDeployment;
        }

        @Override
        public EngineResource resource() {
            return resource;
        }

        @Override
        public boolean enableSafeXml() {
            return cmmnEngineConfiguration.isEnableSafeCmmnXml();
        }

        @Override
        public String xmlEncoding() {
            return cmmnEngineConfiguration.getXmlEncoding();
        }

        @Override
        public boolean validateXml() {
            // On redeploy, we assume it is validated at the first deploy
            return newDeployment && !cmmnEngineConfiguration.isDisableCmmnXmlValidation();
        }

        @Override
        public boolean validateCmmnModel() {
            // On redeploy, we assume it is validated at the first deploy
            return newDeployment && validateXml();
        }

        @Override
        public CaseValidator caseValidator() {
            return cmmnEngineConfiguration.getCaseValidator();
        }
    }

    @Override
    public void undeploy(EngineDeployment parentDeployment, boolean cascade) {
        // Nothing to do
    }
}
