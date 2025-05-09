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
package org.flowable.cmmn.engine.impl.cmd;

import org.flowable.cmmn.engine.CmmnEngineConfiguration;
import org.flowable.cmmn.engine.impl.persistence.entity.PlanItemInstanceEntity;
import org.flowable.cmmn.engine.impl.util.CommandContextUtil;
import org.flowable.common.engine.api.FlowableIllegalArgumentException;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.common.engine.impl.interceptor.Command;
import org.flowable.common.engine.impl.interceptor.CommandContext;

public class SetLocalVariableAsyncCmd extends AbstractSetVariableAsyncCmd implements Command<Void> {
    
    protected String planItemInstanceId;
    protected String variableName;
    protected Object variableValue;
    
    public SetLocalVariableAsyncCmd(String planItemInstanceId, String variableName, Object variableValue) {
        this.planItemInstanceId = planItemInstanceId;
        this.variableName = variableName;
        this.variableValue = variableValue;
    }
    
    @Override
    public Void execute(CommandContext commandContext) {
        if (planItemInstanceId == null) {
            throw new FlowableIllegalArgumentException("planItemInstanceId is null");
        }
        if (variableName == null) {
            throw new FlowableIllegalArgumentException("variable name is null");
        }
     
        CmmnEngineConfiguration cmmnEngineConfiguration = CommandContextUtil.getCmmnEngineConfiguration(commandContext);
        PlanItemInstanceEntity planItemInstanceEntity = cmmnEngineConfiguration.getPlanItemInstanceEntityManager().findById(planItemInstanceId);
        if (planItemInstanceEntity == null) {
            throw new FlowableObjectNotFoundException("No plan item instance found for id " + planItemInstanceId, PlanItemInstanceEntity.class);
        }
        
        addVariable(true, planItemInstanceEntity.getCaseInstanceId(), planItemInstanceEntity.getId(), variableName, variableValue, planItemInstanceEntity.getTenantId(), 
                cmmnEngineConfiguration.getVariableServiceConfiguration().getVariableService());
        createSetAsyncVariablesJob(planItemInstanceEntity, cmmnEngineConfiguration);
        
        return null;
    }

}
