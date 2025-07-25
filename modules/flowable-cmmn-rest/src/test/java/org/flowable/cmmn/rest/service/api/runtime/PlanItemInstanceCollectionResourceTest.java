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

package org.flowable.cmmn.rest.service.api.runtime;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

import java.util.List;

import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.flowable.cmmn.api.runtime.CaseInstance;
import org.flowable.cmmn.api.runtime.PlanItemInstance;
import org.flowable.cmmn.engine.test.CmmnDeployment;
import org.flowable.cmmn.rest.service.BaseSpringRestTestCase;
import org.flowable.cmmn.rest.service.api.CmmnRestUrls;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import net.javacrumbs.jsonunit.core.Option;

/**
 * Test for all REST-operations related to a plan item instance collection resource.
 *
 * @author Tijs Rademakers
 */
public class PlanItemInstanceCollectionResourceTest extends BaseSpringRestTestCase {

    /**
     * Test getting a list of plan item instance, using all possible filters.
     */
    @Test
    @CmmnDeployment(resources = { "org/flowable/cmmn/rest/service/api/repository/oneHumanTaskCase.cmmn" })
    public void testGetPlanItemInstances() throws Exception {
        CaseInstance caseInstance = runtimeService.createCaseInstanceBuilder().caseDefinitionKey("oneHumanTaskCase").businessKey("myBusinessKey").start();

        PlanItemInstance planItem = runtimeService.createPlanItemInstanceQuery().caseInstanceId(caseInstance.getId()).singleResult();

        String id = planItem.getId();

        // Test without any parameters
        String url = CmmnRestUrls.createRelativeResourceUrl(CmmnRestUrls.URL_PLAN_ITEM_INSTANCE_COLLECTION);
        assertResultsPresentInDataResponse(url, id);

        // Plan item instance id
        url = CmmnRestUrls.createRelativeResourceUrl(CmmnRestUrls.URL_PLAN_ITEM_INSTANCE_COLLECTION) + "?id=" + id;
        assertResultsPresentInDataResponse(url, id);

        url = CmmnRestUrls.createRelativeResourceUrl(CmmnRestUrls.URL_PLAN_ITEM_INSTANCE_COLLECTION) + "?id=anotherId";
        assertResultsPresentInDataResponse(url);

        // Case definition id
        url = CmmnRestUrls.createRelativeResourceUrl(CmmnRestUrls.URL_PLAN_ITEM_INSTANCE_COLLECTION) + "?caseDefinitionId=" + caseInstance
                .getCaseDefinitionId();
        assertResultsPresentInDataResponse(url, id);

        url = CmmnRestUrls.createRelativeResourceUrl(CmmnRestUrls.URL_PLAN_ITEM_INSTANCE_COLLECTION) + "?caseDefinitionId=anotherId";
        assertResultsPresentInDataResponse(url);
    }
    
    /**
     * Test getting a list of plan item instances, using all possible filters.
     */
    @Test
    @CmmnDeployment(resources = { "org/flowable/cmmn/rest/service/api/repository/twoHumanTaskCase.cmmn" })
    public void testGetEndedPlanItemInstances() throws Exception {
        CaseInstance caseInstance = runtimeService.createCaseInstanceBuilder().caseDefinitionKey("myCase").businessKey("myBusinessKey").start();

        Task task = taskService.createTaskQuery().caseInstanceId(caseInstance.getId()).singleResult();
        taskService.complete(task.getId());
        
        List<PlanItemInstance> planItems = runtimeService.createPlanItemInstanceQuery().caseInstanceId(caseInstance.getId()).includeEnded().list();
        String activePlanItemId = null;
        String endedPlanItemId = null;
        for (PlanItemInstance planItemInstance : planItems) {
            if (planItemInstance.getEndedTime() != null) {
                endedPlanItemId = planItemInstance.getId();
            } else {
                activePlanItemId = planItemInstance.getId();
            }
        }

        // Test without any parameters
        String url = CmmnRestUrls.createRelativeResourceUrl(CmmnRestUrls.URL_PLAN_ITEM_INSTANCE_COLLECTION);
        assertResultsPresentInDataResponse(url, activePlanItemId);

        // Include ended
        url = CmmnRestUrls.createRelativeResourceUrl(CmmnRestUrls.URL_PLAN_ITEM_INSTANCE_COLLECTION) + "?includeEnded=true";
        assertResultsPresentInDataResponse(url, activePlanItemId, endedPlanItemId);

        url = CmmnRestUrls.createRelativeResourceUrl(CmmnRestUrls.URL_PLAN_ITEM_INSTANCE_COLLECTION) + "?includeEnded=false";
        assertResultsPresentInDataResponse(url, activePlanItemId);

        // Case definition id
        url = CmmnRestUrls.createRelativeResourceUrl(CmmnRestUrls.URL_PLAN_ITEM_INSTANCE_COLLECTION) + "?caseDefinitionId=" + caseInstance.getCaseDefinitionId();
        assertResultsPresentInDataResponse(url, activePlanItemId);

        url = CmmnRestUrls.createRelativeResourceUrl(CmmnRestUrls.URL_PLAN_ITEM_INSTANCE_COLLECTION) + "?caseDefinitionId=" + 
                caseInstance.getCaseDefinitionId() + "&includeEnded=true";
        assertResultsPresentInDataResponse(url, activePlanItemId, endedPlanItemId);
    }

    @Test
    @CmmnDeployment(resources = { "org/flowable/cmmn/rest/service/api/repository/twoHumanTaskCase.cmmn" })
    public void testGetEndedPlanItemInstancesWithLocalVariables() throws Exception {
        CaseInstance caseInstance = runtimeService.createCaseInstanceBuilder().caseDefinitionKey("myCase").start();

        Task task = taskService.createTaskQuery().caseInstanceId(caseInstance.getId()).singleResult();
        runtimeService.setLocalVariable(task.getSubScopeId(), "someLocalVariable", "someLocalValue");

        String url = CmmnRestUrls.createRelativeResourceUrl(CmmnRestUrls.URL_PLAN_ITEM_INSTANCE_COLLECTION);

        url = CmmnRestUrls.createRelativeResourceUrl(CmmnRestUrls.URL_PLAN_ITEM_INSTANCE_COLLECTION) + "?id=" + task.getSubScopeId();
        CloseableHttpResponse response = executeRequest(new HttpGet(SERVER_URL_PREFIX + url), HttpStatus.SC_OK);

        JsonNode dataNode = objectMapper.readTree(response.getEntity().getContent()).get("data");
        closeResponse(response);
        assertThatJson(dataNode).when(Option.IGNORING_EXTRA_FIELDS, Option.IGNORING_ARRAY_ORDER).isEqualTo("["
                + "     {"
                + "         id : '" + task.getSubScopeId() + "',"
                + "         caseInstanceId : '" + caseInstance.getId() + "',"
                + "        localVariables : [ ]"
                + "     }"
                + "]");

        url = CmmnRestUrls.createRelativeResourceUrl(CmmnRestUrls.URL_PLAN_ITEM_INSTANCE_COLLECTION) + "?id=" + task.getSubScopeId() + "&includeLocalVariables=true";
        response = executeRequest(new HttpGet(SERVER_URL_PREFIX + url), HttpStatus.SC_OK);

        // Check status and size
        dataNode = objectMapper.readTree(response.getEntity().getContent()).get("data");
        closeResponse(response);
        assertThatJson(dataNode).when(Option.IGNORING_EXTRA_FIELDS, Option.IGNORING_ARRAY_ORDER).isEqualTo("["
                + "     {"
                + "         id : '" + task.getSubScopeId() + "',"
                + "         caseInstanceId : '" + caseInstance.getId() + "',"
                + "         localVariables:[{"
                + "             name:'someLocalVariable',"
                + "             value:'someLocalValue',"
                + "             scope:'local'"
                + "         }]"
                + "     }"
                + "]");
    }
}
