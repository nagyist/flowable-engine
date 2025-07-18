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

package org.flowable.cmmn.rest.service.api.history;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;

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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import net.javacrumbs.jsonunit.core.Option;

/**
 * Test for REST-operation related to the historic variable instance query resource.
 *
 * @author Tijs Rademakers
 */
public class HistoricVariableInstanceCollectionResourceTest extends BaseSpringRestTestCase {

    /**
     * Test querying historic variable instance. GET cmmn-history/historic-variable-instances
     */
    @Test
    @CmmnDeployment(resources = { "org/flowable/cmmn/rest/service/api/repository/twoHumanTaskCase.cmmn" })
    public void testQueryVariableInstances() throws Exception {
        HashMap<String, Object> caseVariables = new HashMap<>();
        caseVariables.put("stringVar", "Azerty");
        caseVariables.put("intVar", 67890);
        caseVariables.put("booleanVar", false);

        CaseInstance caseInstance = runtimeService.createCaseInstanceBuilder().caseDefinitionKey("myCase").variables(caseVariables).start();
        Task task = taskService.createTaskQuery().caseInstanceId(caseInstance.getId()).singleResult();
        taskService.complete(task.getId());
        task = taskService.createTaskQuery().caseInstanceId(caseInstance.getId()).singleResult();
        taskService.setVariableLocal(task.getId(), "taskVariable", "test");

        CaseInstance caseInstance2 = runtimeService.createCaseInstanceBuilder().caseDefinitionKey("myCase").variables(caseVariables).start();

        String url = CmmnRestUrls.createRelativeResourceUrl(CmmnRestUrls.URL_HISTORIC_VARIABLE_INSTANCES);

        assertResultsPresentInDataResponse(url + "?variableName=stringVar", 2, "stringVar", "Azerty");

        assertResultsPresentInDataResponse(url + "?variableName=booleanVar", 2, "booleanVar", false);

        assertResultsPresentInDataResponse(url + "?variableName=booleanVar2", 0, null, null);

        assertResultsPresentInDataResponse(url + "?caseInstanceId=" + caseInstance.getId(), 4, "taskVariable", "test");

        assertResultsPresentInDataResponse(url + "?caseInstanceId=" + caseInstance.getId() + "&excludeTaskVariables=true", 3, "intVar", 67890);

        assertResultsPresentInDataResponse(url + "?caseInstanceId=" + caseInstance2.getId(), 3, "stringVar", "Azerty");

        assertResultsPresentInDataResponse(url + "?taskId=" + task.getId(), 1, "taskVariable", "test");

        assertResultsPresentInDataResponse(url + "?taskId=" + task.getId() + "&variableName=booleanVar", 0, null, null);

        assertResultsPresentInDataResponse(url + "?variableNameLike=" + encode("%Var"), 6, "stringVar", "Azerty");

        assertResultsPresentInDataResponse(url + "?variableNameLike=" + encode("%Var2"), 0, null, null);
    }

    @Test
    @CmmnDeployment(resources = { "org/flowable/cmmn/rest/service/api/repository/oneHumanTaskCase.cmmn" })
    public void testQueryVariableExcludeLocalVariable() throws Exception {
        CaseInstance caseInstance = runtimeService.createCaseInstanceBuilder()
                .caseDefinitionKey("oneHumanTaskCase")
                .variable("myVar", "test1")
                .start();

        PlanItemInstance planItemInstance = runtimeService.createPlanItemInstanceQuery().planItemDefinitionId("theTask")
                .caseInstanceId(caseInstance.getId()).singleResult();
        Task task = taskService.createTaskQuery().caseInstanceId(caseInstance.getId()).singleResult();
        taskService.setVariableLocal(task.getId(),"localTaskVariable","localTaskVarValue");

        runtimeService.setLocalVariable(planItemInstance.getId(), "myLocalVar", "test2");

        String url = CmmnRestUrls.createRelativeResourceUrl(CmmnRestUrls.URL_HISTORIC_VARIABLE_INSTANCES);

        assertResultsPresentInDataResponse(url + "?caseInstanceId=" + caseInstance.getId(), 3, "myLocalVar", "test2");

        assertResultsPresentInDataResponse(url + "?caseInstanceId=" + caseInstance.getId() + "&excludeLocalVariables=true", 1, "myVar", "test1");

    }

    @Test
    @CmmnDeployment(resources = { "org/flowable/cmmn/rest/service/api/repository/oneHumanTaskCase.cmmn" })
    public void testVariableInstanceScopeIsPresent() throws Exception {
        CaseInstance caseInstance = runtimeService.createCaseInstanceBuilder()
                .caseDefinitionKey("oneHumanTaskCase")
                .variable("myVar", "test1")
                .start();

        PlanItemInstance planItemInstance = runtimeService.createPlanItemInstanceQuery().planItemDefinitionId("theTask")
                .caseInstanceId(caseInstance.getId()).singleResult();

        Task task = taskService.createTaskQuery().caseInstanceId(caseInstance.getId()).singleResult();
        taskService.setVariableLocal(task.getId(), "localTaskVariable", "localTaskVarValue");

        runtimeService.setLocalVariable(planItemInstance.getId(), "myLocalVar", "test2");

        taskService.complete(task.getId());

        String url = CmmnRestUrls.createRelativeResourceUrl(CmmnRestUrls.URL_HISTORIC_VARIABLE_INSTANCES) + "?caseInstanceId=" + caseInstance.getId();

        CloseableHttpResponse response = executeRequest(new HttpGet(SERVER_URL_PREFIX + url), HttpStatus.SC_OK);

        // Check status and size
        JsonNode node = objectMapper.readTree(response.getEntity().getContent());

        assertThatJson(node).when(Option.IGNORING_EXTRA_FIELDS, Option.IGNORING_ARRAY_ORDER, Option.IGNORING_EXTRA_ARRAY_ITEMS).isEqualTo("{"
                + " size: 3,"
                + " data : ["
                + "     {"
                + "         caseInstanceId : '" + caseInstance.getId() + "',"
                + "         planItemInstanceId : '" + planItemInstance.getId() + "',"
                + "         variable:{"
                + "             name:'localTaskVariable',"
                + "             value:'localTaskVarValue',"
                + "             scope:'local'"
                + "         }"
                + "     },"
                + "     {"
                + "         caseInstanceId : '" + caseInstance.getId() + "',"
                + "         planItemInstanceId : '" + planItemInstance.getId() + "',"
                + "         variable:{"
                + "             name:'myLocalVar',"
                + "             value:'test2',"
                + "             scope:'local'"
                + "         }"
                + "     },"
                + "     {"
                + "         caseInstanceId : '" + caseInstance.getId() + "',"
                + "         planItemInstanceId : null,"
                + "         variable:{"
                + "             name:'myVar',"
                + "             value:'test1',"
                + "             scope:'global'"
                + "         }"
                + "     }"
                + " ]"
                + "}");

        url = CmmnRestUrls.createRelativeResourceUrl(CmmnRestUrls.URL_HISTORIC_VARIABLE_INSTANCES) + "?caseInstanceId=" + caseInstance.getId()
                + "&excludeLocalVariables=true";

        response = executeRequest(new HttpGet(SERVER_URL_PREFIX + url), HttpStatus.SC_OK);

        // Check status and size
        node = objectMapper.readTree(response.getEntity().getContent());

        assertThatJson(node).when(Option.IGNORING_EXTRA_FIELDS, Option.IGNORING_ARRAY_ORDER).isEqualTo("{"
                + " size: 1,"
                + " data : ["
                + "     {"
                + "         caseInstanceId : '" + caseInstance.getId() + "',"
                + "         variable:{"
                + "             name:'myVar',"
                + "             value:'test1',"
                + "             scope:'global'"
                + "         }"
                + "     }"
                + " ]"
                + "}");

    }

    protected void assertResultsPresentInDataResponse(String url, int numberOfResultsExpected, String variableName, Object variableValue)
            throws JsonProcessingException, IOException {

        // Do the actual call
        CloseableHttpResponse response = executeRequest(new HttpGet(SERVER_URL_PREFIX + url), HttpStatus.SC_OK);

        // Check status and size
        JsonNode dataNode = objectMapper.readTree(response.getEntity().getContent()).get("data");
        closeResponse(response);
        assertThat(dataNode).hasSize(numberOfResultsExpected);

        // Check presence of ID's
        if (variableName != null) {
            boolean variableFound = false;
            Iterator<JsonNode> it = dataNode.iterator();
            while (it.hasNext()) {
                JsonNode dataElementNode = it.next();
                JsonNode variableNode = dataElementNode.get("variable");
                String name = variableNode.get("name").textValue();
                if (variableName.equals(name)) {
                    variableFound = true;
                    if (variableValue instanceof Boolean) {
                        assertThat((boolean) (Boolean) variableValue).as("Variable value is not equal").isEqualTo(variableNode.get("value").asBoolean());
                    } else if (variableValue instanceof Integer) {
                        assertThat((int) (Integer) variableValue).as("Variable value is not equal").isEqualTo(variableNode.get("value").asInt());
                    } else {
                        assertThat((String) variableValue).as("Variable value is not equal").isEqualTo(variableNode.get("value").asText());
                    }
                }
            }
            assertThat(variableFound).as("Variable " + variableName + " is missing").isTrue();
        }
    }
}
