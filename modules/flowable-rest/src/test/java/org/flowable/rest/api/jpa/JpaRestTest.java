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
package org.flowable.rest.api.jpa;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.test.Deployment;
import org.flowable.rest.api.jpa.model.Message;
import org.flowable.rest.api.jpa.repository.MessageRepository;
import org.flowable.rest.service.api.RestUrls;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;

import net.javacrumbs.jsonunit.core.Option;

public class JpaRestTest extends BaseJPARestTestCase {
    
    @Autowired
    protected MessageRepository messageRepository;
    
    @Test
    @Deployment(resources = { "org/flowable/rest/api/jpa/jpa-process.bpmn20.xml" })
    public void testGetJpaVariableViaTaskVariablesCollections() throws Exception {

        // Get JPA managed entity through the repository
        Message message = messageRepository.findById(1L).orElse(null);
        assertThat(message).isNotNull();
        assertThat(message.getText()).isEqualTo("Hello World");

        // add the entity to the process variables and start the process
        Map<String, Object> processVariables = new HashMap<>();
        processVariables.put("message", message);

        ProcessInstance processInstance = processEngine.getRuntimeService().startProcessInstanceByKey("jpa-process", processVariables);
        assertThat(processInstance).isNotNull();

        Task task = processEngine.getTaskService().createTaskQuery().singleResult();
        assertThat(task.getName()).isEqualTo("Activiti is awesome!");

        // Request all variables (no scope provides) which include global and local
        HttpResponse response = executeRequest(
                new HttpGet(SERVER_URL_PREFIX + RestUrls.createRelativeResourceUrl(RestUrls.URL_TASK_VARIABLES_COLLECTION, task.getId())), HttpStatus.SC_OK);

        JsonNode responseNode = objectMapper.readTree(response.getEntity().getContent()).get(0);

        // check for message variable of type serializable
        assertThat(responseNode).isNotNull();
        assertThatJson(responseNode)
                .when(Option.IGNORING_EXTRA_FIELDS)
                .isEqualTo("{"
                        + "  name: 'message',"
                        + "  scope: 'global',"
                        + "  type: 'serializable',"
                        + "  valueUrl: '${json-unit.any-string}'"
                        + "}");
    }

    @Test
    @Deployment(resources = { "org/flowable/rest/api/jpa/jpa-process.bpmn20.xml" })
    public void testGetJpaVariableViaTaskCollection() throws Exception {

        // Get JPA managed entity through the repository
        Message message = messageRepository.findById(1L).orElse(null);
        assertThat(message).isNotNull();
        assertThat(message.getText()).isEqualTo("Hello World");

        // add the entity to the process variables and start the process
        Map<String, Object> processVariables = new HashMap<>();
        processVariables.put("message", message);

        ProcessInstance processInstance = processEngine.getRuntimeService().startProcessInstanceByKey("jpa-process", processVariables);
        assertThat(processInstance).isNotNull();

        Task task = processEngine.getTaskService().createTaskQuery().singleResult();
        assertThat(task.getName()).isEqualTo("Activiti is awesome!");

        // Request all variables (no scope provides) which include global and local
        HttpResponse response = executeRequest(
                new HttpGet(SERVER_URL_PREFIX + RestUrls.createRelativeResourceUrl(RestUrls.URL_TASK_COLLECTION) + "?includeProcessVariables=true"),
                HttpStatus.SC_OK);

        JsonNode dataNode = objectMapper.readTree(response.getEntity().getContent()).get("data").get(0);
        assertThat(dataNode).isNotNull();

        JsonNode variableNode = dataNode.get("variables").get(0);
        assertThat(variableNode).isNotNull();

        // check for message variable of type serializable
        assertThatJson(variableNode)
                .when(Option.IGNORING_EXTRA_FIELDS)
                .isEqualTo("{"
                        + "name: 'message',"
                        + "type: 'serializable',"
                        + "valueUrl: '${json-unit.any-string}',"
                        + "scope: 'global'"
                        + "}");
    }

    @Test
    @Deployment(resources = { "org/flowable/rest/api/jpa/jpa-process.bpmn20.xml" })
    public void testGetJpaVariableViaHistoricProcessCollection() throws Exception {

        // Get JPA managed entity through the repository
        Message message = messageRepository.findById(1L).orElse(null);
        assertThat(message).isNotNull();
        assertThat(message.getText()).isEqualTo("Hello World");

        // add the entity to the process variables and start the process
        Map<String, Object> processVariables = new HashMap<>();
        processVariables.put("message", message);

        ProcessInstance processInstance = processEngine.getRuntimeService().startProcessInstanceByKey("jpa-process", processVariables);
        assertThat(processInstance).isNotNull();

        Task task = processEngine.getTaskService().createTaskQuery().singleResult();
        assertThat(task.getName()).isEqualTo("Activiti is awesome!");

        // Request all variables (no scope provides) which include global and
        // local
        HttpResponse response = executeRequest(
                new HttpGet(SERVER_URL_PREFIX + RestUrls.createRelativeResourceUrl(RestUrls.URL_HISTORIC_PROCESS_INSTANCES) + "?processInstanceId="
                        + processInstance.getId() + "&includeProcessVariables=true"),
                HttpStatus.SC_OK);

        JsonNode responseNode = objectMapper.readTree(response.getEntity().getContent());

        // check for message variable of type serializable
        assertThat(responseNode).isNotNull();
        assertThatJson(responseNode)
                .when(Option.IGNORING_EXTRA_FIELDS)
                .isEqualTo("{data: ["
                        + "   {"
                        + "     variables: [{"
                        + "                    name: 'message',"
                        + "                    type: 'serializable',"
                        + "                    valueUrl: '${json-unit.any-string}'"
                        + "                }]"
                        + "   }"
                        + "]}");
    }

    @Test
    @Deployment(resources = { "org/flowable/rest/api/jpa/jpa-process.bpmn20.xml" })
    public void testGetJpaVariableViaHistoricVariablesCollections() throws Exception {

        // Get JPA managed entity through the repository
        Message message = messageRepository.findById(1L).orElse(null);
        assertThat(message).isNotNull();
        assertThat(message.getText()).isEqualTo("Hello World");

        // add the entity to the process variables and start the process
        Map<String, Object> processVariables = new HashMap<>();
        processVariables.put("message", message);

        ProcessInstance processInstance = processEngine.getRuntimeService().startProcessInstanceByKey("jpa-process", processVariables);
        assertThat(processInstance).isNotNull();

        Task task = processEngine.getTaskService().createTaskQuery().singleResult();
        assertThat(task.getName()).isEqualTo("Activiti is awesome!");

        // Request all variables (no scope provides) which include global and local
        HttpResponse response = executeRequest(
                new HttpGet(SERVER_URL_PREFIX + RestUrls.createRelativeResourceUrl(RestUrls.URL_HISTORIC_VARIABLE_INSTANCES) + "?processInstanceId="
                        + processInstance.getId()), HttpStatus.SC_OK);

        JsonNode responseNode = objectMapper.readTree(response.getEntity().getContent());

        // check for message variable of type serializable
        assertThat(responseNode).isNotNull();
        assertThatJson(responseNode)
                .when(Option.IGNORING_EXTRA_FIELDS)
                .isEqualTo("{data: ["
                        + "{"
                        + "     variable: {"
                        + "                    name: 'message',"
                        + "                    type: 'serializable',"
                        + "                    valueUrl: '${json-unit.any-string}'"
                        + "                }"
                        + "}]}");
    }
}
