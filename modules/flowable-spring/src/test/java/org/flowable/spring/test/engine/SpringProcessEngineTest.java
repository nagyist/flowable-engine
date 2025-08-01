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
package org.flowable.spring.test.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.flowable.engine.ProcessEngines;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Spring process engine base test
 *
 * @author Henry Yan
 */
@SpringJUnitConfig(locations = "classpath:org/flowable/spring/test/engine/springProcessEngine-context.xml")
@DirtiesContext
public class SpringProcessEngineTest {

    @Test
    public void testGetEngineFromCache() {
        assertThat(ProcessEngines.getDefaultProcessEngine()).isNotNull();
        assertThat(ProcessEngines.getProcessEngine("default")).isNotNull();
    }

}
