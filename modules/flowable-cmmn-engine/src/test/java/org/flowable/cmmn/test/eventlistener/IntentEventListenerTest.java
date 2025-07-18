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
package org.flowable.cmmn.test.eventlistener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import org.flowable.cmmn.api.history.HistoricPlanItemInstance;
import org.flowable.cmmn.api.runtime.CaseInstance;
import org.flowable.cmmn.api.runtime.IntentEventListenerInstance;
import org.flowable.cmmn.api.runtime.PlanItemDefinitionType;
import org.flowable.cmmn.api.runtime.PlanItemInstance;
import org.flowable.cmmn.api.runtime.PlanItemInstanceState;
import org.flowable.cmmn.engine.test.CmmnDeployment;
import org.flowable.cmmn.engine.test.impl.CmmnHistoryTestHelper;
import org.flowable.cmmn.test.FlowableCmmnTestCase;
import org.flowable.common.engine.impl.history.HistoryLevel;
import org.junit.jupiter.api.Test;

public class IntentEventListenerTest extends FlowableCmmnTestCase {

    @Test
    @CmmnDeployment
    public void testSimpleEnableTask() {
        //Simple use of the SignalEventListener as EntryCriteria of a Task
        CaseInstance caseInstance = cmmnRuntimeService.createCaseInstanceBuilder().caseDefinitionKey("testSimpleEnableTask").start();
        assertThat(caseInstance).isNotNull();
        assertThat(cmmnRuntimeService.createCaseInstanceQuery().count()).isEqualTo(1);

        //3 PlanItems reachable
        assertThat(cmmnRuntimeService.createPlanItemInstanceQuery().count()).isEqualTo(3);
        assertThat(cmmnRuntimeService.createPlanItemInstanceQuery().list())
                .extracting(PlanItemInstance::getPlanItemDefinitionType, PlanItemInstance::getPlanItemDefinitionId, PlanItemInstance::getState)
                .containsExactlyInAnyOrder(
                        tuple(PlanItemDefinitionType.INTENT_EVENT_LISTENER, "eventListener", PlanItemInstanceState.AVAILABLE),
                        tuple(PlanItemDefinitionType.HUMAN_TASK, "taskA", PlanItemInstanceState.ACTIVE),
                        tuple(PlanItemDefinitionType.HUMAN_TASK, "taskB", PlanItemInstanceState.AVAILABLE)
                );

        //1 Signal Event Listener
        PlanItemInstance listenerInstance = cmmnRuntimeService.createPlanItemInstanceQuery()
                .planItemDefinitionType(PlanItemDefinitionType.INTENT_EVENT_LISTENER).singleResult();
        assertThat(listenerInstance).isNotNull();
        assertThat(listenerInstance.getPlanItemDefinitionId()).isEqualTo("eventListener");
        assertThat(listenerInstance.getState()).isEqualTo(PlanItemInstanceState.AVAILABLE);

        // Verify same result is returned from query
        IntentEventListenerInstance eventListenerInstance = cmmnRuntimeService.createIntentEventListenerInstanceQuery().caseInstanceId(caseInstance.getId())
                .singleResult();
        assertThat(eventListenerInstance).isNotNull();
        assertThat(listenerInstance.getId()).isEqualTo(eventListenerInstance.getId());
        assertThat(listenerInstance.getCaseDefinitionId()).isEqualTo(eventListenerInstance.getCaseDefinitionId());
        assertThat(listenerInstance.getCaseInstanceId()).isEqualTo(eventListenerInstance.getCaseInstanceId());
        assertThat(listenerInstance.getElementId()).isEqualTo(eventListenerInstance.getElementId());
        assertThat(listenerInstance.getName()).isEqualTo(eventListenerInstance.getName());
        assertThat(listenerInstance.getPlanItemDefinitionId()).isEqualTo(eventListenerInstance.getPlanItemDefinitionId());
        assertThat(listenerInstance.getStageInstanceId()).isEqualTo(eventListenerInstance.getStageInstanceId());
        assertThat(listenerInstance.getState()).isEqualTo(eventListenerInstance.getState());

        assertThat(cmmnRuntimeService.createIntentEventListenerInstanceQuery().count()).isEqualTo(1);
        assertThat(cmmnRuntimeService.createIntentEventListenerInstanceQuery().list()).hasSize(1);

        assertThat(cmmnRuntimeService.createIntentEventListenerInstanceQuery().caseDefinitionId(listenerInstance.getCaseDefinitionId()).singleResult())
                .isNotNull();

        //2 HumanTasks ... one active and other waiting (available)
        assertThat(cmmnRuntimeService.createPlanItemInstanceQuery().planItemDefinitionType(PlanItemDefinitionType.HUMAN_TASK).count()).isEqualTo(2);
        PlanItemInstance active = cmmnRuntimeService.createPlanItemInstanceQuery().planItemDefinitionType(PlanItemDefinitionType.HUMAN_TASK)
                .planItemInstanceStateActive().singleResult();
        assertThat(active).isNotNull();
        assertThat(active.getPlanItemDefinitionId()).isEqualTo("taskA");
        PlanItemInstance available = cmmnRuntimeService.createPlanItemInstanceQuery().planItemDefinitionType(PlanItemDefinitionType.HUMAN_TASK)
                .planItemInstanceStateAvailable().singleResult();
        assertThat(available).isNotNull();
        assertThat(available.getPlanItemDefinitionId()).isEqualTo("taskB");

        if (CmmnHistoryTestHelper.isHistoryLevelAtLeast(HistoryLevel.ACTIVITY, cmmnEngineConfiguration)) {
            assertThat(cmmnHistoryService.createHistoricPlanItemInstanceQuery().list())
                    .extracting(HistoricPlanItemInstance::getPlanItemDefinitionType, HistoricPlanItemInstance::getPlanItemDefinitionId, HistoricPlanItemInstance::getState)
                    .containsExactlyInAnyOrder(
                            tuple(PlanItemDefinitionType.INTENT_EVENT_LISTENER, "eventListener", PlanItemInstanceState.AVAILABLE),
                            tuple(PlanItemDefinitionType.HUMAN_TASK, "taskA", PlanItemInstanceState.ACTIVE),
                            tuple(PlanItemDefinitionType.HUMAN_TASK, "taskB", PlanItemInstanceState.AVAILABLE)
                    );
        }

        // Trigger the intent listener instance
        cmmnRuntimeService.completeIntentEventListenerInstance(listenerInstance.getId());

        // SignalEventListener should be completed
        assertThat(cmmnRuntimeService.createPlanItemInstanceQuery().planItemDefinitionType(PlanItemDefinitionType.INTENT_EVENT_LISTENER).count()).isZero();

        // Only 2 PlanItems left
        assertThat(cmmnRuntimeService.createPlanItemInstanceQuery().list()).hasSize(2);
        assertThat(cmmnRuntimeService.createPlanItemInstanceQuery().list())
                .extracting(PlanItemInstance::getPlanItemDefinitionType, PlanItemInstance::getPlanItemDefinitionId, PlanItemInstance::getState)
                .containsExactlyInAnyOrder(
                        tuple(PlanItemDefinitionType.HUMAN_TASK, "taskA", PlanItemInstanceState.ACTIVE),
                        tuple(PlanItemDefinitionType.HUMAN_TASK, "taskB", PlanItemInstanceState.ACTIVE)
                );

        // Both Human task should be "active" now, as the sentry kicks on the SignalEventListener transition
        assertThat(cmmnRuntimeService.createPlanItemInstanceQuery().planItemDefinitionType(PlanItemDefinitionType.HUMAN_TASK).planItemInstanceStateActive()
                .count()).isEqualTo(2);

        if (CmmnHistoryTestHelper.isHistoryLevelAtLeast(HistoryLevel.ACTIVITY, cmmnEngineConfiguration)) {
            assertThat(cmmnHistoryService.createHistoricPlanItemInstanceQuery().list())
                    .extracting(HistoricPlanItemInstance::getPlanItemDefinitionType, HistoricPlanItemInstance::getPlanItemDefinitionId, HistoricPlanItemInstance::getState)
                    .containsExactlyInAnyOrder(
                            tuple(PlanItemDefinitionType.INTENT_EVENT_LISTENER, "eventListener", PlanItemInstanceState.COMPLETED),
                            tuple(PlanItemDefinitionType.HUMAN_TASK, "taskA", PlanItemInstanceState.ACTIVE),
                            tuple(PlanItemDefinitionType.HUMAN_TASK, "taskB", PlanItemInstanceState.ACTIVE)
                    );
        }

        // Finish the case instance
        assertCaseInstanceNotEnded(caseInstance);
        cmmnTaskService.createTaskQuery().list().forEach(t -> cmmnTaskService.complete(t.getId()));
        assertCaseInstanceEnded(caseInstance);
    }

    @Test
    @CmmnDeployment
    public void testTerminateStage() {
        //Test case where the SignalEventListener is used to complete (ExitCriteria) of a Stage
        CaseInstance caseInstance = cmmnRuntimeService.createCaseInstanceBuilder().caseDefinitionKey("testTerminateStage").start();
        assertThat(caseInstance).isNotNull();
        assertThat(cmmnRuntimeService.createCaseInstanceQuery().count()).isEqualTo(1);

        //3 PlanItems reachable
        assertThat(cmmnRuntimeService.createPlanItemInstanceQuery().list()).hasSize(3);

        //1 Stage
        PlanItemInstance stage = cmmnRuntimeService.createPlanItemInstanceQuery().planItemDefinitionType(PlanItemDefinitionType.STAGE).singleResult();
        assertThat(stage).isNotNull();

        //1 User Event Listener
        PlanItemInstance listenerInstance = cmmnRuntimeService.createPlanItemInstanceQuery()
                .planItemDefinitionType(PlanItemDefinitionType.INTENT_EVENT_LISTENER).singleResult();
        assertThat(listenerInstance).isNotNull();
        assertThat(listenerInstance.getPlanItemDefinitionId()).isEqualTo("eventListener");
        assertThat(listenerInstance.getState()).isEqualTo(PlanItemInstanceState.AVAILABLE);

        //1 Task on Active state
        PlanItemInstance task = cmmnRuntimeService.createPlanItemInstanceQuery().planItemDefinitionType("task").singleResult();
        assertThat(task).isNotNull();
        assertThat(task.getState()).isEqualTo(PlanItemInstanceState.ACTIVE);
        assertThat(task.getStageInstanceId()).isEqualTo(stage.getId());

        //Trigger the listener
        assertCaseInstanceNotEnded(caseInstance);
        cmmnRuntimeService.completeIntentEventListenerInstance(listenerInstance.getId());
        assertCaseInstanceEnded(caseInstance);
    }
}

