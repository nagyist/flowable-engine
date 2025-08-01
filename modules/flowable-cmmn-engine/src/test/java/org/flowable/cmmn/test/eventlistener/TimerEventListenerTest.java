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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import org.assertj.core.groups.Tuple;
import org.flowable.cmmn.api.history.HistoricPlanItemInstance;
import org.flowable.cmmn.api.runtime.CaseInstance;
import org.flowable.cmmn.api.runtime.PlanItemDefinitionType;
import org.flowable.cmmn.api.runtime.PlanItemInstance;
import org.flowable.cmmn.api.runtime.PlanItemInstanceState;
import org.flowable.cmmn.engine.test.CmmnDeployment;
import org.flowable.cmmn.engine.test.impl.CmmnHistoryTestHelper;
import org.flowable.cmmn.engine.test.impl.CmmnJobTestHelper;
import org.flowable.cmmn.model.HumanTask;
import org.flowable.cmmn.model.Stage;
import org.flowable.cmmn.model.TimerEventListener;
import org.flowable.cmmn.test.FlowableCmmnTestCase;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.common.engine.api.scope.ScopeTypes;
import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.job.api.Job;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;

/**
 * @author Joram Barrez
 */
public class TimerEventListenerTest extends FlowableCmmnTestCase {

    @Test
    @CmmnDeployment
    public void testTimerExpressionDuration() {
        CaseInstance caseInstance = cmmnRuntimeService.createCaseInstanceBuilder().caseDefinitionKey("testTimerExpression").start();
        assertThat(caseInstance).isNotNull();
        assertThat(cmmnRuntimeService.createCaseInstanceQuery().count()).isEqualTo(1);

        assertThat(cmmnRuntimeService.createPlanItemInstanceQuery().planItemDefinitionType(PlanItemDefinitionType.TIMER_EVENT_LISTENER)
                .planItemInstanceStateAvailable().singleResult()).isNotNull();
        assertThat(cmmnRuntimeService.createPlanItemInstanceQuery().planItemDefinitionType(PlanItemDefinitionType.HUMAN_TASK).planItemInstanceStateAvailable()
                .singleResult()).isNotNull();
        assertThat(cmmnRuntimeService.createPlanItemInstanceQuery().list())
                .extracting(PlanItemInstance::getPlanItemDefinitionType, PlanItemInstance::getPlanItemDefinitionId, PlanItemInstance::getState)
                .containsExactlyInAnyOrder(
                        tuple(PlanItemDefinitionType.TIMER_EVENT_LISTENER, "timerListener", PlanItemInstanceState.AVAILABLE),
                        tuple(PlanItemDefinitionType.HUMAN_TASK, "taskA", PlanItemInstanceState.AVAILABLE)
                );

        if (CmmnHistoryTestHelper.isHistoryLevelAtLeast(HistoryLevel.ACTIVITY, cmmnEngineConfiguration)) {
            assertThat(cmmnHistoryService.createHistoricPlanItemInstanceQuery().list())
                    .extracting(HistoricPlanItemInstance::getPlanItemDefinitionType, HistoricPlanItemInstance::getPlanItemDefinitionId, HistoricPlanItemInstance::getState)
                    .containsExactlyInAnyOrder(
                            tuple(PlanItemDefinitionType.TIMER_EVENT_LISTENER, "timerListener", PlanItemInstanceState.AVAILABLE),
                            tuple(PlanItemDefinitionType.HUMAN_TASK, "taskA", PlanItemInstanceState.AVAILABLE)
                    );
        }

        assertThat(cmmnManagementService.createTimerJobQuery().count()).isEqualTo(1);
        assertThat(cmmnManagementService.createTimerJobQuery().scopeId(caseInstance.getId()).scopeType(ScopeTypes.CMMN).count()).isEqualTo(1);

        // User task should not be active before the timer triggers
        assertThat(cmmnTaskService.createTaskQuery().count()).isZero();

        Job timerJob = cmmnManagementService.createTimerJobQuery().scopeDefinitionId(caseInstance.getCaseDefinitionId()).singleResult();
        assertThat(timerJob).isNotNull();
        assertThat(timerJob.getElementId()).isEqualTo("timerListener");
        assertThat(timerJob.getElementName()).isEqualTo("Timer listener");

        cmmnManagementService.moveTimerToExecutableJob(timerJob.getId());
        cmmnManagementService.executeJob(timerJob.getId());

        assertThat(cmmnRuntimeService.createPlanItemInstanceQuery().list())
                .extracting(PlanItemInstance::getPlanItemDefinitionType, PlanItemInstance::getPlanItemDefinitionId, PlanItemInstance::getState)
                .containsExactlyInAnyOrder(
                        tuple(PlanItemDefinitionType.HUMAN_TASK, "taskA", PlanItemInstanceState.ACTIVE)
                );

        if (CmmnHistoryTestHelper.isHistoryLevelAtLeast(HistoryLevel.ACTIVITY, cmmnEngineConfiguration)) {
            assertThat(cmmnHistoryService.createHistoricPlanItemInstanceQuery().list())
                    .extracting(HistoricPlanItemInstance::getPlanItemDefinitionType, HistoricPlanItemInstance::getPlanItemDefinitionId, HistoricPlanItemInstance::getState)
                    .containsExactlyInAnyOrder(
                            tuple(PlanItemDefinitionType.TIMER_EVENT_LISTENER, "timerListener", PlanItemInstanceState.COMPLETED),
                            tuple(PlanItemDefinitionType.HUMAN_TASK, "taskA", PlanItemInstanceState.ACTIVE)
                    );
        }

        // User task should be active after the timer has triggered
        assertThat(cmmnTaskService.createTaskQuery().count()).isEqualTo(1);
    }
    
    @Test
    @CmmnDeployment
    public void testTimerExpressionDurationWithCategory() {
        CaseInstance caseInstance = cmmnRuntimeService.createCaseInstanceBuilder().caseDefinitionKey("testTimerExpression").start();
        assertThat(caseInstance).isNotNull();
        assertThat(cmmnRuntimeService.createCaseInstanceQuery().count()).isEqualTo(1);

        assertThat(cmmnManagementService.createTimerJobQuery().count()).isEqualTo(1);
        assertThat(cmmnManagementService.createTimerJobQuery().scopeId(caseInstance.getId()).scopeType(ScopeTypes.CMMN).count()).isEqualTo(1);

        Job timerJob = cmmnManagementService.createTimerJobQuery().scopeDefinitionId(caseInstance.getCaseDefinitionId()).singleResult();
        assertThat(timerJob).isNotNull();
        assertThat(timerJob.getCategory()).isEqualTo("myCategory");

        cmmnManagementService.moveTimerToExecutableJob(timerJob.getId());
        cmmnManagementService.executeJob(timerJob.getId());

        // User task should be active after the timer has triggered
        assertThat(cmmnTaskService.createTaskQuery().count()).isEqualTo(1);
    }
    
    @Test
    @CmmnDeployment
    public void testTimerExpressionDurationWithCategoryExpression() {
        CaseInstance caseInstance = cmmnRuntimeService.createCaseInstanceBuilder()
                .caseDefinitionKey("testTimerExpression")
                .variable("categoryValue", "testValue")
                .start();
        assertThat(caseInstance).isNotNull();
        assertThat(cmmnRuntimeService.createCaseInstanceQuery().count()).isEqualTo(1);

        assertThat(cmmnManagementService.createTimerJobQuery().count()).isEqualTo(1);
        assertThat(cmmnManagementService.createTimerJobQuery().scopeId(caseInstance.getId()).scopeType(ScopeTypes.CMMN).count()).isEqualTo(1);

        Job timerJob = cmmnManagementService.createTimerJobQuery().scopeDefinitionId(caseInstance.getCaseDefinitionId()).singleResult();
        assertThat(timerJob).isNotNull();
        assertThat(timerJob.getCategory()).isEqualTo("testValue");

        cmmnManagementService.moveTimerToExecutableJob(timerJob.getId());
        cmmnManagementService.executeJob(timerJob.getId());

        // User task should be active after the timer has triggered
        assertThat(cmmnTaskService.createTaskQuery().count()).isEqualTo(1);
    }

    /**
     * Similar test as #testTimerExpressionDuration but with the real async executor,
     * instead of manually triggering the timer.
     */
    @Test
    @CmmnDeployment
    public void testTimerExpressionDurationWithRealAsyncExecutor() {
        Date startTime = setClockFixedToCurrentTime();
        CaseInstance caseInstance = cmmnRuntimeService.createCaseInstanceBuilder().caseDefinitionKey("testTimerExpression").start();

        PlanItemInstance planItemInstance = cmmnRuntimeService.createPlanItemInstanceQuery()
                .caseInstanceId(caseInstance.getId())
                .planItemInstanceState(PlanItemInstanceState.AVAILABLE)
                .planItemDefinitionType(PlanItemDefinitionType.TIMER_EVENT_LISTENER)
                .singleResult();
        assertThat(planItemInstance).isNotNull();

        // User task should not be active before the timer triggers
        assertThat(cmmnTaskService.createTaskQuery().count()).isZero();

        // Timer fires after 1 hour, so setting it to 1 hours + 1 second
        setClockTo(new Date(startTime.getTime() + (60 * 60 * 1000 + 1)));

        CmmnJobTestHelper.waitForJobExecutorToProcessAllJobs(cmmnEngineConfiguration, 7000L, 200L, true);

        // User task should be active after the timer has triggered 
        assertThat(cmmnTaskService.createTaskQuery().count()).isEqualTo(1);
    }

    @Test
    @CmmnDeployment
    public void testStageAfterTimer() {
        Date startTime = setClockFixedToCurrentTime();
        CaseInstance caseInstance = cmmnRuntimeService.createCaseInstanceBuilder().caseDefinitionKey("testStageAfterTimerEventListener").start();

        PlanItemInstance planItemInstance = cmmnRuntimeService.createPlanItemInstanceQuery()
                .caseInstanceId(caseInstance.getId())
                .planItemInstanceState(PlanItemInstanceState.AVAILABLE)
                .planItemDefinitionType(PlanItemDefinitionType.TIMER_EVENT_LISTENER)
                .singleResult();
        assertThat(planItemInstance).isNotNull();

        assertThat(cmmnTaskService.createTaskQuery().count()).isZero();

        // Timer fires after 1 day, so setting it to 1 day + 1 second
        setClockTo(new Date(startTime.getTime() + (24 * 60 * 60 * 1000 + 1)));

        CmmnJobTestHelper.waitForJobExecutorToProcessAllJobs(cmmnEngineConfiguration, 7000L, 200L, true);

        // User task should be active after the timer has triggered 
        assertThat(cmmnTaskService.createTaskQuery().count()).isEqualTo(2);
    }

    @Test
    @CmmnDeployment
    public void testTimerInStage() {
        Date startTime = setClockFixedToCurrentTime();
        CaseInstance caseInstance = cmmnRuntimeService.createCaseInstanceBuilder().caseDefinitionKey("testTimerInStage").start();

        assertThat(cmmnRuntimeService.createPlanItemInstanceQuery().caseInstanceId(caseInstance.getId()).planItemInstanceState(PlanItemInstanceState.ACTIVE)
                .count()).isEqualTo(1);
        PlanItemInstance planItemInstance = cmmnRuntimeService.createPlanItemInstanceQuery()
                .caseInstanceId(caseInstance.getId())
                .planItemInstanceState(PlanItemInstanceState.AVAILABLE)
                .planItemDefinitionType(PlanItemDefinitionType.TIMER_EVENT_LISTENER)
                .singleResult();
        assertThat(planItemInstance).isNotNull();

        assertThat(cmmnTaskService.createTaskQuery().count()).isEqualTo(1);

        // Timer fires after 3 hours, so setting it to 3 hours + 1 second
        setClockTo(new Date(startTime.getTime() + (3 * 60 * 60 * 1000 + 1)));

        CmmnJobTestHelper.waitForJobExecutorToProcessAllJobs(cmmnEngineConfiguration, 7000L, 200L, true);

        // User task should be active after the timer has triggered
        List<Task> tasks = cmmnTaskService.createTaskQuery().caseInstanceId(caseInstance.getId()).orderByTaskName().asc().list();
        assertThat(tasks)
                .extracting(Task::getName)
                .containsExactly("A", "B");
    }

    @Test
    @CmmnDeployment
    public void testExitPlanModelOnTimerOccurrence() {
        Date startTime = setClockFixedToCurrentTime();
        CaseInstance caseInstance = cmmnRuntimeService.createCaseInstanceBuilder().caseDefinitionKey("testStageExitOnTimerOccurrence").start();

        assertThat(cmmnRuntimeService.createPlanItemInstanceQuery().caseInstanceId(caseInstance.getId())
                .planItemInstanceState(PlanItemInstanceState.ACTIVE).planItemDefinitionType(PlanItemDefinitionType.HUMAN_TASK).count()).isEqualTo(3);
        assertThat(cmmnRuntimeService.createPlanItemInstanceQuery().caseInstanceId(caseInstance.getId())
                .planItemInstanceState(PlanItemInstanceState.AVAILABLE).planItemDefinitionType(PlanItemDefinitionType.TIMER_EVENT_LISTENER).count())
                .isEqualTo(1);
        assertThat(cmmnRuntimeService.createPlanItemInstanceQuery().caseInstanceId(caseInstance.getId())
                .planItemInstanceState(PlanItemInstanceState.ACTIVE).planItemDefinitionType(PlanItemDefinitionType.STAGE).count()).isEqualTo(1);
        assertThat(cmmnRuntimeService.createPlanItemInstanceQuery().caseInstanceId(caseInstance.getId())
                .planItemInstanceState(PlanItemInstanceState.ACTIVE).count()).isEqualTo(4);

        // Timer fires after 24 hours, so setting it to 24 hours + 1 second
        setClockTo(new Date(startTime.getTime() + (24 * 60 * 60 * 1000 + 1)));

        CmmnJobTestHelper.waitForJobExecutorToProcessAllJobs(cmmnEngineConfiguration, 7000L, 200L, true);

        assertThat(cmmnRuntimeService.createPlanItemInstanceQuery().caseInstanceId(caseInstance.getId()).count()).isZero();
        assertThat(cmmnRuntimeService.createCaseInstanceQuery().count()).isZero();
    }

    @Test
    @CmmnDeployment
    public void testDateExpression() {

        // Timer will fire on 2017-12-05T10:00
        // So moving the clock to the day before
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, 2017);
        calendar.set(Calendar.MONTH, 12);
        calendar.set(Calendar.DAY_OF_MONTH, 4);
        calendar.set(Calendar.HOUR, 11);
        calendar.set(Calendar.MINUTE, 0);
        Date dayBefore = calendar.getTime();
        setClockTo(dayBefore);

        CaseInstance caseInstance = cmmnRuntimeService.createCaseInstanceBuilder().caseDefinitionKey("testDateExpression").start();

        assertThat(cmmnRuntimeService.createPlanItemInstanceQuery().caseInstanceId(caseInstance.getId())
                .planItemInstanceState(PlanItemInstanceState.AVAILABLE).planItemDefinitionType(TimerEventListener.class.getSimpleName().toLowerCase()).count())
                .isEqualTo(1);
        assertThat(cmmnRuntimeService.createPlanItemInstanceQuery().caseInstanceId(caseInstance.getId())
                .planItemInstanceState(PlanItemInstanceState.AVAILABLE).planItemDefinitionType(Stage.class.getSimpleName().toLowerCase()).count())
                .isEqualTo(2);
        assertThat(cmmnRuntimeService.createPlanItemInstanceQuery().caseInstanceId(caseInstance.getId())
                .planItemInstanceState(PlanItemInstanceState.ACTIVE).planItemDefinitionType(Stage.class.getSimpleName().toLowerCase()).count()).isZero();
        assertThat(cmmnRuntimeService.createPlanItemInstanceQuery().caseInstanceId(caseInstance.getId())
                .planItemInstanceState(PlanItemInstanceState.ACTIVE).planItemDefinitionType(HumanTask.class.getSimpleName().toLowerCase()).count())
                .isZero();
        assertThat(cmmnRuntimeService.createPlanItemInstanceQuery().caseInstanceId(caseInstance.getId())
                .planItemInstanceState(PlanItemInstanceState.AVAILABLE).planItemDefinitionType(HumanTask.class.getSimpleName().toLowerCase()).count())
                .isEqualTo(1);

        assertThat(cmmnTaskService.createTaskQuery().caseInstanceId(caseInstance.getId()).count()).isZero();

        setClockTo(new Date(dayBefore.getTime() + (24 * 60 * 60 * 1000)));
        CmmnJobTestHelper.waitForJobExecutorToProcessAllJobs(cmmnEngineConfiguration, 7000L, 200L, true);

        assertThat(cmmnRuntimeService.createPlanItemInstanceQuery().caseInstanceId(caseInstance.getId())
                .planItemInstanceState(PlanItemInstanceState.ACTIVE).planItemDefinitionType(TimerEventListener.class.getSimpleName().toLowerCase()).count())
                .isZero();

        List<Task> tasks = cmmnTaskService.createTaskQuery().caseInstanceId(caseInstance.getId()).list();
        assertThat(tasks).hasSize(3);
        for (Task task : tasks) {
            cmmnTaskService.complete(task.getId());
        }

        assertCaseInstanceEnded(caseInstance);
    }

    @Test
    @CmmnDeployment
    public void testTimerWithBeanExpression() {
        Date startTime = setClockFixedToCurrentTime();
        CaseInstance caseInstance = cmmnRuntimeService.createCaseInstanceBuilder()
                .caseDefinitionKey("testBean")
                .variable("startTime", startTime)
                .start();

        assertThat(cmmnTaskService.createTaskQuery().caseInstanceId(caseInstance.getId()).count()).isEqualTo(2);

        assertThat(cmmnRuntimeService.createPlanItemInstanceQuery().caseInstanceId(caseInstance.getId())
                .planItemInstanceState(PlanItemInstanceState.ACTIVE).planItemDefinitionType(Stage.class.getSimpleName().toLowerCase()).count()).isEqualTo(2);
        assertThat(cmmnRuntimeService.createPlanItemInstanceQuery().caseInstanceId(caseInstance.getId())
                .planItemInstanceState(PlanItemInstanceState.ACTIVE).planItemDefinitionType(HumanTask.class.getSimpleName().toLowerCase()).count())
                .isEqualTo(2);

        setClockTo(new Date(startTime.getTime() + (2 * 60 * 60 * 1000)));
        CmmnJobTestHelper.waitForJobExecutorToProcessAllJobs(cmmnEngineConfiguration, 7000L, 200L, true);

        Task task = cmmnTaskService.createTaskQuery().caseInstanceId(caseInstance.getId()).singleResult();
        assertThat(task.getName()).isEqualTo("A");
        cmmnTaskService.complete(task.getId());
        assertCaseInstanceEnded(caseInstance);
    }

    @Test
    @CmmnDeployment
    public void testTimerStartTrigger() {
        // Completing the stage will be the start trigger for the timer.
        // The timer event will exit the whole plan model

        Date startTime = setClockFixedToCurrentTime();
        CaseInstance caseInstance = cmmnRuntimeService.createCaseInstanceBuilder().caseDefinitionKey("testStartTrigger").start();

        assertThat(cmmnRuntimeService.createPlanItemInstanceQuery().caseInstanceId(caseInstance.getId())
                .planItemDefinitionType(TimerEventListener.class.getSimpleName().toLowerCase()).count()).isEqualTo(1);

        Task task = cmmnTaskService.createTaskQuery().caseInstanceId(caseInstance.getId()).singleResult();
        assertThat(task.getName()).isEqualTo("A");
        cmmnTaskService.complete(task.getId());
        task = cmmnTaskService.createTaskQuery().caseInstanceId(caseInstance.getId()).singleResult();
        assertThat(task.getName()).isEqualTo("B");
        cmmnTaskService.complete(task.getId());
        task = cmmnTaskService.createTaskQuery().caseInstanceId(caseInstance.getId()).singleResult();
        assertThat(task.getName()).isEqualTo("C");

        assertThat(cmmnRuntimeService.createPlanItemInstanceQuery().caseInstanceId(caseInstance.getId())
                .planItemInstanceState(PlanItemInstanceState.AVAILABLE).planItemDefinitionType(TimerEventListener.class.getSimpleName().toLowerCase()).count())
                .isEqualTo(1);

        setClockTo(new Date(startTime.getTime() + (3 * 60 * 60 * 1000)));
        CmmnJobTestHelper.waitForJobExecutorToProcessAllJobs(cmmnEngineConfiguration, 7000L, 200L, true);
        assertCaseInstanceEnded(caseInstance);
    }

    @Test
    @CmmnDeployment
    public void testExitNestedStageThroughTimer() {
        Date startTime = setClockFixedToCurrentTime();
        CaseInstance caseInstance = cmmnRuntimeService.createCaseInstanceBuilder().caseDefinitionKey("testExitNestedStageThroughTimer").start();
        assertThat(cmmnRuntimeService.createPlanItemInstanceQuery().caseInstanceId(caseInstance.getId()).planItemDefinitionType(PlanItemDefinitionType.STAGE)
                .count()).isEqualTo(3);
        Task task = cmmnTaskService.createTaskQuery().caseInstanceId(caseInstance.getId()).singleResult();
        assertThat(task.getName()).isEqualTo("The task");

        setClockTo(new Date(startTime.getTime() + (5 * 60 * 60 * 1000)));
        CmmnJobTestHelper.waitForJobExecutorToProcessAllJobs(cmmnEngineConfiguration, 10000L, 100L, true);
        assertThat(cmmnTaskService.createTaskQuery().caseInstanceId(caseInstance.getId()).count()).isZero();
        assertCaseInstanceEnded(caseInstance);
    }

    @Test
    @CmmnDeployment
    public void timerActivatesAndExitStages() {
        Date startTime = setClockFixedToCurrentTime();
        CaseInstance caseInstance = cmmnRuntimeService.createCaseInstanceBuilder().caseDefinitionKey("timerActivatesAndExitStages").start();

        List<Task> tasks = cmmnTaskService.createTaskQuery()
                .caseInstanceId(caseInstance.getId())
                .orderByTaskName().asc()
                .list();
        assertThat(tasks)
                .extracting(Task::getName)
                .containsExactly("A", "B");

        assertThat(cmmnManagementService.createTimerJobQuery().count()).isZero();

        // Completing A activates the stage and the timer event listener
        cmmnTaskService.complete(tasks.get(0).getId());
        cmmnTaskService.complete(tasks.get(1).getId());

        // Timer event listener created a timer job
        assertThat(cmmnManagementService.createTimerJobQuery().count()).isEqualTo(1);
        tasks = cmmnTaskService.createTaskQuery()
                .caseInstanceId(caseInstance.getId())
                .orderByTaskName().asc()
                .list();
        assertThat(tasks)
                .extracting(Task::getName)
                .containsExactly("Stage 1 task", "Stage 3 task 1", "Stage 3 task 2");

        // Timer is set to 10 hours
        setClockTo(new Date(startTime.getTime() + (11 * 60 * 60 * 1000)));
        CmmnJobTestHelper.waitForJobExecutorToProcessAllJobs(cmmnEngineConfiguration, 10000L, 100L, true);

        tasks = cmmnTaskService.createTaskQuery()
                .caseInstanceId(caseInstance.getId())
                .orderByTaskName().asc()
                .list();
        assertThat(tasks)
                .extracting(Task::getName)
                .containsExactly("Stage 1 task", "Stage 2 task");

        for (Task task : tasks) {
            cmmnTaskService.complete(task.getId());
        }
        assertCaseInstanceEnded(caseInstance);
    }

    @Test
    @CmmnDeployment
    public void timerExitStageOnCompletedTasks() {
        CaseInstance caseInstance = cmmnRuntimeService.createCaseInstanceBuilder().caseDefinitionKey("timerExitStageOnCompletedTasks").start();

        List<Task> tasks = cmmnTaskService.createTaskQuery()
                .caseInstanceId(caseInstance.getId())
                .orderByTaskName().asc()
                .list();
        assertThat(tasks).hasSize(1);

        // Should have a TimerJob
        assertThat(cmmnManagementService.createTimerJobQuery().count()).isEqualTo(1);

        for (Task task : tasks) {
            cmmnTaskService.complete(task.getId());
        }

        // TimerJob should be deleted after all tasks have completed in the stage
        assertThat(cmmnManagementService.createTimerJobQuery().count()).isZero();
    }

    @Test
    @CmmnDeployment
    public void testTimerWithAvailableCondition() {
        CaseInstance caseInstance = cmmnRuntimeService.createCaseInstanceBuilder().caseDefinitionKey("testTimerExpression").start();

        assertThat(cmmnManagementService.createTimerJobQuery().count()).isZero();

        // Setting the variable should make the timer available and create the timer job
        cmmnRuntimeService.setVariable(caseInstance.getId(), "timerVar", true);
        assertThat(cmmnManagementService.createTimerJobQuery().count()).isEqualTo(1);

        PlanItemInstance timerPlanItemInstance = cmmnRuntimeService.createPlanItemInstanceQuery()
                .planItemDefinitionType(PlanItemDefinitionType.TIMER_EVENT_LISTENER).singleResult();
        assertThat(timerPlanItemInstance.getState()).isEqualTo(PlanItemInstanceState.AVAILABLE);

        // Setting the variable to false again will dismiss the timer
        cmmnRuntimeService.setVariable(caseInstance.getId(), "timerVar", false);
        assertThat(cmmnManagementService.createTimerJobQuery().count()).isZero();
        timerPlanItemInstance = cmmnRuntimeService.createPlanItemInstanceQuery().planItemDefinitionType(PlanItemDefinitionType.TIMER_EVENT_LISTENER)
                .singleResult();
        assertThat(timerPlanItemInstance.getState()).isEqualTo(PlanItemInstanceState.UNAVAILABLE);

        cmmnRuntimeService.setVariable(caseInstance.getId(), "timerVar", true);
        assertThat(cmmnManagementService.createTimerJobQuery().count()).isEqualTo(1);
        timerPlanItemInstance = cmmnRuntimeService.createPlanItemInstanceQuery().planItemDefinitionType(PlanItemDefinitionType.TIMER_EVENT_LISTENER)
                .singleResult();
        assertThat(timerPlanItemInstance.getState()).isEqualTo(PlanItemInstanceState.AVAILABLE);

        // Execute the job
        Job timerJob = cmmnManagementService.createTimerJobQuery().scopeDefinitionId(caseInstance.getCaseDefinitionId()).singleResult();
        assertThat(timerJob).isNotNull();
        cmmnManagementService.moveTimerToExecutableJob(timerJob.getId());
        cmmnManagementService.executeJob(timerJob.getId());

        timerPlanItemInstance = cmmnRuntimeService.createPlanItemInstanceQuery().planItemDefinitionType(PlanItemDefinitionType.TIMER_EVENT_LISTENER)
                .includeEnded().singleResult();
        assertThat(timerPlanItemInstance.getState()).isEqualTo(PlanItemInstanceState.COMPLETED);
    }

    @Test
    @CmmnDeployment(resources = "org/flowable/cmmn/test/eventlistener/TimerEventListenerTest.testTimerWithVariableExpression.cmmn")
    public void testTimerWithInstantVariableExpression() {
        setClockTo(Date.from(Instant.parse("2020-10-21T08:31:45.585Z")));
        CaseInstance caseInstance = cmmnRuntimeService.createCaseInstanceBuilder()
                .caseDefinitionKey("testVariable")
                .variable("startTime", Instant.parse("2020-10-21T08:31:46.585Z"))
                .start();

        assertThat(cmmnTaskService.createTaskQuery().caseInstanceId(caseInstance.getId()).count()).isEqualTo(2);

        assertThat(cmmnRuntimeService.createPlanItemInstanceQuery().caseInstanceId(caseInstance.getId())
                .planItemInstanceState(PlanItemInstanceState.ACTIVE).planItemDefinitionType(PlanItemDefinitionType.STAGE).count()).isEqualTo(2);
        assertThat(cmmnRuntimeService.createPlanItemInstanceQuery().caseInstanceId(caseInstance.getId())
                .planItemInstanceState(PlanItemInstanceState.ACTIVE).planItemDefinitionType(PlanItemDefinitionType.HUMAN_TASK).count())
                .isEqualTo(2);

        setClockTo(Date.from(Instant.parse("2020-10-21T08:31:47.585Z")));
        waitForJobExecutorToProcessAllJobs();

        Task task = cmmnTaskService.createTaskQuery().caseInstanceId(caseInstance.getId()).singleResult();
        assertThat(task.getName()).isEqualTo("A");
        cmmnTaskService.complete(task.getId());
        assertCaseInstanceEnded(caseInstance);
    }

    @Test
    @CmmnDeployment
    public void testRepeatingTimer() {

        Date startTime = new Date();
        cmmnEngineConfiguration.getClock().setCurrentTime(startTime);

        CaseInstance caseInstance = cmmnRuntimeService.createCaseInstanceBuilder().caseDefinitionKey("repeatingTimer").start();
        assertThat(cmmnManagementService.createTimerJobQuery().caseInstanceId(caseInstance.getId()).count()).isEqualTo(1);

        List<PlanItemInstance> planItemInstances = cmmnRuntimeService.createPlanItemInstanceQuery().includeEnded().orderByName().asc().list();
        assertThat(planItemInstances)
                .extracting(PlanItemInstance::getName, PlanItemInstance::getState)
                        .containsExactly(
                                tuple("A", PlanItemInstanceState.AVAILABLE),
                                tuple("Human task", PlanItemInstanceState.ACTIVE),
                                tuple("Timer", PlanItemInstanceState.AVAILABLE)
                                );

        // Repeat is set to 20S
        Date newTime = new Date(startTime.getTime() + 30_000);
        cmmnEngineConfiguration.getClock().setCurrentTime(newTime);

        Job job = cmmnManagementService.moveTimerToExecutableJob(cmmnManagementService.createTimerJobQuery().singleResult().getId());
        cmmnManagementService.executeJob(job.getId());

        assertThat(cmmnManagementService.createTimerJobQuery().caseInstanceId(caseInstance.getId()).count()).isEqualTo(1);

        planItemInstances = cmmnRuntimeService.createPlanItemInstanceQuery().includeEnded().orderByName().asc().list();
        assertThat(planItemInstances)
                .extracting(PlanItemInstance::getName, PlanItemInstance::getState)
                .containsExactlyInAnyOrder(
                        tuple("A", PlanItemInstanceState.ACTIVE),
                        tuple("A", PlanItemInstanceState.WAITING_FOR_REPETITION),
                        tuple("Human task", PlanItemInstanceState.ACTIVE),
                        tuple("Timer", PlanItemInstanceState.AVAILABLE),
                        tuple("Timer", PlanItemInstanceState.COMPLETED)
                );

        job = cmmnManagementService.moveTimerToExecutableJob(cmmnManagementService.createTimerJobQuery().singleResult().getId());
        cmmnManagementService.executeJob(job.getId());

        assertThat(cmmnManagementService.createTimerJobQuery().caseInstanceId(caseInstance.getId()).count()).isEqualTo(1);

        planItemInstances = cmmnRuntimeService.createPlanItemInstanceQuery().includeEnded().orderByName().asc().list();
        assertThat(planItemInstances)
                .extracting(PlanItemInstance::getName, PlanItemInstance::getState)
                .containsExactlyInAnyOrder(
                        tuple("A", PlanItemInstanceState.ACTIVE),
                        tuple("A", PlanItemInstanceState.ACTIVE),
                        tuple("A", PlanItemInstanceState.WAITING_FOR_REPETITION),
                        tuple("Human task", PlanItemInstanceState.ACTIVE),
                        tuple("Timer", PlanItemInstanceState.AVAILABLE),
                        tuple("Timer", PlanItemInstanceState.COMPLETED),
                        tuple("Timer", PlanItemInstanceState.COMPLETED)
                );
    }

    @Test
    @CmmnDeployment
    public void testRepeatingTimerWithAvailableCondition() {
        Date time = new Date();
        cmmnEngineConfiguration.getClock().setCurrentTime(time);

        CaseInstance caseInstance = cmmnRuntimeService.createCaseInstanceBuilder().caseDefinitionKey("repeatingTimer").start();
        assertThat(cmmnManagementService.createTimerJobQuery().caseInstanceId(caseInstance.getId()).count()).isEqualTo(1);

        List<PlanItemInstance> planItemInstances = cmmnRuntimeService.createPlanItemInstanceQuery().includeEnded().orderByName().asc().list();
        assertThat(planItemInstances)
                .extracting(PlanItemInstance::getName, PlanItemInstance::getState)
                .containsExactly(
                        tuple("A", PlanItemInstanceState.AVAILABLE),
                        tuple("Human task", PlanItemInstanceState.ACTIVE),
                        tuple("Timer", PlanItemInstanceState.AVAILABLE)
                );


        for (int i = 1; i < 9; i++) {

            // Repeat is set to 20S
            Date newTime = new Date(time.getTime() + 30_000);
            cmmnEngineConfiguration.getClock().setCurrentTime(newTime);
            time = newTime;

            Job job = cmmnManagementService.moveTimerToExecutableJob(cmmnManagementService.createTimerJobQuery().singleResult().getId());
            cmmnManagementService.executeJob(job.getId());

            assertThat(cmmnManagementService.createTimerJobQuery().caseInstanceId(caseInstance.getId()).count()).isEqualTo(1);

            Tuple[] expectedTuples = new Tuple[3 + (i * 2)];
            expectedTuples[0] = tuple("A", PlanItemInstanceState.WAITING_FOR_REPETITION);
            expectedTuples[1] = tuple("Human task", PlanItemInstanceState.ACTIVE);
            expectedTuples[2] = tuple("Timer", PlanItemInstanceState.AVAILABLE);
            int currentIndex = 2;
            for (int j = 0; j < i; j++) {
                expectedTuples[++currentIndex] = tuple("A", PlanItemInstanceState.ACTIVE);
                expectedTuples[++currentIndex] = tuple("Timer", PlanItemInstanceState.COMPLETED);
            }

            planItemInstances = cmmnRuntimeService.createPlanItemInstanceQuery().includeEnded().orderByName().asc().list();
            assertThat(planItemInstances)
                    .extracting(PlanItemInstance::getName, PlanItemInstance::getState)
                    .containsExactlyInAnyOrder(expectedTuples);
        }

    }

    @Test
    @CmmnDeployment
    public void testRepeatingTimerWithChangingAvailableCondition() {
        Date time = new Date();
        cmmnEngineConfiguration.getClock().setCurrentTime(time);

        CaseInstance caseInstance = cmmnRuntimeService.createCaseInstanceBuilder()
                .variable("timerActive", false)
                .caseDefinitionKey("repeatingTimer")
                .start();
        assertThat(cmmnManagementService.createTimerJobQuery().caseInstanceId(caseInstance.getId()).count()).isEqualTo(0);

        for (int i = 1; i < 12; i++) {

            cmmnRuntimeService.setVariable(caseInstance.getId(), "timerActive", true);
            assertThat(cmmnManagementService.createTimerJobQuery().caseInstanceId(caseInstance.getId()).count()).isEqualTo(1);

            // Repeat is set to 20S
            Date newTime = new Date(time.getTime() + 30_000);
            cmmnEngineConfiguration.getClock().setCurrentTime(newTime);
            time = newTime;

            Job job = cmmnManagementService.moveTimerToExecutableJob(cmmnManagementService.createTimerJobQuery().singleResult().getId());
            cmmnManagementService.executeJob(job.getId());

            assertThat(cmmnManagementService.createTimerJobQuery().caseInstanceId(caseInstance.getId()).count()).isEqualTo(1);

            Tuple[] expectedTuples = new Tuple[3 + (i * 2)];
            expectedTuples[0] = tuple("A", PlanItemInstanceState.WAITING_FOR_REPETITION);
            expectedTuples[1] = tuple("Human task", PlanItemInstanceState.ACTIVE);
            expectedTuples[2] = tuple("Timer", PlanItemInstanceState.AVAILABLE);
            int currentIndex = 2;
            for (int j = 0; j < i; j++) {
                expectedTuples[++currentIndex] = tuple("A", PlanItemInstanceState.ACTIVE);
                expectedTuples[++currentIndex] = tuple("Timer", PlanItemInstanceState.COMPLETED);
            }

            List<PlanItemInstance> planItemInstances = cmmnRuntimeService.createPlanItemInstanceQuery().includeEnded().orderByName().asc().list();
            assertThat(planItemInstances)
                    .extracting(PlanItemInstance::getName, PlanItemInstance::getState)
                    .containsExactlyInAnyOrder(expectedTuples);

            cmmnRuntimeService.setVariable(caseInstance.getId(), "timerActive", false);
            assertThat(cmmnManagementService.createTimerJobQuery().caseInstanceId(caseInstance.getId()).count()).isEqualTo(0);
        }
    }

    @Test
    @CmmnDeployment
    public void testLimitedRepeatingTimerWithAvailableCondition() {

        // The repeat is limited to 4 times here

        Date time = new Date();
        cmmnEngineConfiguration.getClock().setCurrentTime(time);
        CaseInstance caseInstance = cmmnRuntimeService.createCaseInstanceBuilder().caseDefinitionKey("repeatingTimer").start();

        int nrOfTimerJobsExecuted = 0;
        for (int i = 0; i < 10; i++) {

            // Repeat is set to 20S
            Date newTime = new Date(time.getTime() + 21_000);
            cmmnEngineConfiguration.getClock().setCurrentTime(newTime);
            time = newTime;

            if (cmmnManagementService.createTimerJobQuery().count() > 0) {
                Job job = cmmnManagementService.moveTimerToExecutableJob(cmmnManagementService.createTimerJobQuery().singleResult().getId());
                cmmnManagementService.executeJob(job.getId());

                nrOfTimerJobsExecuted++;
            }
        }

        assertThat(nrOfTimerJobsExecuted).isEqualTo(4);
        assertThat(cmmnManagementService.createTimerJobQuery().caseInstanceId(caseInstance.getId()).count()).isEqualTo(0);
    }

    @Test
    @CmmnDeployment
    public void testLimitedRepeatingTimerWithChangingAvailableCondition() {

        // The repeat is limited to 4 times here

        Date time = new Date();
        cmmnEngineConfiguration.getClock().setCurrentTime(time);
        CaseInstance caseInstance = cmmnRuntimeService.createCaseInstanceBuilder()
                .variable("timerActive", true)
                .caseDefinitionKey("repeatingTimer")
                .start();

        int nrOfTimerJobsExecuted = 0;
        for (int i = 0; i < 10; i++) {

            // Repeat is set to 20S
            Date newTime = new Date(time.getTime() + 21_000);
            cmmnEngineConfiguration.getClock().setCurrentTime(newTime);
            time = newTime;

            // When the availableCondition is true again, the repetition starts form the beginning
            if (i == 2) {
                cmmnRuntimeService.setVariable(caseInstance.getId(), "timerActive", false);
            } else if (i == 4) {
                cmmnRuntimeService.setVariable(caseInstance.getId(), "timerActive", true);
            }

            if (cmmnManagementService.createTimerJobQuery().count() > 0) {
                Job job = cmmnManagementService.moveTimerToExecutableJob(cmmnManagementService.createTimerJobQuery().singleResult().getId());
                cmmnManagementService.executeJob(job.getId());

                nrOfTimerJobsExecuted++;
            }

        }

        assertThat(nrOfTimerJobsExecuted).isEqualTo(6); // should 2 from first part and then 4, because it continued until the 4 times were used up

        cmmnRuntimeService.setVariable(caseInstance.getId(), "timerActive", true);
        assertThat(cmmnManagementService.createTimerJobQuery().caseInstanceId(caseInstance.getId()).count()).isEqualTo(0);
    }

    @Test
    @CmmnDeployment
    public void testDateTypes() throws ParseException {
        Date date = new SimpleDateFormat("dd-MM-yyyy hh:mm:ss XXX").parse("30-01-2024 10:28:00 +01:00");
        Instant instant = date.toInstant();
        LocalDate localDate = LocalDate.of(2024, 1, 30);
        LocalDateTime localDateTime = LocalDateTime.of(2024, 1, 30, 10, 22);

        CaseInstance caseInstance = cmmnRuntimeService.createCaseInstanceBuilder()
                .caseDefinitionKey("testTimerEventDateTypes")
                .variable("taskDate", date)
                .variable("taskInstant", instant)
                .variable("taskLocalDate", localDate)
                .variable("taskLocalDateTime", localDateTime)
                .variable("taskDateString", "2024-01-30T10:28:00+01:00")
                .start();

        // The case model has 5 timer event listeners, each with a user task afer it.
        // The fact that the user task exists, means that the date variable was able to be parsed.

        cmmnManagementService.createTimerJobQuery().caseInstanceId(caseInstance.getId()).list()
                .forEach(t -> cmmnManagementService.moveTimerToExecutableJob(t.getId()));
        cmmnManagementService.createJobQuery().list().forEach(j -> cmmnManagementService.executeJob(j.getId()));

        List<Task> tasks = cmmnTaskService.createTaskQuery().caseInstanceId(caseInstance.getId()).list();
        assertThat(tasks).extracting(Task::getName).containsExactlyInAnyOrder(
                "dateTask", "instantTask", "localDateTask", "localDateTimeTask", "dateStringTask"
        );
    }

    @Test
    @CmmnDeployment
    public void testTimerRescheduleWithDate() {
        CaseInstance caseInstance = cmmnRuntimeService.createCaseInstanceBuilder().caseDefinitionKey("testTimerExpression").start();
        assertThat(cmmnRuntimeService.createPlanItemInstanceQuery().planItemDefinitionType(PlanItemDefinitionType.TIMER_EVENT_LISTENER)
                .planItemInstanceStateAvailable().singleResult()).isNotNull();
        assertThat(cmmnManagementService.createTimerJobQuery().count()).isEqualTo(1);
        assertThat(cmmnManagementService.createTimerJobQuery().scopeId(caseInstance.getId()).scopeType(ScopeTypes.CMMN).count()).isEqualTo(1);

        Job timerJob = cmmnManagementService.createTimerJobQuery().scopeId(caseInstance.getId()).singleResult();
        
        Calendar newDueDateCal = new GregorianCalendar();
        newDueDateCal.add(Calendar.DATE, 20);
        Job newTimerJob = cmmnManagementService.rescheduleTimeDateJob(timerJob.getId(), newDueDateCal.getTime());
        
        Calendar compareCal = new GregorianCalendar();
        compareCal.add(Calendar.DATE, 19);
        
        assertThat(newTimerJob.getId()).isNotEqualTo(timerJob.getId());
        assertThat(newTimerJob.getDuedate()).isAfter(compareCal.getTime());
        
        Job dbTimerJob = cmmnManagementService.createTimerJobQuery().scopeId(caseInstance.getId()).singleResult();
        assertThat(dbTimerJob.getId()).isEqualTo(newTimerJob.getId());
        assertThat(dbTimerJob.getDuedate()).isAfter(compareCal.getTime());
        
        if (CmmnHistoryTestHelper.isHistoryLevelAtLeast(HistoryLevel.ACTIVITY, cmmnEngineConfiguration)) {
            assertThat(cmmnHistoryService.createHistoricPlanItemInstanceQuery().list())
                    .extracting(HistoricPlanItemInstance::getPlanItemDefinitionType, HistoricPlanItemInstance::getPlanItemDefinitionId, HistoricPlanItemInstance::getState)
                    .containsExactlyInAnyOrder(
                            tuple(PlanItemDefinitionType.TIMER_EVENT_LISTENER, "timerListener", PlanItemInstanceState.AVAILABLE),
                            tuple(PlanItemDefinitionType.HUMAN_TASK, "taskA", PlanItemInstanceState.AVAILABLE)
                    );
        }
    }
    
    @Test
    @CmmnDeployment(resources = "org/flowable/cmmn/test/eventlistener/TimerEventListenerTest.testTimerRescheduleWithDate.cmmn")
    public void testWrongTimerRescheduleWithDate() {
        CaseInstance caseInstance = cmmnRuntimeService.createCaseInstanceBuilder().caseDefinitionKey("testTimerExpression").start();
        assertThat(cmmnRuntimeService.createPlanItemInstanceQuery().planItemDefinitionType(PlanItemDefinitionType.TIMER_EVENT_LISTENER)
                .planItemInstanceStateAvailable().singleResult()).isNotNull();
        assertThat(cmmnManagementService.createTimerJobQuery().count()).isEqualTo(1);
        assertThat(cmmnManagementService.createTimerJobQuery().scopeId(caseInstance.getId()).scopeType(ScopeTypes.CMMN).count()).isEqualTo(1);

        Calendar newDueDateCal = new GregorianCalendar();
        newDueDateCal.add(Calendar.DATE, 20);
        assertThatThrownBy(() -> {
            cmmnManagementService.rescheduleTimeDateJob("wrongid", newDueDateCal.getTime());
        }).isInstanceOf(FlowableObjectNotFoundException.class)
            .hasMessageContaining("Timer job not found for id wrongid");
    }
    
    @Test
    @CmmnDeployment(resources = "org/flowable/cmmn/test/eventlistener/TimerEventListenerTest.testTimerRescheduleWithDate.cmmn")
    public void testTimerRescheduleWithDateValue() {
        CaseInstance caseInstance = cmmnRuntimeService.createCaseInstanceBuilder().caseDefinitionKey("testTimerExpression").start();
        assertThat(cmmnRuntimeService.createPlanItemInstanceQuery().planItemDefinitionType(PlanItemDefinitionType.TIMER_EVENT_LISTENER)
                .planItemInstanceStateAvailable().singleResult()).isNotNull();
        assertThat(cmmnManagementService.createTimerJobQuery().count()).isEqualTo(1);
        assertThat(cmmnManagementService.createTimerJobQuery().scopeId(caseInstance.getId()).scopeType(ScopeTypes.CMMN).count()).isEqualTo(1);

        Job timerJob = cmmnManagementService.createTimerJobQuery().scopeId(caseInstance.getId()).singleResult();
        
        Job newTimerJob = cmmnManagementService.rescheduleTimeDateValueJob(timerJob.getId(), "P20D");
        
        Calendar compareCal = new GregorianCalendar();
        compareCal.add(Calendar.DATE, 19);
        
        assertThat(newTimerJob.getId()).isNotEqualTo(timerJob.getId());
        assertThat(newTimerJob.getDuedate()).isAfter(compareCal.getTime());
        
        Job dbTimerJob = cmmnManagementService.createTimerJobQuery().scopeId(caseInstance.getId()).singleResult();
        assertThat(dbTimerJob.getId()).isEqualTo(newTimerJob.getId());
        assertThat(dbTimerJob.getDuedate()).isAfter(compareCal.getTime());
        
        if (CmmnHistoryTestHelper.isHistoryLevelAtLeast(HistoryLevel.ACTIVITY, cmmnEngineConfiguration)) {
            assertThat(cmmnHistoryService.createHistoricPlanItemInstanceQuery().list())
                    .extracting(HistoricPlanItemInstance::getPlanItemDefinitionType, HistoricPlanItemInstance::getPlanItemDefinitionId, HistoricPlanItemInstance::getState)
                    .containsExactlyInAnyOrder(
                            tuple(PlanItemDefinitionType.TIMER_EVENT_LISTENER, "timerListener", PlanItemInstanceState.AVAILABLE),
                            tuple(PlanItemDefinitionType.HUMAN_TASK, "taskA", PlanItemInstanceState.AVAILABLE)
                    );
        }
    }
    
    @Test
    @CmmnDeployment(resources = "org/flowable/cmmn/test/eventlistener/TimerEventListenerTest.testTimerRescheduleWithDate.cmmn")
    public void testTimerEventListenerInstanceRescheduleWithDate() {
        CaseInstance caseInstance = cmmnRuntimeService.createCaseInstanceBuilder().caseDefinitionKey("testTimerExpression").start();
        assertThat(cmmnRuntimeService.createPlanItemInstanceQuery().planItemDefinitionType(PlanItemDefinitionType.TIMER_EVENT_LISTENER)
                .planItemInstanceStateAvailable().singleResult()).isNotNull();
        assertThat(cmmnManagementService.createTimerJobQuery().count()).isEqualTo(1);
        assertThat(cmmnManagementService.createTimerJobQuery().scopeId(caseInstance.getId()).scopeType(ScopeTypes.CMMN).count()).isEqualTo(1);

        Job timerJob = cmmnManagementService.createTimerJobQuery().scopeId(caseInstance.getId()).singleResult();
        
        Calendar newDueDateCal = new GregorianCalendar();
        newDueDateCal.add(Calendar.DATE, 20);
        PlanItemInstance planItemInstance = cmmnRuntimeService.createPlanItemInstanceQuery().planItemDefinitionType(PlanItemDefinitionType.TIMER_EVENT_LISTENER).caseInstanceId(caseInstance.getId()).singleResult();
        Job newTimerJob = cmmnManagementService.rescheduleTimerEventListenerInstanceWithDate(planItemInstance.getId(), newDueDateCal.getTime());
        
        Calendar compareCal = new GregorianCalendar();
        compareCal.add(Calendar.DATE, 19);
        
        assertThat(newTimerJob.getId()).isNotEqualTo(timerJob.getId());
        assertThat(newTimerJob.getDuedate()).isAfter(compareCal.getTime());
        
        Job dbTimerJob = cmmnManagementService.createTimerJobQuery().scopeId(caseInstance.getId()).singleResult();
        assertThat(dbTimerJob.getId()).isEqualTo(newTimerJob.getId());
        assertThat(dbTimerJob.getDuedate()).isAfter(compareCal.getTime());
        
        if (CmmnHistoryTestHelper.isHistoryLevelAtLeast(HistoryLevel.ACTIVITY, cmmnEngineConfiguration)) {
            assertThat(cmmnHistoryService.createHistoricPlanItemInstanceQuery().list())
                    .extracting(HistoricPlanItemInstance::getPlanItemDefinitionType, HistoricPlanItemInstance::getPlanItemDefinitionId, HistoricPlanItemInstance::getState)
                    .containsExactlyInAnyOrder(
                            tuple(PlanItemDefinitionType.TIMER_EVENT_LISTENER, "timerListener", PlanItemInstanceState.AVAILABLE),
                            tuple(PlanItemDefinitionType.HUMAN_TASK, "taskA", PlanItemInstanceState.AVAILABLE)
                    );
        }
    }
    
    @Test
    @CmmnDeployment(resources = "org/flowable/cmmn/test/eventlistener/TimerEventListenerTest.testTimerRescheduleWithDate.cmmn")
    public void testTimerEventListenerInstanceRescheduleWithDateValue() {
        CaseInstance caseInstance = cmmnRuntimeService.createCaseInstanceBuilder().caseDefinitionKey("testTimerExpression").start();
        assertThat(cmmnRuntimeService.createPlanItemInstanceQuery().planItemDefinitionType(PlanItemDefinitionType.TIMER_EVENT_LISTENER)
                .planItemInstanceStateAvailable().singleResult()).isNotNull();
        assertThat(cmmnManagementService.createTimerJobQuery().count()).isEqualTo(1);
        assertThat(cmmnManagementService.createTimerJobQuery().scopeId(caseInstance.getId()).scopeType(ScopeTypes.CMMN).count()).isEqualTo(1);

        Job timerJob = cmmnManagementService.createTimerJobQuery().scopeId(caseInstance.getId()).singleResult();
        
        PlanItemInstance planItemInstance = cmmnRuntimeService.createPlanItemInstanceQuery().planItemDefinitionType(PlanItemDefinitionType.TIMER_EVENT_LISTENER).caseInstanceId(caseInstance.getId()).singleResult();
        Job newTimerJob = cmmnManagementService.rescheduleTimerEventListenerInstanceWithDateValue(planItemInstance.getId(), "P20D");
        
        Calendar compareCal = new GregorianCalendar();
        compareCal.add(Calendar.DATE, 19);
        
        assertThat(newTimerJob.getId()).isNotEqualTo(timerJob.getId());
        assertThat(newTimerJob.getDuedate()).isAfter(compareCal.getTime());
        
        Job dbTimerJob = cmmnManagementService.createTimerJobQuery().scopeId(caseInstance.getId()).singleResult();
        assertThat(dbTimerJob.getId()).isEqualTo(newTimerJob.getId());
        assertThat(dbTimerJob.getDuedate()).isAfter(compareCal.getTime());
        
        if (CmmnHistoryTestHelper.isHistoryLevelAtLeast(HistoryLevel.ACTIVITY, cmmnEngineConfiguration)) {
            assertThat(cmmnHistoryService.createHistoricPlanItemInstanceQuery().list())
                    .extracting(HistoricPlanItemInstance::getPlanItemDefinitionType, HistoricPlanItemInstance::getPlanItemDefinitionId, HistoricPlanItemInstance::getState)
                    .containsExactlyInAnyOrder(
                            tuple(PlanItemDefinitionType.TIMER_EVENT_LISTENER, "timerListener", PlanItemInstanceState.AVAILABLE),
                            tuple(PlanItemDefinitionType.HUMAN_TASK, "taskA", PlanItemInstanceState.AVAILABLE)
                    );
        }
    }
}