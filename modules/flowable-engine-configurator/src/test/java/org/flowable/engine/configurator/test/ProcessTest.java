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
package org.flowable.engine.configurator.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.flowable.app.api.AppRepositoryService;
import org.flowable.app.api.repository.AppDeployment;
import org.flowable.app.engine.AppEngine;
import org.flowable.app.engine.AppEngineConfiguration;
import org.flowable.app.engine.test.FlowableAppExtension;
import org.flowable.cmmn.api.CmmnRuntimeService;
import org.flowable.cmmn.api.CmmnTaskService;
import org.flowable.cmmn.api.history.HistoricCaseInstance;
import org.flowable.cmmn.api.runtime.CaseInstance;
import org.flowable.cmmn.api.runtime.CaseInstanceState;
import org.flowable.cmmn.api.runtime.PlanItemInstance;
import org.flowable.cmmn.api.runtime.PlanItemInstanceState;
import org.flowable.cmmn.engine.CmmnEngineConfiguration;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.common.engine.api.scope.ScopeTypes;
import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.common.engine.impl.interceptor.EngineConfigurationConstants;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.impl.test.HistoryTestHelper;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.form.api.FormEngineConfigurationApi;
import org.flowable.form.api.FormInfo;
import org.flowable.form.api.FormRepositoryService;
import org.flowable.form.api.FormService;
import org.flowable.identitylink.api.IdentityLinkType;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

/**
 * @author Tijs Rademakers
 */
@ExtendWith(FlowableAppExtension.class)
@MockitoSettings
public class ProcessTest {

    protected AppEngineConfiguration appEngineConfiguration;
    protected AppRepositoryService appRepositoryService;

    @Mock
    protected FormEngineConfigurationApi formEngineConfiguration;

    @Mock
    protected FormService formEngineFormService;

    @Mock
    protected FormRepositoryService formRepositoryService;

    @BeforeEach
    void setUp(AppEngine appEngine) {
        appEngineConfiguration = appEngine.getAppEngineConfiguration();
        appRepositoryService = appEngine.getAppRepositoryService();
        Map engineConfigurations = appEngineConfiguration.getEngineConfigurations();
        engineConfigurations.put(EngineConfigurationConstants.KEY_FORM_ENGINE_CONFIG, formEngineConfiguration);
    }

    @AfterEach
    public void resetMocks() {
        appEngineConfiguration.getEngineConfigurations().remove(EngineConfigurationConstants.KEY_FORM_ENGINE_CONFIG);
    }
    
    @Test
    public void testCompleteTask() throws Exception {
        ProcessEngineConfiguration processEngineConfiguration = (ProcessEngineConfiguration) appEngineConfiguration.getEngineConfigurations()
                        .get(EngineConfigurationConstants.KEY_PROCESS_ENGINE_CONFIG);
        RuntimeService runtimeService = processEngineConfiguration.getRuntimeService();
        TaskService taskService = processEngineConfiguration.getTaskService();
        
        AppDeployment deployment = appRepositoryService.createDeployment()
            .addClasspathResource("org/flowable/engine/configurator/test/oneTaskProcess.bpmn20.xml").deploy();
        
        try {
            ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTask");
            Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertThat(task).isNotNull();
            
            runtimeService.addUserIdentityLink(processInstance.getId(), "anotherUser", IdentityLinkType.STARTER);
            taskService.addUserIdentityLink(task.getId(), "testUser", IdentityLinkType.PARTICIPANT);
            
            assertThat(runtimeService.getIdentityLinksForProcessInstance(processInstance.getId())).hasSize(2);
            assertThat(taskService.getIdentityLinksForTask(task.getId())).hasSize(1);
            
            taskService.complete(task.getId());

            assertThatThrownBy(() -> runtimeService.getIdentityLinksForProcessInstance(processInstance.getId()))
                    .isInstanceOf(FlowableObjectNotFoundException.class);

            assertThatThrownBy(() -> taskService.getIdentityLinksForTask(task.getId()))
                    .isInstanceOf(FlowableObjectNotFoundException.class);

            assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(processInstance.getId()).count()).isZero();

        } finally {
            appRepositoryService.deleteDeployment(deployment.getId(), true);
            processEngineConfiguration.getRepositoryService()
                    .createDeploymentQuery()
                    .parentDeploymentId(deployment.getId())
                    .list()
                    .forEach(processDeployment -> processEngineConfiguration.getRepositoryService().deleteDeployment(processDeployment.getId(), true));
        }
    }
    
    @Test
    public void testCompleteTaskWithForm() throws Exception {
        ProcessEngineConfiguration processEngineConfiguration = (ProcessEngineConfiguration) appEngineConfiguration.getEngineConfigurations()
                        .get(EngineConfigurationConstants.KEY_PROCESS_ENGINE_CONFIG);
        RuntimeService runtimeService = processEngineConfiguration.getRuntimeService();
        TaskService taskService = processEngineConfiguration.getTaskService();

        AppDeployment deployment = appRepositoryService.createDeployment()
            .addClasspathResource("org/flowable/engine/configurator/test/oneTaskWithFormProcess.bpmn20.xml")
            .deploy();
        
        try {
            ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTask");
            Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertThat(task).isNotNull();
            
            runtimeService.addUserIdentityLink(processInstance.getId(), "anotherUser", IdentityLinkType.STARTER);
            taskService.addUserIdentityLink(task.getId(), "testUser", IdentityLinkType.PARTICIPANT);
            
            assertThat(runtimeService.getIdentityLinksForProcessInstance(processInstance.getId())).hasSize(2);
            assertThat(taskService.getIdentityLinksForTask(task.getId())).hasSize(1);
            
            Map<String, Object> variables = new HashMap<>();
            variables.put("input1", "test");

            when(formEngineConfiguration.getFormService()).thenReturn(formEngineFormService);
            when(formEngineConfiguration.getFormRepositoryService()).thenReturn(formRepositoryService);
            FormInfo formInfo = new FormInfo();
            when(formRepositoryService.getFormModelById("formDefId")).thenReturn(formInfo);
            when(formEngineFormService.getVariablesFromFormSubmission(task.getTaskDefinitionKey(), "userTask", processInstance.getId(),
                    processInstance.getProcessDefinitionId(), ScopeTypes.BPMN, formInfo, variables, null))
                    .thenReturn(Collections.emptyMap());

            taskService.completeTaskWithForm(task.getId(), "formDefId", null, variables);

            assertThatThrownBy(() -> runtimeService.getIdentityLinksForProcessInstance(processInstance.getId()))
                    .isInstanceOf(FlowableObjectNotFoundException.class);

            assertThatThrownBy(() -> taskService.getIdentityLinksForTask(task.getId()))
                    .isInstanceOf(FlowableObjectNotFoundException.class);

            assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(processInstance.getId()).count()).isZero();

        } finally {
            appRepositoryService.deleteDeployment(deployment.getId(), true);
            processEngineConfiguration.getRepositoryService()
                    .createDeploymentQuery()
                    .parentDeploymentId(deployment.getId())
                    .list()
                    .forEach(processDeployment -> processEngineConfiguration.getRepositoryService().deleteDeployment(processDeployment.getId(), true));
        }
    }

    @Test
    public void testCompleteTaskWithAnotherForm() {
        ProcessEngineConfiguration processEngineConfiguration = (ProcessEngineConfiguration) appEngineConfiguration.getEngineConfigurations()
                        .get(EngineConfigurationConstants.KEY_PROCESS_ENGINE_CONFIG);
        RuntimeService runtimeService = processEngineConfiguration.getRuntimeService();
        TaskService taskService = processEngineConfiguration.getTaskService();

        AppDeployment deployment = appRepositoryService.createDeployment()
            .addClasspathResource("org/flowable/engine/configurator/test/oneTaskWithFormProcess.bpmn20.xml")
            .deploy();

        try {
            ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTask");
            Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertThat(task).isNotNull();

            runtimeService.addUserIdentityLink(processInstance.getId(), "anotherUser", IdentityLinkType.STARTER);
            taskService.addUserIdentityLink(task.getId(), "testUser", IdentityLinkType.PARTICIPANT);

            assertThat(runtimeService.getIdentityLinksForProcessInstance(processInstance.getId())).hasSize(2);
            assertThat(taskService.getIdentityLinksForTask(task.getId())).hasSize(1);

            Map<String, Object> variables = new HashMap<>();
            variables.put("anotherInput", "test");
            when(formEngineConfiguration.getFormService()).thenReturn(formEngineFormService);
            when(formEngineConfiguration.getFormRepositoryService()).thenReturn(formRepositoryService);
            FormInfo formInfo = new FormInfo();
            when(formRepositoryService.getFormModelById("formDefId")).thenReturn(formInfo);
            when(formEngineFormService.getVariablesFromFormSubmission(task.getTaskDefinitionKey(), "userTask", processInstance.getId(),
                    processInstance.getProcessDefinitionId(), ScopeTypes.BPMN, formInfo, variables, null))
                    .thenReturn(Collections.emptyMap());

            taskService.completeTaskWithForm(task.getId(), "formDefId", null, variables);

            assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(processInstance.getId()).count()).isZero();


        } finally {
            appRepositoryService.deleteDeployment(deployment.getId(), true);
            processEngineConfiguration.getRepositoryService()
                    .createDeploymentQuery()
                    .parentDeploymentId(deployment.getId())
                    .list()
                    .forEach(processDeployment -> processEngineConfiguration.getRepositoryService().deleteDeployment(processDeployment.getId(), true));
        }
    }
    
    @Test
    public void testCompleteProcessUserTaskWithCmmnEngine() throws Exception {
        ProcessEngineConfiguration processEngineConfiguration = (ProcessEngineConfiguration) appEngineConfiguration.getEngineConfigurations()
                        .get(EngineConfigurationConstants.KEY_PROCESS_ENGINE_CONFIG);
        RuntimeService runtimeService = processEngineConfiguration.getRuntimeService();
        TaskService taskService = processEngineConfiguration.getTaskService();
        
        CmmnEngineConfiguration cmmnEngineConfiguration = (CmmnEngineConfiguration) appEngineConfiguration.getEngineConfigurations()
                .get(EngineConfigurationConstants.KEY_CMMN_ENGINE_CONFIG);
        CmmnTaskService cmmnTaskService = cmmnEngineConfiguration.getCmmnTaskService();
        
        AppDeployment deployment = appRepositoryService.createDeployment()
            .addClasspathResource("org/flowable/engine/configurator/test/oneTaskProcess.bpmn20.xml").deploy();
        
        try {
            ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTask");
            Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertThat(task).isNotNull();
            
            assertThatThrownBy(() -> cmmnTaskService.complete(task.getId())).isInstanceOf(FlowableException.class)
                .hasMessageContaining("created by the process engine");
            
            assertThatThrownBy(() -> cmmnTaskService.completeTaskWithForm(task.getId(), null, null, null)).isInstanceOf(FlowableException.class)
                .hasMessageContaining("created by the process engine");

            assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(processInstance.getId()).count()).isEqualTo(1);
            
            taskService.complete(task.getId());
            
            assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(processInstance.getId()).count()).isZero();

        } finally {
            appRepositoryService.deleteDeployment(deployment.getId(), true);
            processEngineConfiguration.getRepositoryService()
                    .createDeploymentQuery()
                    .parentDeploymentId(deployment.getId())
                    .list()
                    .forEach(processDeployment -> processEngineConfiguration.getRepositoryService().deleteDeployment(processDeployment.getId(), true));
        }
    }
    
    @Test
    public void testProcessWithCaseTaskAndVariableListener() throws Exception {
        ProcessEngineConfiguration processEngineConfiguration = (ProcessEngineConfiguration) appEngineConfiguration.getEngineConfigurations()
                        .get(EngineConfigurationConstants.KEY_PROCESS_ENGINE_CONFIG);
        RuntimeService runtimeService = processEngineConfiguration.getRuntimeService();
        TaskService taskService = processEngineConfiguration.getTaskService();
        
        CmmnEngineConfiguration cmmnEngineConfiguration = (CmmnEngineConfiguration) appEngineConfiguration.getEngineConfigurations()
                .get(EngineConfigurationConstants.KEY_CMMN_ENGINE_CONFIG);
        
        cmmnEngineConfiguration.getCmmnRepositoryService().createDeployment()
                .addClasspathResource("org/flowable/engine/configurator/test/caseWithVariableListener.cmmn")
                .deploy();
        
        processEngineConfiguration.getRepositoryService().createDeployment()
                .addClasspathResource("org/flowable/engine/configurator/test/caseTaskProcess.bpmn20.xml")
                .deploy();
        
        try {
            ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("caseTaskProcess");
            Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            taskService.complete(task.getId());
            
            List<PlanItemInstance> planItemInstances = cmmnEngineConfiguration.getCmmnRuntimeService()
                    .createPlanItemInstanceQuery()
                    .planItemInstanceState(PlanItemInstanceState.ACTIVE)
                    .list();
            assertThat(planItemInstances).hasSize(1);
            String caseInstanceId = planItemInstances.iterator().next().getCaseInstanceId();
            
            cmmnEngineConfiguration.getCmmnRuntimeService().setVariable(caseInstanceId, "var1", "test");
            
            planItemInstances = cmmnEngineConfiguration.getCmmnRuntimeService()
                    .createPlanItemInstanceQuery()
                    .caseInstanceId(caseInstanceId)
                    .planItemInstanceState(PlanItemInstanceState.ACTIVE)
                    .list();
            assertThat(planItemInstances).hasSize(2);
            
            for (PlanItemInstance planItemInstance : planItemInstances) {
                cmmnEngineConfiguration.getCmmnRuntimeService().triggerPlanItemInstance(planItemInstance.getId());
            }
            
            assertThat(cmmnEngineConfiguration.getCmmnRuntimeService().createCaseInstanceQuery().caseInstanceId(caseInstanceId).count()).isZero();
            
            task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertThat(task.getTaskDefinitionKey()).isEqualTo("secondTask");
            taskService.complete(task.getId());
            
            assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(processInstance.getId()).count()).isZero();

        } finally {
            processEngineConfiguration.getRepositoryService()
                    .createDeploymentQuery()
                    .list()
                    .forEach(processDeployment -> processEngineConfiguration.getRepositoryService().deleteDeployment(processDeployment.getId(), true));
            
            cmmnEngineConfiguration.getCmmnRepositoryService()
                    .createDeploymentQuery()
                    .list()
                    .forEach(caseDeployment -> cmmnEngineConfiguration.getCmmnRepositoryService().deleteDeployment(caseDeployment.getId(), true));
        }
    }
    
    @Test
    public void testProcessWithCaseTaskAndMultipleVariableListeners() throws Exception {
        ProcessEngineConfiguration processEngineConfiguration = (ProcessEngineConfiguration) appEngineConfiguration.getEngineConfigurations()
                        .get(EngineConfigurationConstants.KEY_PROCESS_ENGINE_CONFIG);
        RuntimeService runtimeService = processEngineConfiguration.getRuntimeService();
        TaskService taskService = processEngineConfiguration.getTaskService();
        
        CmmnEngineConfiguration cmmnEngineConfiguration = (CmmnEngineConfiguration) appEngineConfiguration.getEngineConfigurations()
                .get(EngineConfigurationConstants.KEY_CMMN_ENGINE_CONFIG);
        
        cmmnEngineConfiguration.getCmmnRepositoryService().createDeployment()
                .addClasspathResource("org/flowable/engine/configurator/test/caseWithVariableListener.cmmn")
                .deploy();
        
        processEngineConfiguration.getRepositoryService().createDeployment()
                .addClasspathResource("org/flowable/engine/configurator/test/processWithVariableListener.bpmn20.xml")
                .deploy();
        
        try {
            ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("variableListenerProcess");
            Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            assertThat(task.getTaskDefinitionKey()).isEqualTo("theTask");
            
            List<PlanItemInstance> planItemInstances = cmmnEngineConfiguration.getCmmnRuntimeService()
                    .createPlanItemInstanceQuery()
                    .planItemInstanceState(PlanItemInstanceState.ACTIVE)
                    .list();
            assertThat(planItemInstances).hasSize(1);
            String caseInstanceId = planItemInstances.iterator().next().getCaseInstanceId();
            
            cmmnEngineConfiguration.getCmmnRuntimeService().setVariable(caseInstanceId, "var1", "test");
            
            planItemInstances = cmmnEngineConfiguration.getCmmnRuntimeService()
                    .createPlanItemInstanceQuery()
                    .caseInstanceId(caseInstanceId)
                    .planItemInstanceState(PlanItemInstanceState.ACTIVE)
                    .list();
            assertThat(planItemInstances).hasSize(2);
            
            for (PlanItemInstance planItemInstance : planItemInstances) {
                cmmnEngineConfiguration.getCmmnRuntimeService().triggerPlanItemInstance(planItemInstance.getId());
            }
            
            assertThat(cmmnEngineConfiguration.getCmmnRuntimeService().createCaseInstanceQuery().caseInstanceId(caseInstanceId).count()).isZero();
            
            assertThat(taskService.createTaskQuery().processInstanceId(processInstance.getId()).count()).isEqualTo(1);
            
            runtimeService.setVariable(processInstance.getId(), "var1", "test");
            
            List<Execution> executions = runtimeService.createExecutionQuery().processInstanceId(processInstance.getId()).list();
            for (Execution execution : executions) {
                System.out.println("execution " + execution.getId() + " " + execution.getActivityId());
            }
            
            assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(processInstance.getId()).count()).isZero();

        } finally {
            processEngineConfiguration.getRepositoryService()
                    .createDeploymentQuery()
                    .list()
                    .forEach(processDeployment -> processEngineConfiguration.getRepositoryService().deleteDeployment(processDeployment.getId(), true));
            
            cmmnEngineConfiguration.getCmmnRepositoryService()
                    .createDeploymentQuery()
                    .list()
                    .forEach(caseDeployment -> cmmnEngineConfiguration.getCmmnRepositoryService().deleteDeployment(caseDeployment.getId(), true));
        }
    }
    
    @Test
    public void testProcessWithCaseTaskAndListener() throws Exception {
        ProcessEngineConfigurationImpl processEngineConfiguration = (ProcessEngineConfigurationImpl) appEngineConfiguration.getEngineConfigurations()
                        .get(EngineConfigurationConstants.KEY_PROCESS_ENGINE_CONFIG);
        RuntimeService runtimeService = processEngineConfiguration.getRuntimeService();
        TaskService taskService = processEngineConfiguration.getTaskService();
        HistoryService historyService = processEngineConfiguration.getHistoryService();
        
        CmmnEngineConfiguration cmmnEngineConfiguration = (CmmnEngineConfiguration) appEngineConfiguration.getEngineConfigurations()
                .get(EngineConfigurationConstants.KEY_CMMN_ENGINE_CONFIG);
        
        cmmnEngineConfiguration.getCmmnRepositoryService().createDeployment()
                .addClasspathResource("org/flowable/engine/configurator/test/caseWithListener.cmmn")
                .deploy();
        
        processEngineConfiguration.getRepositoryService().createDeployment()
                .addClasspathResource("org/flowable/engine/configurator/test/caseTaskProcessWithListener.bpmn20.xml")
                .deploy();
        
        try {
            ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("caseTaskProcess");
            Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            taskService.complete(task.getId());
            
            CaseInstance subCaseInstance = cmmnEngineConfiguration.getCmmnRuntimeService()
                    .createCaseInstanceQuery()
                    .caseDefinitionKey("caseWithListener")
                    .singleResult();
            assertThat(subCaseInstance).isNotNull();
            
            runtimeService.deleteProcessInstance(processInstance.getId(), "some reason");
            
            assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(processInstance.getId()).list().size()).isZero();
            assertThat(cmmnEngineConfiguration.getCmmnRuntimeService().createCaseInstanceQuery().caseInstanceId(subCaseInstance.getId()).list().size()).isZero();
            
            if (HistoryTestHelper.isHistoryLevelAtLeast(HistoryLevel.ACTIVITY, processEngineConfiguration)) {
                assertThat(cmmnEngineConfiguration.getCmmnHistoryService().createHistoricCaseInstanceQuery().caseInstanceId(subCaseInstance.getId()).count()).isEqualTo(1);
                HistoricCaseInstance historicCaseInstance = cmmnEngineConfiguration.getCmmnHistoryService().createHistoricCaseInstanceQuery()
                        .caseInstanceId(subCaseInstance.getId())
                        .singleResult();
                assertThat(historicCaseInstance.getEndTime()).isNotNull();
                assertThat(historicCaseInstance.getState()).isEqualTo(CaseInstanceState.TERMINATED);
                
                assertThat(historyService.createHistoricProcessInstanceQuery().processInstanceId(processInstance.getId()).count()).isEqualTo(1);
                HistoricProcessInstance historicProcessInstance = historyService.createHistoricProcessInstanceQuery()
                        .processInstanceId(processInstance.getId())
                        .singleResult();
                assertThat(historicProcessInstance.getEndTime()).isNotNull();
            }

        } finally {
            processEngineConfiguration.getRepositoryService()
                    .createDeploymentQuery()
                    .list()
                    .forEach(processDeployment -> processEngineConfiguration.getRepositoryService().deleteDeployment(processDeployment.getId(), true));
            
            cmmnEngineConfiguration.getCmmnRepositoryService()
                    .createDeploymentQuery()
                    .list()
                    .forEach(caseDeployment -> cmmnEngineConfiguration.getCmmnRepositoryService().deleteDeployment(caseDeployment.getId(), true));
        }
    }

    @Test
    public void deletingAppDeploymentShouldDeleteChildInstances() {
        AppDeployment appDeployment = appRepositoryService
                .createDeployment()
                .addClasspathResource("org/flowable/engine/configurator/test/test.app")
                .addClasspathResource("org/flowable/engine/configurator/test/caseWithListener.cmmn")
                .addClasspathResource("org/flowable/engine/configurator/test/caseTaskProcessWithListener.bpmn20.xml")
                .deploy();

        try {
            ProcessEngineConfigurationImpl processEngineConfiguration = (ProcessEngineConfigurationImpl) appEngineConfiguration.getEngineConfigurations()
                    .get(EngineConfigurationConstants.KEY_PROCESS_ENGINE_CONFIG);
            RuntimeService runtimeService = processEngineConfiguration.getRuntimeService();
            TaskService taskService = processEngineConfiguration.getTaskService();
            HistoryService historyService = processEngineConfiguration.getHistoryService();

            CmmnEngineConfiguration cmmnEngineConfiguration = (CmmnEngineConfiguration) appEngineConfiguration.getEngineConfigurations()
                    .get(EngineConfigurationConstants.KEY_CMMN_ENGINE_CONFIG);

            ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("caseTaskProcess");
            Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            taskService.complete(task.getId());

            CmmnRuntimeService cmmnRuntimeService = cmmnEngineConfiguration.getCmmnRuntimeService();
            CaseInstance subCaseInstance = cmmnRuntimeService
                    .createCaseInstanceQuery()
                    .caseDefinitionKey("caseWithListener")
                    .singleResult();
            assertThat(subCaseInstance).isNotNull();

            appRepositoryService.deleteDeployment(appDeployment.getId(), true);

            assertThat(runtimeService.createProcessInstanceQuery().list()).isEmpty();
            assertThat(cmmnRuntimeService.createCaseInstanceQuery().list()).isEmpty();

            if (HistoryTestHelper.isHistoryLevelAtLeast(HistoryLevel.ACTIVITY, processEngineConfiguration)) {
                assertThat(cmmnEngineConfiguration.getCmmnHistoryService().createHistoricCaseInstanceQuery().list()).isEmpty();
                assertThat(historyService.createHistoricProcessInstanceQuery().list()).isEmpty();
            }
        } finally {
            appDeployment = appRepositoryService.createDeploymentQuery().deploymentId(appDeployment.getId()).singleResult();
            if (appDeployment != null) {
                appRepositoryService.deleteDeployment(appDeployment.getId(), true);
            }

        }

    }
}
