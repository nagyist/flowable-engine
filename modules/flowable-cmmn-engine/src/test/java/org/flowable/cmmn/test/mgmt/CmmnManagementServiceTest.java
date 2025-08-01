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
package org.flowable.cmmn.test.mgmt;

import static org.assertj.core.api.Assertions.assertThat;

import org.flowable.cmmn.api.runtime.CaseInstance;
import org.flowable.cmmn.engine.test.CmmnDeployment;
import org.flowable.cmmn.test.FlowableCmmnTestCase;
import org.flowable.common.engine.api.scope.ScopeTypes;
import org.flowable.job.api.Job;
import org.flowable.job.service.impl.persistence.entity.SuspendedJobEntity;
import org.flowable.job.service.impl.persistence.entity.SuspendedJobEntityManager;
import org.junit.jupiter.api.Test;

/**
 * @author Joram Barrez
 */
public class CmmnManagementServiceTest extends FlowableCmmnTestCase {

    @Test
    @CmmnDeployment(resources = "org/flowable/cmmn/test/one-task-model.cmmn")
    public void moveSuspendedJobToExecutableJobTest() {

        CaseInstance caseInstance = cmmnRuntimeService.createCaseInstanceBuilder().caseDefinitionKey("oneTaskCase").start();

        SuspendedJobEntity jobEntity = cmmnEngineConfiguration.getCommandExecutor().execute(commandContext -> {
            SuspendedJobEntityManager suspendedJobEntityManager = cmmnEngineConfiguration.getJobServiceConfiguration().getSuspendedJobEntityManager();
            SuspendedJobEntity suspendedJobEntity = suspendedJobEntityManager.create();
            suspendedJobEntity.setScopeId(caseInstance.getId());
            suspendedJobEntity.setSubScopeId("testSubScopeId");
            suspendedJobEntity.setScopeType(ScopeTypes.CMMN);
            suspendedJobEntity.setJobType(SuspendedJobEntity.JOB_TYPE_MESSAGE);
            suspendedJobEntity.setJobHandlerType("testJobHandlerType");

            suspendedJobEntityManager.insert(suspendedJobEntity);
            return suspendedJobEntity;
        });

        assertThat(cmmnManagementService.createJobQuery().count()).isZero();
        assertThat(cmmnManagementService.createSuspendedJobQuery().count()).isEqualTo(1);

        Job executableJob = cmmnManagementService.moveSuspendedJobToExecutableJob(jobEntity.getId());
        assertThat(executableJob).isNotNull();

        assertThat(cmmnManagementService.createJobQuery().count()).isEqualTo(1);
        assertThat(cmmnManagementService.createSuspendedJobQuery().count()).isZero();
    }

}
