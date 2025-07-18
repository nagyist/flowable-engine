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

package org.flowable.cmmn.engine.test.impl;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.flowable.cmmn.api.CmmnManagementService;
import org.flowable.cmmn.api.CmmnRepositoryService;
import org.flowable.cmmn.api.repository.CmmnDeploymentBuilder;
import org.flowable.cmmn.engine.CmmnEngine;
import org.flowable.cmmn.engine.CmmnEngineConfiguration;
import org.flowable.cmmn.engine.impl.deployer.CmmnDeployer;
import org.flowable.cmmn.engine.impl.history.CmmnHistoryManager;
import org.flowable.cmmn.engine.impl.history.DefaultCmmnHistoryManager;
import org.flowable.cmmn.engine.test.CmmnDeployment;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.common.engine.impl.util.ReflectUtil;
import org.flowable.job.api.HistoryJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tijs Rademakers
 */
public abstract class CmmnTestHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(CmmnTestHelper.class);

    static Map<String, CmmnEngine> cmmnEngines = new HashMap<>();
    
    // Test annotation support /////////////////////////////////////////////

    public static String annotationDeploymentSetUp(CmmnEngine cmmnEngine, Class<?> testClass, String methodName) {
        Method method = null;
        try {
            method = testClass.getMethod(methodName, (Class<?>[]) null);
        } catch (Exception e) {
            LOGGER.warn("Could not get method by reflection. This could happen if you are using @Parameters in combination with annotations.", e);
            return null;
        }
        return annotationDeploymentSetUp(cmmnEngine, testClass, method);
    }

    public static String annotationDeploymentSetUp(CmmnEngine cmmnEngine, Class<?> testClass, Method method) {
        CmmnDeployment deploymentAnnotation = method.getAnnotation(CmmnDeployment.class);
        return annotationDeploymentSetUp(cmmnEngine, testClass, method, deploymentAnnotation);
    }

    public static String annotationDeploymentSetUp(CmmnEngine cmmnEngine, Class<?> testClass, Method method, CmmnDeployment deploymentAnnotation) {
        return annotationDeploymentSetUp(cmmnEngine.getCmmnRepositoryService(), testClass, method, deploymentAnnotation);
    }

    public static String annotationDeploymentSetUp(CmmnRepositoryService cmmnRepositoryService, Class<?> testClass, Method method, CmmnDeployment deploymentAnnotation) {
        String deploymentId = null;
        String methodName = method.getName();
        if (deploymentAnnotation != null) {
            LOGGER.debug("annotation @CmmnDeployment creates deployment for {}.{}", testClass.getSimpleName(), methodName);
            String[] resources = deploymentAnnotation.resources();
            if (resources.length == 0) {
                String name = method.getName();
                String resource = getCmmnCaseDefinitionResource(testClass, name);
                resources = new String[] { resource };
            }

            CmmnDeploymentBuilder deploymentBuilder = cmmnRepositoryService.createDeployment().name(testClass.getSimpleName() + "." + methodName);

            for (String resource : resources) {
                deploymentBuilder.addClasspathResource(resource);
            }

            String[] extraResources = deploymentAnnotation.extraResources();
            if (extraResources != null && extraResources.length > 0) {
                for (String extraResource : extraResources) {
                    deploymentBuilder.addClasspathResource(extraResource);
                }
            }

            if (deploymentAnnotation.tenantId() != null
                && deploymentAnnotation.tenantId().length() > 0) {
                deploymentBuilder.tenantId(deploymentAnnotation.tenantId());
            }

            deploymentId = deploymentBuilder.deploy().getId();
        }

        return deploymentId;
    }

    public static void annotationDeploymentTearDown(CmmnEngine cmmnEngine, String deploymentId, Class<?> testClass, String methodName) {
        LOGGER.debug("annotation @CmmnDeployment deletes deployment for {}.{}", testClass.getSimpleName(), methodName);
        if (deploymentId != null) {
            try {
                CmmnTestHelper.deleteDeployment(cmmnEngine.getCmmnEngineConfiguration(), deploymentId);

            } catch (FlowableObjectNotFoundException e) {
                // Deployment was already deleted by the test case. Ignore.
            }
        }
    }
    
    public static CmmnEngine getCmmnEngine(String configurationResource) {
        CmmnEngine cmmnEngine = cmmnEngines.get(configurationResource);
        if (cmmnEngine == null) {
            LOGGER.debug("==== BUILDING PROCESS ENGINE ========================================================================");
            cmmnEngine = CmmnEngineConfiguration.createCmmnEngineConfigurationFromResource(configurationResource).buildCmmnEngine();
            LOGGER.debug("==== PROCESS ENGINE CREATED =========================================================================");
            cmmnEngines.put(configurationResource, cmmnEngine);
        }
        return cmmnEngine;
    }

    /**
     * get a resource location by convention based on a class (type) and a relative resource name. The return value will be the full classpath location of the type, plus a suffix built from the name
     * parameter: <code>CMMN_RESOURCE_SUFFIXES</code>. The first resource matching a suffix will be returned.
     */
    public static String getCmmnCaseDefinitionResource(Class<?> type, String name) {
        for (String suffix : CmmnDeployer.CMMN_RESOURCE_SUFFIXES) {
            String resource = type.getName().replace('.', '/') + "." + name + suffix;
            InputStream inputStream = ReflectUtil.getResourceAsStream(resource);
            if (inputStream == null) {
                continue;
            } else {
                return resource;
            }
        }
        return type.getName().replace('.', '/') + "." + name + CmmnDeployer.CMMN_RESOURCE_SUFFIXES[1];
    }

    public static void deleteDeployment(CmmnEngineConfiguration cmmnEngineConfiguration, String deploymentId) {
        if (deploymentId != null) {
            deleteWithoutGeneratingHistoryJobs(cmmnEngineConfiguration,
                configuration -> configuration.getCmmnRepositoryService().deleteDeployment(deploymentId, true));
        }
    }

    public static void deleteWithoutGeneratingHistoryJobs(CmmnEngineConfiguration cmmnEngineConfiguration, Consumer<CmmnEngineConfiguration> consumer) {
        boolean isAsyncHistoryEnabled = cmmnEngineConfiguration.isAsyncHistoryEnabled();
        if (isAsyncHistoryEnabled) {
            CmmnManagementService cmmnManagementService = cmmnEngineConfiguration.getCmmnManagementService();
            List<HistoryJob> historyJobs = cmmnManagementService.createHistoryJobQuery().list();
            for (HistoryJob historyJob : historyJobs) {
                cmmnManagementService.deleteHistoryJob(historyJob.getId());
            }
        }

        CmmnHistoryManager asyncHistoryManager = null;
        try {
            if (isAsyncHistoryEnabled) {
                cmmnEngineConfiguration.setAsyncHistoryEnabled(false);
                asyncHistoryManager = cmmnEngineConfiguration.getCmmnHistoryManager();
                cmmnEngineConfiguration.setCmmnHistoryManager(new DefaultCmmnHistoryManager(cmmnEngineConfiguration));
            }

            consumer.accept(cmmnEngineConfiguration);

        } finally {

            if (isAsyncHistoryEnabled) {
                cmmnEngineConfiguration.setAsyncHistoryEnabled(true);
                cmmnEngineConfiguration.setCmmnHistoryManager(asyncHistoryManager);
            }

        }
    }

}
