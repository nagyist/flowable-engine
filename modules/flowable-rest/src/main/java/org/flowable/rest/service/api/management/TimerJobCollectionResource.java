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

package org.flowable.rest.service.api.management;

import static org.flowable.common.rest.api.PaginateListUtil.paginateList;

import java.util.Arrays;
import java.util.Map;

import org.flowable.common.engine.api.FlowableIllegalArgumentException;
import org.flowable.common.rest.api.DataResponse;
import org.flowable.common.rest.api.RequestUtil;
import org.flowable.engine.ManagementService;
import org.flowable.job.api.TimerJobQuery;
import org.flowable.rest.service.api.BpmnRestApiInterceptor;
import org.flowable.rest.service.api.RestResponseFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;

/**
 * @author Joram Barrez
 */
@RestController
@Api(tags = { "Jobs" }, description = "Manage Jobs", authorizations = { @Authorization(value = "basicAuth") })
public class TimerJobCollectionResource {

    @Autowired
    protected RestResponseFactory restResponseFactory;

    @Autowired
    protected ManagementService managementService;
    
    @Autowired(required=false)
    protected BpmnRestApiInterceptor restApiInterceptor;

    // Fixme documentation & real parameters
    @ApiOperation(value = "List timer jobs", tags = { "Jobs" }, nickname = "listTimerJobs")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", dataType = "string", value = "Only return job with the given id", paramType = "query"),
            @ApiImplicitParam(name = "processInstanceId", dataType = "string", value = "Only return jobs part of a process with the given id", paramType = "query"),
            @ApiImplicitParam(name = "withoutProcessInstanceId", dataType = "boolean", value = "If true, only returns jobs without a process instance id set. If false, the withoutProcessInstanceId parameter is ignored.", paramType = "query"),
            @ApiImplicitParam(name = "executionId", dataType = "string", value = "Only return jobs part of an execution with the given id", paramType = "query"),
            @ApiImplicitParam(name = "processDefinitionId", dataType = "string", value = "Only return jobs with the given process definition id", paramType = "query"),
            @ApiImplicitParam(name = "elementId", dataType = "string", value = "Only return jobs with the given element id", paramType = "query"),
            @ApiImplicitParam(name = "elementName", dataType = "string", value = "Only return jobs with the given element name", paramType = "query"),
            @ApiImplicitParam(name = "handlerType", dataType = "string", value = "Only return jobs with the given handler type", paramType = "query"),
            @ApiImplicitParam(name = "handlerTypes", dataType = "string", value = "Only return jobs which have one of the given job handler type", paramType = "query"),
            @ApiImplicitParam(name = "executable", dataType = "boolean", value = "If true, only return jobs which are executable. If false, this parameter is ignored.", paramType = "query"),
            @ApiImplicitParam(name = "timersOnly", dataType = "boolean", value = "If true, only return jobs which are timers. If false, this parameter is ignored. Cannot be used together with 'messagesOnly'.", paramType = "query"),
            @ApiImplicitParam(name = "messagesOnly", dataType = "boolean", value = "If true, only return jobs which are messages. If false, this parameter is ignored. Cannot be used together with 'timersOnly'", paramType = "query"),
            @ApiImplicitParam(name = "withException", dataType = "boolean", value = "If true, only return jobs for which an exception occurred while executing it. If false, this parameter is ignored.", paramType = "query"),
            @ApiImplicitParam(name = "dueBefore", dataType = "string", format="date-time", value = "Only return jobs which are due to be executed before the given date. Jobs without duedate are never returned using this parameter.", paramType = "query"),
            @ApiImplicitParam(name = "dueAfter", dataType = "string", format="date-time", value = "Only return jobs which are due to be executed after the given date. Jobs without duedate are never returned using this parameter.", paramType = "query"),
            @ApiImplicitParam(name = "exceptionMessage", dataType = "string", value = "Only return jobs with the given exception message", paramType = "query"),
            @ApiImplicitParam(name = "tenantId", dataType = "string", value = "Only return jobs with the given tenantId.", paramType = "query"),
            @ApiImplicitParam(name = "tenantIdLike", dataType = "string", value = "Only return jobs with a tenantId like the given value.", paramType = "query"),
            @ApiImplicitParam(name = "withoutTenantId", dataType = "boolean", value = "If true, only returns jobs without a tenantId set. If false, the withoutTenantId parameter is ignored.", paramType = "query"),
            @ApiImplicitParam(name = "locked", dataType = "boolean", value = "If true, only return jobs which are locked.  If false, this parameter is ignored.", paramType = "query"),
            @ApiImplicitParam(name = "unlocked", dataType = "boolean", value = "If true, only return jobs which are unlocked. If false, this parameter is ignored.", paramType = "query"),
            @ApiImplicitParam(name = "withoutScopeId", dataType = "boolean", value = "If true, only returns jobs without a scope id set. If false, the withoutScopeId parameter is ignored.", paramType = "query"),
            @ApiImplicitParam(name = "withoutScopeType", dataType = "boolean", value = "If true, only returns jobs without a scope type set. If false, the withoutScopeType parameter is ignored.", paramType = "query"),
            @ApiImplicitParam(name = "sort", dataType = "string", value = "Property to sort on, to be used together with the order.", allowableValues = "id,dueDate,executionId,processInstanceId,retries,tenantId", paramType = "query"),
            @ApiImplicitParam(name = "order", dataType = "string", value = "The sort order, either 'asc' or 'desc'. Defaults to 'asc'.", paramType = "query"),
            @ApiImplicitParam(name = "start", dataType = "integer", value = "Index of the first row to fetch. Defaults to 0.", paramType = "query"),
            @ApiImplicitParam(name = "size", dataType = "integer", value = "Number of rows to fetch, starting from start. Defaults to 10.", paramType = "query"),
    })
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Indicates the requested jobs were returned."),
            @ApiResponse(code = 400, message = "Indicates an illegal value has been used in a url query parameter or the both 'messagesOnly' and 'timersOnly' are used as parameters. Status description contains additional details about the error.")
    })
    @GetMapping(value = "/management/timer-jobs", produces = "application/json")
    public DataResponse<JobResponse> getJobs(@ApiParam(hidden = true) @RequestParam Map<String, String> allRequestParams) {
        TimerJobQuery query = managementService.createTimerJobQuery();

        if (allRequestParams.containsKey("id")) {
            query.jobId(allRequestParams.get("id"));
        }
        if (allRequestParams.containsKey("processInstanceId")) {
            query.processInstanceId(allRequestParams.get("processInstanceId"));
        }
        if (allRequestParams.containsKey("withoutProcessInstanceId") && Boolean.parseBoolean(allRequestParams.get("withoutProcessInstanceId"))) {
            query.withoutProcessInstanceId();
        }
        if (allRequestParams.containsKey("executionId")) {
            query.executionId(allRequestParams.get("executionId"));
        }
        if (allRequestParams.containsKey("processDefinitionId")) {
            query.processDefinitionId(allRequestParams.get("processDefinitionId"));
        }
        if (allRequestParams.containsKey("elementId")) {
            query.elementId(allRequestParams.get("elementId"));
        }
        if (allRequestParams.containsKey("handlerType")) {
            query.handlerType(allRequestParams.get("handlerType"));
        }
        if (allRequestParams.containsKey("handlerTypes")) {
            query.handlerTypes(Arrays.asList(allRequestParams.get("handlerTypes").split(",")));
        }
        if (allRequestParams.containsKey("elementName")) {
            query.elementName(allRequestParams.get("elementName"));
        }
        if (allRequestParams.containsKey("executable")) {
            query.executable();
        }
        if (allRequestParams.containsKey("timersOnly")) {
            if (allRequestParams.containsKey("messagesOnly")) {
                throw new FlowableIllegalArgumentException("Only one of 'timersOnly' or 'messagesOnly' can be provided.");
            }
            if (Boolean.parseBoolean(allRequestParams.get("timersOnly"))) {
                query.timers();
            }
        }
        if (allRequestParams.containsKey("messagesOnly") && Boolean.parseBoolean(allRequestParams.get("messagesOnly"))) {
            query.messages();
        }
        if (allRequestParams.containsKey("dueBefore")) {
            query.duedateLowerThan(RequestUtil.getDate(allRequestParams, "dueBefore"));
        }
        if (allRequestParams.containsKey("dueAfter")) {
            query.duedateHigherThan(RequestUtil.getDate(allRequestParams, "dueAfter"));
        }
        if (allRequestParams.containsKey("withException") && Boolean.parseBoolean(allRequestParams.get("withException"))) {
            query.withException();
        }
        if (allRequestParams.containsKey("exceptionMessage")) {
            query.exceptionMessage(allRequestParams.get("exceptionMessage"));
        }
        if (allRequestParams.containsKey("tenantId")) {
            query.jobTenantId(allRequestParams.get("tenantId"));
        }
        if (allRequestParams.containsKey("tenantIdLike")) {
            query.jobTenantIdLike(allRequestParams.get("tenantIdLike"));
        }
        if (allRequestParams.containsKey("withoutTenantId") && Boolean.parseBoolean(allRequestParams.get("withoutTenantId"))) {
            query.jobWithoutTenantId();
        }
        if (allRequestParams.containsKey("withoutScopeType") && Boolean.parseBoolean(allRequestParams.get("withoutScopeType"))) {
            query.withoutScopeType();
        }
        if (allRequestParams.containsKey("withoutScopeId") && Boolean.parseBoolean(allRequestParams.get("withoutScopeId"))) {
            query.withoutScopeId();
        }
        
        if (restApiInterceptor != null) {
            restApiInterceptor.accessTimerJobInfoWithQuery(query);
        }

        return paginateList(allRequestParams, query, "id", JobQueryProperties.PROPERTIES, restResponseFactory::createTimerJobResponseList);
    }
}
