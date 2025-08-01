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

package org.flowable.cmmn.rest.service.api.runtime.planitem;

import java.util.Map;

import org.flowable.common.rest.api.DataResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
 * @author Tijs Rademakers
 */
@RestController
@Api(tags = { "Plan Item Instances" }, description = "Manage Plan Item Instances", authorizations = { @Authorization(value = "basicAuth") })
public class PlanItemInstanceQueryResource extends PlanItemInstanceBaseResource {

    @ApiOperation(value = "Query plan item instances", tags = {"Plan Item Instances", "Query" }, nickname = "queryPlanItemInstances",
            notes = "The request body can contain all possible filters that can be used in the List plan item instances URL query. On top of these, it’s possible to provide an array of variables and caseInstanceVariables to include in the query, with their format described here.\n"
            + "\n" + "The general paging and sorting query-parameters can be used for this URL.")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "sort", dataType = "string", value = "The field to sort by. Defaults to 'createTime'.", allowableValues = "name,createTime,startTime", paramType = "body"),
    })
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Indicates request was successful and the plan item instances are returned."),
            @ApiResponse(code = 404, message = "Indicates a parameter was passed in the wrong format. The status message contains additional information.")
    })
    @PostMapping(value = "/cmmn-query/plan-item-instances", produces = "application/json")
    public DataResponse<PlanItemInstanceResponse> queryPlanItemInstances(@RequestBody PlanItemInstanceQueryRequest queryRequest, 
                    @ApiParam(hidden = true) @RequestParam Map<String, String> allRequestParams) {

        return getQueryResponse(queryRequest, allRequestParams);
    }
}
