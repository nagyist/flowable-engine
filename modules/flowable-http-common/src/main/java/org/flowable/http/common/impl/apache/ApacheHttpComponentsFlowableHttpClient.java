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
package org.flowable.http.common.impl.apache;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpMessage;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.api.FlowableIllegalArgumentException;
import org.flowable.http.common.api.HttpHeaders;
import org.flowable.http.common.api.HttpRequest;
import org.flowable.http.common.api.HttpResponse;
import org.flowable.http.common.api.MultiValuePart;
import org.flowable.http.common.api.client.ExecutableHttpRequest;
import org.flowable.http.common.api.client.FlowableHttpClient;
import org.flowable.http.common.impl.HttpClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Filip Hrisafov
 */
public class ApacheHttpComponentsFlowableHttpClient implements FlowableHttpClient, HttpClient {

    // Implements HttpClient in order to be backwards compatible for the deprecated HttpRequestHandler

    private static final boolean MULTIPART_ENTITY_BUILDER_PRESENT;
    private static final Pattern PLUS_CHARACTER_PATTERN = Pattern.compile("\\+");
    private static final String ENCODED_PLUS_CHARACTER = "%2B";
    private static final Pattern SPACE_CHARACTER_PATTERN = Pattern.compile(" ");
    private static final String ENCODED_SPACE_CHARACTER = "%20";

    static {
        ClassLoader loader = ApacheHttpComponentsFlowableHttpClient.class.getClassLoader();
        boolean multipartEntityBuilderPresent = false;
        try {
            Class.forName("org.apache.http.entity.mime.MultipartEntityBuilder", false, loader);
            multipartEntityBuilderPresent = true;
        } catch (ClassNotFoundException e) {
        }

        MULTIPART_ENTITY_BUILDER_PRESENT = multipartEntityBuilderPresent;

    }

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected HttpClientBuilder clientBuilder;
    protected int socketTimeout;
    protected int connectTimeout;
    protected int connectionRequestTimeout;

    @SuppressWarnings("unused") // Used by HttpClientConfig determineHttpClient
    public ApacheHttpComponentsFlowableHttpClient(HttpClientConfig config) {
        this(config, clientBuilder -> {});
    }

    public ApacheHttpComponentsFlowableHttpClient(HttpClientConfig config, Consumer<HttpClientBuilder> clientBuilderCustomizer) {
        HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();

        // https settings
        if (config.isDisableCertVerify()) {
            try {
                SSLContextBuilder builder = new SSLContextBuilder();
                builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
                httpClientBuilder.setSSLSocketFactory(
                        new SSLConnectionSocketFactory(builder.build(), new HostnameVerifier() {

                            @Override
                            public boolean verify(String s, SSLSession sslSession) {
                                return true;
                            }
                        }));

            } catch (Exception e) {
                logger.error("Could not configure HTTP client SSL self signed strategy", e);
            }
        }

        // request retry settings
        int retryCount = 0;
        if (config.getRequestRetryLimit() > 0) {
            retryCount = config.getRequestRetryLimit();
        }
        httpClientBuilder.setRetryHandler(new DefaultHttpRequestRetryHandler(retryCount, false));

        // client builder settings
        if (config.isUseSystemProperties()) {
            httpClientBuilder.useSystemProperties();
        }

        clientBuilderCustomizer.accept(httpClientBuilder);

        this.clientBuilder = httpClientBuilder;

        this.socketTimeout = config.getSocketTimeout();
        this.connectTimeout = config.getConnectTimeout();
        this.connectionRequestTimeout = config.getConnectionRequestTimeout();
    }

    public ApacheHttpComponentsFlowableHttpClient(HttpClientBuilder clientBuilder, int socketTimeout, int connectTimeout,
            int connectionRequestTimeout) {
        this.clientBuilder = clientBuilder;
        this.socketTimeout = socketTimeout;
        this.connectTimeout = connectTimeout;
        this.connectionRequestTimeout = connectionRequestTimeout;
    }

    @Override
    public ExecutableHttpRequest prepareRequest(HttpRequest requestInfo) {
        try {
            HttpRequestBase request;
            URI uri = createUri(requestInfo.getUrl());
            switch (requestInfo.getMethod()) {
                case "GET": {
                    request = new HttpGet(uri);
                    break;
                }
                case "POST": {
                    HttpPost post = new HttpPost(uri);
                    setRequestEntity(requestInfo, post);
                    request = post;
                    break;
                }
                case "PUT": {
                    HttpPut put = new HttpPut(uri);
                    setRequestEntity(requestInfo, put);
                    request = put;
                    break;
                }
                case "PATCH": {
                    HttpPatch patch = new HttpPatch(uri);
                    setRequestEntity(requestInfo, patch);
                    request = patch;
                    break;
                }
                case "DELETE": {
                    HttpDeleteWithBody delete = new HttpDeleteWithBody(uri);
                    setRequestEntity(requestInfo, delete);
                    request = delete;
                    break;
                }
                case "HEAD": {
                    request = new HttpHead(uri);
                    break;
                }
                case "OPTIONS":
                    request = new HttpOptions(uri);
                    break;
                default: {
                    throw new FlowableException(requestInfo.getMethod() + " HTTP method not supported");
                }
            }

            setHeaders(request, requestInfo.getHttpHeaders());
            setHeaders(request, requestInfo.getSecureHttpHeaders());

            setConfig(request, requestInfo);
            return new ApacheHttpComponentsExecutableHttpRequest(request);
        } catch (URISyntaxException ex) {
            throw new FlowableException("Invalid URL exception occurred", ex);
        } catch (IOException ex) {
            throw new FlowableException("IO exception occurred", ex);
        }
    }

    protected URI createUri(String url) throws URISyntaxException {
        String uri = SPACE_CHARACTER_PATTERN.matcher(url).replaceAll(ENCODED_SPACE_CHARACTER);
        return new URI(PLUS_CHARACTER_PATTERN.matcher(uri).replaceAll(ENCODED_PLUS_CHARACTER));
    }

    protected void setRequestEntity(HttpRequest requestInfo, HttpEntityEnclosingRequestBase requestBase) throws UnsupportedEncodingException {
        if (requestInfo.getBody() != null) {
            if (StringUtils.isNotEmpty(requestInfo.getBodyEncoding())) {
                requestBase.setEntity(new StringEntity(requestInfo.getBody(), requestInfo.getBodyEncoding()));
            } else {
                requestBase.setEntity(new StringEntity(requestInfo.getBody()));
            }
        } else if (requestInfo.getMultiValueParts() != null) {
            if (MULTIPART_ENTITY_BUILDER_PRESENT) {
                MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create();
                entityBuilder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
                for (MultiValuePart part : requestInfo.getMultiValueParts()) {
                    String name = part.getName();
                    Object value = part.getBody();
                    if (value instanceof byte[]) {
                        if (StringUtils.isNotBlank(part.getMimeType())) {
                            entityBuilder.addBinaryBody(name, (byte[]) value, ContentType.create(part.getMimeType()), part.getFilename());
                        } else {
                            entityBuilder.addBinaryBody(name, (byte[]) value, ContentType.DEFAULT_BINARY, part.getFilename());
                        }
                    } else if (value instanceof String) {
                        entityBuilder.addTextBody(name, (String) value);
                    } else if (value != null) {
                        throw new FlowableIllegalArgumentException("Value of type " + value.getClass() + " is not supported as multi part content");
                    }
                }
                requestBase.setEntity(entityBuilder.build());
            } else {
                throw new FlowableException("org.apache.http.entity.mime.MultipartEntityBuilder is not present on the classpath."
                        + " Multi value parts cannot be used."
                        + " If you want to use, please make sure that the org.apache.httpcomponents:httpmime dependency is available");
            }

        } else if (requestInfo.getFormParameters() != null) {
            Map<String, List<String>> formParameters = requestInfo.getFormParameters();
            List<BasicNameValuePair> parameters = new ArrayList<>(formParameters.size());
            for (Map.Entry<String, List<String>> entry : formParameters.entrySet()) {
                String name = entry.getKey();
                for (String value : entry.getValue()) {
                    parameters.add(new BasicNameValuePair(name, value));
                }
            }

            if (StringUtils.isNotEmpty(requestInfo.getBodyEncoding())) {
                requestBase.setEntity(new UrlEncodedFormEntity(parameters, requestInfo.getBodyEncoding()));
            } else {
                requestBase.setEntity(new UrlEncodedFormEntity(parameters));
            }
        }
    }

    protected void setHeaders(final HttpMessage base, final HttpHeaders headers) {
        if (headers == null) {
            return;
        }
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            String headerName = entry.getKey();
            for (String headerValue : entry.getValue()) {
                base.addHeader(headerName, headerValue);
            }
        }
    }

    protected void setConfig(HttpRequestBase base, HttpRequest requestInfo) {
        base.setConfig(RequestConfig.custom()
                .setRedirectsEnabled(!requestInfo.isNoRedirects())
                .setSocketTimeout(requestInfo.getTimeout() == 0 ? socketTimeout : requestInfo.getTimeout())
                .setConnectTimeout(connectTimeout)
                .setConnectionRequestTimeout(connectionRequestTimeout)
                .build());
    }

    protected HttpResponse toFlowableHttpResponse(CloseableHttpResponse response) throws IOException {
        HttpResponse responseInfo = new HttpResponse();

        if (response.getStatusLine() != null) {
            responseInfo.setStatusCode(response.getStatusLine().getStatusCode());
            responseInfo.setProtocol(response.getStatusLine().getProtocolVersion().toString());
            responseInfo.setReason(response.getStatusLine().getReasonPhrase());
        }

        if (response.getAllHeaders() != null) {
            responseInfo.setHttpHeaders(getHeaders(response.getAllHeaders()));
        }

        if (response.getEntity() != null) {
            byte[] bodyBytes = EntityUtils.toByteArray(response.getEntity());
            if (bodyBytes != null) {
                Charset charset = determineCharset(response);
                responseInfo.setBody(new String(bodyBytes, charset));
                responseInfo.setBodyBytes(bodyBytes);
            }
        }

        return responseInfo;

    }

    protected Charset determineCharset(CloseableHttpResponse response) {
        // This is the logic that Apache Http Components does in EntityUtils#toString(HttpEntity)
        ContentType contentType = ContentType.get(response.getEntity());
        Charset charset = null;
        if (contentType != null) {
            charset = contentType.getCharset();
            if (charset == null) {
                ContentType defaultContentType = ContentType.getByMimeType(contentType.getMimeType());
                charset = defaultContentType != null ? defaultContentType.getCharset() : null;
            }
        }

        if (charset == null) {
            charset = HTTP.DEF_CONTENT_CHARSET;
        }

        return charset;
    }

    protected HttpHeaders getHeaders(Header[] headers) {
        HttpHeaders httpHeaders = new HttpHeaders();
        for (Header header : headers) {
            httpHeaders.add(header.getName(), header.getValue());
        }
        return httpHeaders;
    }

    // Apache HttpClient methods for backwards compatibility with deprecate HttpRequestHandler
    @Override
    public HttpParams getParams() {
        return null;
    }

    @Override
    public ClientConnectionManager getConnectionManager() {
        return null;
    }

    @Override
    public org.apache.http.HttpResponse execute(HttpUriRequest request) throws IOException, ClientProtocolException {
        try (CloseableHttpClient httpClient = clientBuilder.build()) {
            return httpClient.execute(request);
        }
    }

    @Override
    public org.apache.http.HttpResponse execute(HttpUriRequest request, HttpContext context) throws IOException, ClientProtocolException {
        try (CloseableHttpClient httpClient = clientBuilder.build()) {
            return httpClient.execute(request, context);
        }
    }

    @Override
    public org.apache.http.HttpResponse execute(HttpHost target, org.apache.http.HttpRequest request) throws IOException, ClientProtocolException {
        try (CloseableHttpClient httpClient = clientBuilder.build()) {
            return httpClient.execute(target, request);
        }
    }

    @Override
    public org.apache.http.HttpResponse execute(HttpHost target, org.apache.http.HttpRequest request, HttpContext context)
            throws IOException, ClientProtocolException {
        try (CloseableHttpClient httpClient = clientBuilder.build()) {
            return httpClient.execute(target, request, context);
        }
    }

    @Override
    public <T> T execute(HttpUriRequest request, ResponseHandler<? extends T> responseHandler) throws IOException, ClientProtocolException {
        try (CloseableHttpClient httpClient = clientBuilder.build()) {
            return httpClient.execute(request, responseHandler);
        }
    }

    @Override
    public <T> T execute(HttpUriRequest request, ResponseHandler<? extends T> responseHandler, HttpContext context)
            throws IOException, ClientProtocolException {
        try (CloseableHttpClient httpClient = clientBuilder.build()) {
            return httpClient.execute(request, responseHandler, context);
        }
    }

    @Override
    public <T> T execute(HttpHost target, org.apache.http.HttpRequest request, ResponseHandler<? extends T> responseHandler)
            throws IOException, ClientProtocolException {
        try (CloseableHttpClient httpClient = clientBuilder.build()) {
            return httpClient.execute(target, request, responseHandler);
        }
    }

    @Override
    public <T> T execute(HttpHost target, org.apache.http.HttpRequest request, ResponseHandler<? extends T> responseHandler, HttpContext context)
            throws IOException, ClientProtocolException {
        try (CloseableHttpClient httpClient = clientBuilder.build()) {
            return httpClient.execute(target, request, responseHandler, context);
        }
    }

    protected class ApacheHttpComponentsExecutableHttpRequest implements ExecutableHttpRequest {

        protected final HttpRequestBase request;

        public ApacheHttpComponentsExecutableHttpRequest(HttpRequestBase request) {
            this.request = request;
        }

        @Override
        public HttpResponse call() {
            try (CloseableHttpClient httpClient = clientBuilder.build()) {

                try (CloseableHttpResponse response = httpClient.execute(request)) {
                    return toFlowableHttpResponse(response);
                }

            } catch (ClientProtocolException ex) {
                throw new FlowableException("HTTP exception occurred", ex);
            } catch (IOException ex) {
                throw new FlowableException("IO exception occurred", ex);
            }
        }
    }
    
    /**
     * A HttpDelete alternative that extends {@link HttpEntityEnclosingRequestBase} to allow DELETE with a request body
     * 
     * @author ikaakkola
     */
    protected static class HttpDeleteWithBody extends HttpEntityEnclosingRequestBase {

        public HttpDeleteWithBody(URI uri) {
            super();
            setURI(uri);
        }

        @Override
        public String getMethod() {
            return "DELETE";
        }
    }
}
