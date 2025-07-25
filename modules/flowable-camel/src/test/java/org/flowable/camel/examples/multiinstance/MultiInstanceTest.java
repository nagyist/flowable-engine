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

package org.flowable.camel.examples.multiinstance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.test.Deployment;
import org.flowable.job.api.Job;
import org.flowable.spring.impl.test.SpringFlowableTestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

/**
 * @author Saeid Mirzaei
 */
@Tag("camel")
@ContextConfiguration("classpath:generic-camel-flowable-context.xml")
public class MultiInstanceTest extends SpringFlowableTestCase {

    @Autowired
    protected CamelContext camelContext;

    @BeforeEach
    public void setUp() throws Exception {
        camelContext.addRoutes(new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                from("flowable:miProcessExample:serviceTask1").to("seda:continueAsync1");
                from("seda:continueAsync1").to("bean:sleepBean?method=sleep").to("flowable:miProcessExample:receive1");
            }
        });
    }

    @Test
    @Deployment(resources = { "process/multiinstanceReceive.bpmn20.xml" })
    public void testRunProcess() {
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("miProcessExample");
        List<Job> jobList = managementService.createJobQuery().list();
        assertThat(jobList).hasSize(5);

        assertThat(runtimeService.createExecutionQuery()
                .processInstanceId(processInstance.getId())
                .activityId("serviceTask1")
                .count()).isEqualTo(5);

        waitForJobExecutorToProcessAllJobs(3000, 500);
        await("Receive from Camel")
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(processInstance.getId()).list()).isEmpty());
    }
}
