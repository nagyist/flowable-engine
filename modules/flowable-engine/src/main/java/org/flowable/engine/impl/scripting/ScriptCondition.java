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
package org.flowable.engine.impl.scripting;

import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.impl.scripting.ScriptEngineRequest;
import org.flowable.common.engine.impl.scripting.ScriptingEngines;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.impl.Condition;
import org.flowable.engine.impl.util.CommandContextUtil;

/**
 * @author Tom Baeyens
 */
public class ScriptCondition implements Condition {

    private final String expression;
    private final String language;

    public ScriptCondition(String expression, String language) {
        this.expression = expression;
        this.language = language;
    }

    @Override
    public boolean evaluate(String elementId, DelegateExecution execution) {
        ScriptingEngines scriptingEngines = CommandContextUtil.getProcessEngineConfiguration().getScriptingEngines();

        ScriptEngineRequest.Builder builder = ScriptEngineRequest.builder()
                .script(expression)
                .language(language)
                .scopeContainer(execution);
        Object result = scriptingEngines.evaluate(builder.build()).getResult();
        if (result == null) {
            throw new FlowableException("condition script returns null: " + expression + " for " + execution);
        }
        if (!(result instanceof Boolean)) {
            throw new FlowableException("condition script returns non-Boolean: " + result + " (" + result.getClass().getName() + ") for " + execution);
        }
        return (Boolean) result;
    }

}
