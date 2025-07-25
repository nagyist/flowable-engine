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

package org.flowable.rest.service.api.history;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.flowable.engine.impl.cmd.ChangeDeploymentTenantIdCmd;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.test.Deployment;
import org.flowable.rest.service.BaseSpringRestTestCase;
import org.flowable.rest.service.api.RestUrls;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;

import net.javacrumbs.jsonunit.core.Option;

/**
 * Test for REST-operation related to the historic task instance query resource.
 * 
 * @author Tijs Rademakers
 */
public class HistoricTaskInstanceCollectionResourceTest extends BaseSpringRestTestCase {

    protected ISO8601DateFormat dateFormat = new ISO8601DateFormat();

    /**
     * Test querying historic task instance. GET history/historic-task-instances
     */
    @Test
    @Deployment
    public void testQueryTaskInstances() throws Exception {
        HashMap<String, Object> processVariables = new HashMap<>();
        processVariables.put("stringVar", "Azerty");
        processVariables.put("intVar", 67890);
        processVariables.put("booleanVar", false);

        Calendar created = Calendar.getInstance();
        created.set(Calendar.YEAR, 2001);
        created.set(Calendar.MILLISECOND, 0);
        processEngineConfiguration.getClock().setCurrentTime(created.getTime());

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess", "testBusinessKey", processVariables);
        processEngineConfiguration.getClock().reset();
        Task task1 = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
        taskService.complete(task1.getId());
        Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
        taskService.setVariableLocal(task.getId(), "local", "test");
        taskService.setOwner(task.getId(), "test");
        taskService.setDueDate(task.getId(), new GregorianCalendar(2013, 0, 1).getTime());

        // Set tenant on deployment
        managementService.executeCommand(new ChangeDeploymentTenantIdCmd(deploymentId, "myTenant"));

        ProcessInstance processInstance2 = runtimeService.startProcessInstanceByKeyAndTenantId("oneTaskProcess", processVariables, "myTenant");
        Task task2 = taskService.createTaskQuery().processInstanceId(processInstance2.getId()).singleResult();

        String url = RestUrls.createRelativeResourceUrl(RestUrls.URL_HISTORIC_TASK_INSTANCES);

        assertResultsPresentInDataResponse(url, 3, task.getId(), task2.getId());

        assertResultsPresentInDataResponse(url + "?processDefinitionName=" + "The%20One%20Task%20Process", 3, task.getId());

        assertResultsPresentInDataResponse(url + "?processDefinitionNameLike=" + "The%25", 3, task.getId());

        assertResultsPresentInDataResponse(url + "?processDefinitionKey=" + "oneTaskProcess", 3, task.getId());

        assertResultsPresentInDataResponse(url + "?processDefinitionKeyLike=" + "oneTask%25", 3, task.getId());

        assertResultsPresentInDataResponse(url + "?taskMinPriority=" + "0", 3, task.getId());

        assertResultsPresentInDataResponse(url + "?taskMaxPriority=" + "60", 3, task.getId());

        assertResultsPresentInDataResponse(url + "?processBusinessKey=" + "testBusinessKey", 2, task.getId());

        assertResultsPresentInDataResponse(url + "?processBusinessKeyLike=" + "testBusin%25", 2, task.getId());

        assertResultsPresentInDataResponse(url + "?processInstanceId=" + processInstance.getId(), 2, task.getId());

        assertResultsPresentInDataResponse(url + "?processInstanceId=" + processInstance2.getId(), 1, task2.getId());
        
        assertResultsPresentInDataResponse(url + "?processInstanceIdWithChildren=" + processInstance.getId(), 2, task.getId());
        
        assertResultsPresentInDataResponse(url + "?processInstanceIdWithChildren=nonexisting", 0);
        
        assertResultsPresentInDataResponse(url + "?withoutProcessInstanceId=true", 0);

        assertResultsPresentInDataResponse(url + "?taskAssignee=kermit", 2, task2.getId());

        assertResultsPresentInDataResponse(url + "?taskAssigneeLike=" + encode("%mit"), 2, task2.getId());

        assertResultsPresentInDataResponse(url + "?taskAssignee=fozzie", 1, task.getId());

        assertResultsPresentInDataResponse(url + "?taskOwner=test", 1, task.getId());

        assertResultsPresentInDataResponse(url + "?taskOwnerLike=" + encode("t%"), 1, task.getId());

        assertResultsPresentInDataResponse(url + "?taskInvolvedUser=test", 1, task.getId());

        assertResultsPresentInDataResponse(url + "?taskDefinitionKey=processTask2", 1, task.getId());

        assertResultsPresentInDataResponse(url + "?taskDefinitionKeys=processTask,processTask2", 3, task.getId(), task1.getId(), task2.getId());

        assertResultsPresentInDataResponse(url + "?dueDateAfter=" + dateFormat.format(new GregorianCalendar(2010, 0, 1).getTime()), 1, task.getId());

        assertResultsPresentInDataResponse(url + "?dueDateAfter=" + dateFormat.format(new GregorianCalendar(2013, 4, 1).getTime()), 0);

        assertResultsPresentInDataResponse(url + "?dueDateBefore=" + dateFormat.format(new GregorianCalendar(2010, 0, 1).getTime()), 0);

        assertResultsPresentInDataResponse(url + "?dueDateBefore=" + dateFormat.format(new GregorianCalendar(2013, 4, 1).getTime()), 1, task.getId());

        assertResultsPresentInDataResponse(url + "?taskCreatedOn=" + dateFormat.format(created.getTime()), 1, task1.getId());

        created.set(Calendar.YEAR, 2002);
        assertResultsPresentInDataResponse(url + "?taskCreatedBefore=" + dateFormat.format(created.getTime()), 1, task1.getId());

        created.set(Calendar.YEAR, 2000);
        assertResultsPresentInDataResponse(url + "?taskCreatedAfter=" + dateFormat.format(created.getTime()), 3, task1.getId(), task2.getId());

        // Without tenant id
        assertResultsPresentInDataResponse(url + "?withoutTenantId=true", 2, task.getId(), task1.getId());
        
        assertResultsPresentInDataResponse(url + "?withoutScopeId=true", 3, task.getId(), task1.getId(), task2.getId());

        // Tenant id
        assertResultsPresentInDataResponse(url + "?tenantId=myTenant", 1, task2.getId());
        assertResultsPresentInDataResponse(url + "?tenantId=anotherTenant", 0);

        // Tenant id like
        assertResultsPresentInDataResponse(url + "?tenantIdLike=" + encode("%enant"), 1, task2.getId());
        assertResultsPresentInDataResponse(url + "?tenantIdLike=anotherTenant", 0);

    }

    @Test
    @Deployment
    public void testQueryTaskInstancesWithCandidateGroup() throws Exception {
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");
        Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();

        String url = RestUrls.createRelativeResourceUrl(RestUrls.URL_HISTORIC_TASK_INSTANCES);

        assertResultsPresentInDataResponse(url + "?taskCandidateGroup=sales", 1, task.getId());
        assertEmptyResultsPresentInDataResponse(url + "?taskCandidateGroup=notExisting");

        taskService.claim(task.getId(), "johnDoe");
        assertEmptyResultsPresentInDataResponse(url + "?taskCandidateGroup=sales");
        assertResultsPresentInDataResponse(url + "?taskCandidateGroup=sales&ignoreTaskAssignee=true", 1, task.getId());
    }

    @Test
    @Deployment(resources = {
            "org/flowable/rest/service/api/runtime/simpleParallelCallActivity.bpmn20.xml",
            "org/flowable/rest/service/api/runtime/simpleInnerCallActivity.bpmn20.xml",
            "org/flowable/rest/service/api/runtime/simpleProcessWithUserTasks.bpmn20.xml",
            "org/flowable/rest/service/api/oneTaskProcess.bpmn20.xml"
    })
    public void testQueryByRootScopeId() throws IOException {
        runtimeService.startProcessInstanceByKey("oneTaskProcess");
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("simpleParallelCallActivity");

        List<String> taskExecutionIds = runtimeService.createExecutionQuery().rootProcessInstanceId(processInstance.getId())
                .processDefinitionKey("oneTaskProcess").activityId("theTask").list().stream().map(Execution::getId).toList();

        Task task1 = taskService.createTaskQuery().executionId(taskExecutionIds.get(0)).singleResult();
        Task task2 = taskService.createTaskQuery().executionId(taskExecutionIds.get(1)).singleResult();
        Task task3 = taskService.createTaskQuery().executionId(taskExecutionIds.get(2)).singleResult();

        Execution formTask1Execution = runtimeService.createExecutionQuery().rootProcessInstanceId(processInstance.getId()).activityId("formTask1")
                .singleResult();
        Task formTask1 = taskService.createTaskQuery().executionId(formTask1Execution.getId()).singleResult();

        Execution taskForm2Execution = runtimeService.createExecutionQuery().rootProcessInstanceId(processInstance.getId()).activityId("formTask2")
                .singleResult();
        Task formTask2 = taskService.createTaskQuery().executionId(taskForm2Execution.getId()).singleResult();

        taskService.createTaskQuery().list().forEach(task -> taskService.complete(task.getId()));

        String url = SERVER_URL_PREFIX + RestUrls.createRelativeResourceUrl(RestUrls.URL_HISTORIC_TASK_INSTANCES) + "?rootScopeId="
                + processInstance.getId();

        CloseableHttpResponse response = executeRequest(new HttpGet(url), HttpStatus.SC_OK);

        JsonNode responseNode = objectMapper.readTree(response.getEntity().getContent());
        closeResponse(response);
        assertThatJson(responseNode)
                .when(Option.IGNORING_EXTRA_FIELDS, Option.IGNORING_ARRAY_ORDER)
                .isEqualTo("{"
                        + "  data: ["
                        + "    { id: '" + task1.getId() + "' },"
                        + "    { id: '" + task2.getId() + "' },"
                        + "    { id: '" + task3.getId() + "' },"
                        + "    { id: '" + formTask1.getId() + "' },"
                        + "    { id: '" + formTask2.getId() + "' }"
                        + "  ]"
                        + "}");
    }

    @Test
    @Deployment(resources = {
            "org/flowable/rest/service/api/runtime/simpleParallelCallActivity.bpmn20.xml",
            "org/flowable/rest/service/api/runtime/simpleInnerCallActivity.bpmn20.xml",
            "org/flowable/rest/service/api/runtime/simpleProcessWithUserTasks.bpmn20.xml",
            "org/flowable/rest/service/api/oneTaskProcess.bpmn20.xml"
    })
    public void testQueryByParentScopeId() throws IOException {
        runtimeService.startProcessInstanceByKey("oneTaskProcess");
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("simpleParallelCallActivity");

        Execution formTask1Execution = runtimeService.createExecutionQuery().rootProcessInstanceId(processInstance.getId()).activityId("formTask1")
                .singleResult();
        Task formTask1 = taskService.createTaskQuery().executionId(formTask1Execution.getId()).singleResult();

        Execution taskForm2Execution = runtimeService.createExecutionQuery().rootProcessInstanceId(processInstance.getId()).activityId("formTask2")
                .singleResult();
        Task formTask2 = taskService.createTaskQuery().executionId(taskForm2Execution.getId()).singleResult();

        taskService.createTaskQuery().list().forEach(task -> taskService.complete(task.getId()));

        String url = SERVER_URL_PREFIX + RestUrls.createRelativeResourceUrl(RestUrls.URL_HISTORIC_TASK_INSTANCES) + "?parentScopeId="
                + taskForm2Execution.getProcessInstanceId();

        CloseableHttpResponse response = executeRequest(new HttpGet(url), HttpStatus.SC_OK);

        JsonNode responseNode = objectMapper.readTree(response.getEntity().getContent());
        closeResponse(response);
        assertThatJson(responseNode)
                .when(Option.IGNORING_EXTRA_FIELDS, Option.IGNORING_ARRAY_ORDER)
                .isEqualTo("{"
                        + "  data: ["
                        + "    { id: '" + formTask1.getId() + "' },"
                        + "    { id: '" + formTask2.getId() + "' }"

                        + "  ]"
                        + "}");

    }


    protected void assertResultsPresentInDataResponse(String url, int numberOfResultsExpected, String... expectedTaskIds) throws JsonProcessingException, IOException {

        // Do the actual call
        CloseableHttpResponse response = executeRequest(new HttpGet(SERVER_URL_PREFIX + url), HttpStatus.SC_OK);

        // Check status and size
        JsonNode dataNode = objectMapper.readTree(response.getEntity().getContent()).get("data");
        closeResponse(response);
        assertThat(dataNode).hasSize(numberOfResultsExpected);

        // Check presence of ID's
        if (expectedTaskIds != null) {
            List<String> toBeFound = new ArrayList<>(Arrays.asList(expectedTaskIds));
            Iterator<JsonNode> it = dataNode.iterator();
            while (it.hasNext()) {
                String id = it.next().get("id").textValue();
                toBeFound.remove(id);
            }
            assertThat(toBeFound).as("Not all entries have been found in result, missing: " + StringUtils.join(toBeFound, ", ")).isEmpty();
        }
    }
}
