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
package org.flowable.cdi.test.api.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import org.flowable.cdi.BusinessProcess;
import org.flowable.cdi.test.CdiFlowableTestCase;
import org.flowable.engine.test.Deployment;
import org.junit.jupiter.api.Test;

/**
 * 
 * @author Daniel Meyer
 */
public class ProcessIdTest extends CdiFlowableTestCase {

    @Test
    @Deployment
    public void testProcessIdInjectable() {
        getBeanInstance(BusinessProcess.class).startProcessByKey("keyOfTheProcess");
        assertThat(getBeanInstance("processInstanceId")).isNotNull();
    }

}
