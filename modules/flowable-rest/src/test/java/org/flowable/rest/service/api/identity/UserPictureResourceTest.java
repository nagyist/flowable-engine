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

package org.flowable.rest.service.api.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.flowable.idm.api.Picture;
import org.flowable.idm.api.User;
import org.flowable.rest.service.BaseSpringRestTestCase;
import org.flowable.rest.service.HttpMultipartHelper;
import org.flowable.rest.service.api.RestUrls;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * @author Frederik Heremans
 */
public class UserPictureResourceTest extends BaseSpringRestTestCase {

    /**
     * Test getting the picture for a user.
     */
    @Test
    public void testGetUserPicture() throws Exception {
        User savedUser = null;
        try {
            User newUser = identityService.newUser("testuser");
            newUser.setFirstName("Fred");
            newUser.setLastName("McDonald");
            newUser.setEmail("no-reply@activiti.org");
            identityService.saveUser(newUser);
            savedUser = newUser;

            // Create picture for user
            Picture thePicture = new Picture("this is the picture raw byte stream".getBytes(), "image/png");
            identityService.setUserPicture(newUser.getId(), thePicture);

            CloseableHttpResponse response = executeRequest(
                    new HttpGet(SERVER_URL_PREFIX + RestUrls.createRelativeResourceUrl(RestUrls.URL_USER_PICTURE, newUser.getId())), HttpStatus.SC_OK);

            try (InputStream contentStream = response.getEntity().getContent()) {
                assertThat(contentStream).hasContent("this is the picture raw byte stream");
            }

            // Check if media-type is correct
            assertThat(response.getEntity().getContentType().getValue()).isEqualTo("image/png");
            closeResponse(response);

        } finally {

            // Delete user after test passes or fails
            if (savedUser != null) {
                identityService.deleteUser(savedUser.getId());
            }
        }
    }

    /**
     * Test getting the picture for an unexisting user.
     */
    @Test
    public void testGetPictureForUnexistingUser() throws Exception {
        closeResponse(executeRequest(new HttpGet(SERVER_URL_PREFIX + RestUrls.createRelativeResourceUrl(RestUrls.URL_USER_PICTURE, "unexisting")),
                HttpStatus.SC_NOT_FOUND));
    }

    /**
     * Test getting the picture for a user who does not have a picture set
     */
    @Test
    public void testGetPictureForUserWithoutPicture() throws Exception {
        User savedUser = null;
        try {
            User newUser = identityService.newUser("testuser");
            newUser.setFirstName("Fred");
            newUser.setLastName("McDonald");
            newUser.setEmail("no-reply@activiti.org");
            identityService.saveUser(newUser);
            savedUser = newUser;

            CloseableHttpResponse response = executeRequest(
                    new HttpGet(SERVER_URL_PREFIX + RestUrls.createRelativeResourceUrl(RestUrls.URL_USER_PICTURE, newUser.getId())), HttpStatus.SC_NOT_FOUND);

            // response content type application/json;charset=UTF-8
            assertThat(response.getEntity().getContentType().getValue().split(";")[0]).isEqualTo("application/json");
            closeResponse(response);

        } finally {

            // Delete user after test passes or fails
            if (savedUser != null) {
                identityService.deleteUser(savedUser.getId());
            }
        }
    }

    @Test
    public void testUpdatePicture() throws Exception {
        User savedUser = null;
        try {
            User newUser = identityService.newUser("testuser");
            newUser.setFirstName("Fred");
            newUser.setLastName("McDonald");
            newUser.setEmail("no-reply@activiti.org");
            identityService.saveUser(newUser);
            savedUser = newUser;

            HttpPut httpPut = new HttpPut(SERVER_URL_PREFIX + RestUrls.createRelativeResourceUrl(RestUrls.URL_USER_PICTURE, newUser.getId()));
            httpPut.setEntity(HttpMultipartHelper
                    .getMultiPartEntity("myPicture.png", "image/png", new ByteArrayInputStream("this is the picture raw byte stream".getBytes()), null));
            closeResponse(executeBinaryRequest(httpPut, HttpStatus.SC_NO_CONTENT));

            Picture picture = identityService.getUserPicture(newUser.getId());
            assertThat(picture).isNotNull();
            assertThat(picture.getMimeType()).isEqualTo("image/png");
            assertThat(new String(picture.getBytes())).isEqualTo("this is the picture raw byte stream");

        } finally {

            // Delete user after test passes or fails
            if (savedUser != null) {
                identityService.deleteUser(savedUser.getId());
            }
        }
    }

    @Test
    public void testUpdatePictureWithCustomMimeType() throws Exception {
        User savedUser = null;
        try {
            User newUser = identityService.newUser("testuser");
            newUser.setFirstName("Fred");
            newUser.setLastName("McDonald");
            newUser.setEmail("no-reply@activiti.org");
            identityService.saveUser(newUser);
            savedUser = newUser;

            Map<String, String> additionalFields = new HashMap<>();
            additionalFields.put("mimeType", MediaType.IMAGE_PNG.toString());

            HttpPut httpPut = new HttpPut(SERVER_URL_PREFIX + RestUrls.createRelativeResourceUrl(RestUrls.URL_USER_PICTURE, newUser.getId()));
            httpPut.setEntity(HttpMultipartHelper
                    .getMultiPartEntity("myPicture.png", "image/png", new ByteArrayInputStream("this is the picture raw byte stream".getBytes()),
                            additionalFields));
            closeResponse(executeBinaryRequest(httpPut, HttpStatus.SC_NO_CONTENT));

            Picture picture = identityService.getUserPicture(newUser.getId());
            assertThat(picture).isNotNull();
            assertThat(picture.getMimeType()).isEqualTo("image/png");
            assertThat(new String(picture.getBytes())).isEqualTo("this is the picture raw byte stream");

        } finally {

            // Delete user after test passes or fails
            if (savedUser != null) {
                identityService.deleteUser(savedUser.getId());
            }
        }
    }

}
