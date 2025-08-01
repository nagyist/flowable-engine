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
package org.flowable.rest.service.api.repository;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.message.BasicHeader;
import org.flowable.engine.repository.Deployment;
import org.flowable.rest.service.BaseSpringRestTestCase;
import org.flowable.rest.service.api.RestUrls;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import net.javacrumbs.jsonunit.core.Option;

/**
 * Test for all REST-operations related to a resources that is part of a deployment.
 *
 * @author Frederik Heremans
 */
public class DeploymentResourceResourceTest extends BaseSpringRestTestCase {

    /**
     * Test getting a single resource, deployed in a deployment. GET repository/deployments/{deploymentId}/resources/{resourceId}
     */
    @Test
    public void testGetDeploymentResource() throws Exception {
        try {
            String rawResourceName = "org/flowable/rest/service/api/repository/oneTaskProcess.bpmn20.xml";
            Deployment deployment = repositoryService.createDeployment().name("Deployment 1").addClasspathResource(rawResourceName)
                    .addInputStream("test.txt", new ByteArrayInputStream("Test content".getBytes())).deploy();

            // Build up the URL manually to make sure resource-id gets encoded
            // correctly as one piece
            HttpGet httpGet = new HttpGet(buildUrl(RestUrls.URL_DEPLOYMENT_RESOURCE, deployment.getId(), rawResourceName));
            httpGet.addHeader(new BasicHeader(HttpHeaders.ACCEPT, "application/json"));
            CloseableHttpResponse response = executeRequest(httpGet, HttpStatus.SC_OK);
            JsonNode responseNode = objectMapper.readTree(response.getEntity().getContent());
            closeResponse(response);
            assertThatJson(responseNode)
                    .when(Option.IGNORING_EXTRA_FIELDS)
                    .isEqualTo("{"
                            + "    url: '" + buildUrl(RestUrls.URL_DEPLOYMENT_RESOURCE, deployment.getId(), rawResourceName) + "',"
                            + "    contentUrl: '" + buildUrl(RestUrls.URL_DEPLOYMENT_RESOURCE_CONTENT, deployment.getId(), rawResourceName) + "',"
                            + "    mediaType: 'text/xml',"
                            + "    type: 'processDefinition'"
                            + "}");

        } finally {
            // Always cleanup any created deployments, even if the test failed
            List<Deployment> deployments = repositoryService.createDeploymentQuery().list();
            for (Deployment deployment : deployments) {
                repositoryService.deleteDeployment(deployment.getId(), true);
            }
        }
    }

    /**
     * Test getting a single resource for an unexisting deployment. GET repository/deployments/{deploymentId}/resources/{resourceId}
     */
    @Test
    public void testGetDeploymentResourceUnexistingDeployment() throws Exception {
        HttpGet httpGet = new HttpGet(SERVER_URL_PREFIX + RestUrls.createRelativeResourceUrl(RestUrls.URL_DEPLOYMENT_RESOURCE, "unexisting", "resource.png"));
        httpGet.addHeader(new BasicHeader(HttpHeaders.ACCEPT, "image/png,application/json"));
        closeResponse(executeRequest(httpGet, HttpStatus.SC_NOT_FOUND));
    }

    /**
     * Test getting an unexisting resource for an existing deployment. GET repository/deployments/{deploymentId}/resources/{resourceId}
     */
    @Test
    public void testGetDeploymentResourceUnexistingResource() throws Exception {
        try {
            Deployment deployment = repositoryService.createDeployment().name("Deployment 1").addInputStream("test.txt", new ByteArrayInputStream("Test content".getBytes())).deploy();

            HttpGet httpGet = new HttpGet(SERVER_URL_PREFIX + RestUrls.createRelativeResourceUrl(RestUrls.URL_DEPLOYMENT_RESOURCE, deployment.getId(), "unexisting-resource.png"));
            httpGet.addHeader(new BasicHeader(HttpHeaders.ACCEPT, "image/png,application/json"));
            closeResponse(executeRequest(httpGet, HttpStatus.SC_NOT_FOUND));

        } finally {
            // Always cleanup any created deployments, even if the test failed
            List<Deployment> deployments = repositoryService.createDeploymentQuery().list();
            for (Deployment deployment : deployments) {
                repositoryService.deleteDeployment(deployment.getId(), true);
            }
        }
    }

    /**
     * Test getting a deployment resource content. GET repository/deployments/{deploymentId}/resources/{resourceId}
     */
    @Test
    public void testGetDeploymentResourceContent() throws Exception {
        try {
            Deployment deployment = repositoryService.createDeployment().name("Deployment 1")
                    .addInputStream("test.txt", new ByteArrayInputStream("Test content".getBytes())).deploy();

            HttpGet httpGet = new HttpGet(
                    SERVER_URL_PREFIX + RestUrls.createRelativeResourceUrl(RestUrls.URL_DEPLOYMENT_RESOURCE_CONTENT, deployment.getId(), "test.txt"));
            httpGet.addHeader(new BasicHeader(HttpHeaders.ACCEPT, "text/plain"));
            CloseableHttpResponse response = executeRequest(httpGet, HttpStatus.SC_OK);
            String responseAsString = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            closeResponse(response);
            assertThat(responseAsString).isEqualTo("Test content");

        } finally {
            // Always cleanup any created deployments, even if the test failed
            List<Deployment> deployments = repositoryService.createDeploymentQuery().list();
            for (Deployment deployment : deployments) {
                repositoryService.deleteDeployment(deployment.getId(), true);
            }
        }
    }
}
