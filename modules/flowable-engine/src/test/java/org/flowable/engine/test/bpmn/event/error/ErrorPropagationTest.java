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
package org.flowable.engine.test.bpmn.event.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.flowable.engine.impl.test.PluggableFlowableTestCase;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.test.Deployment;
import org.junit.jupiter.api.Test;

/**
 * Testcase for error in method 'propagateError' of class
 * 'org.flowable.engine.impl.bpmn.helper.ErrorPropagation'.
 * 
 * @author Fritsche
 */
public class ErrorPropagationTest extends PluggableFlowableTestCase {

    @Test
    @Deployment(resources = { "org/flowable/engine/test/bpmn/event/error/catchError3.bpmn",
                    "org/flowable/engine/test/bpmn/event/error/catchError4.bpmn", 
                    "org/flowable/engine/test/bpmn/event/error/throwError.bpmn" })
    public void test() {
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("catchError4");
        assertThat(processInstance).isNotNull();

        final org.flowable.task.api.Task task = taskService.createTaskQuery().singleResult();

        assertThat(task.getName()).isEqualTo("MyErrorTaskNested");
    }
}
