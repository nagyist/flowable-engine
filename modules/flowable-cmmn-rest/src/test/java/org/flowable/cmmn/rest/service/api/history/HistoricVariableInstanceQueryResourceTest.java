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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;

import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.flowable.cmmn.api.runtime.CaseInstance;
import org.flowable.cmmn.engine.test.CmmnDeployment;
import org.flowable.cmmn.rest.service.BaseSpringRestTestCase;
import org.flowable.cmmn.rest.service.api.CmmnRestUrls;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Test for REST-operation related to the historic variable instance query resource.
 *
 * @author Tijs Rademakers
 */
public class HistoricVariableInstanceQueryResourceTest extends BaseSpringRestTestCase {

    /**
     * Test querying historic variable instance. POST cmmn-query/historic-variable-instances
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

        String url = CmmnRestUrls.createRelativeResourceUrl(CmmnRestUrls.URL_HISTORIC_VARIABLE_INSTANCE_QUERY);

        ObjectNode requestNode = objectMapper.createObjectNode();
        requestNode.put("variableName", "stringVar");
        assertResultsPresentInDataResponse(url, requestNode, 2, "stringVar", "Azerty");

        requestNode = objectMapper.createObjectNode();
        requestNode.put("variableName", "booleanVar");
        assertResultsPresentInDataResponse(url, requestNode, 2, "booleanVar", false);

        requestNode = objectMapper.createObjectNode();
        requestNode.put("variableName", "booleanVar2");
        assertResultsPresentInDataResponse(url, requestNode, 0, null, null);

        requestNode = objectMapper.createObjectNode();
        requestNode.put("caseInstanceId", caseInstance.getId());
        assertResultsPresentInDataResponse(url, requestNode, 4, "taskVariable", "test");

        requestNode = objectMapper.createObjectNode();
        requestNode.put("caseInstanceId", caseInstance.getId());
        requestNode.put("excludeTaskVariables", true);
        assertResultsPresentInDataResponse(url, requestNode, 3, "intVar", 67890);

        requestNode = objectMapper.createObjectNode();
        requestNode.put("caseInstanceId", caseInstance2.getId());
        assertResultsPresentInDataResponse(url, requestNode, 3, "stringVar", "Azerty");

        requestNode = objectMapper.createObjectNode();
        requestNode.put("taskId", task.getId());
        assertResultsPresentInDataResponse(url, requestNode, 1, "taskVariable", "test");

        requestNode = objectMapper.createObjectNode();
        requestNode.put("taskId", task.getId());
        requestNode.put("variableName", "booleanVar");
        assertResultsPresentInDataResponse(url, requestNode, 0, null, null);

        requestNode = objectMapper.createObjectNode();
        requestNode.put("variableNameLike", "%Var");
        assertResultsPresentInDataResponse(url, requestNode, 6, "stringVar", "Azerty");

        requestNode = objectMapper.createObjectNode();
        requestNode.put("variableNameLike", "%Var2");
        assertResultsPresentInDataResponse(url, requestNode, 0, null, null);

        requestNode = objectMapper.createObjectNode();
        ArrayNode variableArray = objectMapper.createArrayNode();
        ObjectNode variableNode = objectMapper.createObjectNode();
        variableArray.add(variableNode);
        requestNode.set("variables", variableArray);
        variableNode.put("name", "stringVar");
        variableNode.put("value", "Azerty");
        variableNode.put("operation", "equals");
        assertResultsPresentInDataResponse(url, requestNode, 2, "stringVar", "Azerty");

        variableNode.removeAll();
        requestNode.set("variables", variableArray);
        variableNode.put("name", "taskVariable");
        variableNode.put("value", "test");
        variableNode.put("operation", "equals");
        assertResultsPresentInDataResponse(url, requestNode, 1, "taskVariable", "test");

        variableNode.removeAll();
        requestNode.set("variables", variableArray);
        variableNode.put("name", "taskVariable");
        variableNode.put("value", "test");
        variableNode.put("operation", "notEquals");
        assertErrorResult(url, requestNode, HttpStatus.SC_BAD_REQUEST);
    }

    protected void assertResultsPresentInDataResponse(String url, ObjectNode body, int numberOfResultsExpected, String variableName, Object variableValue)
            throws JsonProcessingException, IOException {

        // Do the actual call
        HttpPost httpPost = new HttpPost(SERVER_URL_PREFIX + url);
        httpPost.setEntity(new StringEntity(body.toString()));
        CloseableHttpResponse response = executeRequest(httpPost, HttpStatus.SC_OK);

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
