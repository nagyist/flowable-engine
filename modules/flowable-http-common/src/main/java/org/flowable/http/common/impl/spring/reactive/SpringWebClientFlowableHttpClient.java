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
package org.flowable.http.common.impl.spring.reactive;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.http.common.api.HttpRequest;
import org.flowable.http.common.api.HttpResponse;
import org.flowable.http.common.api.MultiValuePart;
import org.flowable.http.common.api.client.AsyncExecutableHttpRequest;
import org.flowable.http.common.api.client.FlowableAsyncHttpClient;
import org.flowable.http.common.impl.HttpClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.timeout.ReadTimeoutHandler;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

/**
 * @author Filip Hrisafov
 */
public class SpringWebClientFlowableHttpClient implements FlowableAsyncHttpClient {

    private static final Pattern PLUS_CHARACTER_PATTERN = Pattern.compile("\\+");
    private static final String ENCODED_PLUS_CHARACTER = "%2B";
    private static final Pattern SPACE_CHARACTER_PATTERN = Pattern.compile(" ");
    private static final String ENCODED_SPACE_CHARACTER = "%20";

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected final WebClient webClient;
    protected final Duration initialRequestTimeout;

    public SpringWebClientFlowableHttpClient(HttpClientConfig config) {
        HttpClient httpClient = HttpClient.create(ConnectionProvider
                .builder("flowableHttpClient")
                .maxConnections(500)
                .build())
                .compress(true);

        if (config.isDisableCertVerify()) {
            try {
                SslContext sslContext = SslContextBuilder
                        .forClient()
                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .build();

                httpClient = httpClient.secure(spec -> spec.sslContext(sslContext));
            } catch (Exception e) {
                logger.error("Could not configure HTTP Client SSL self signed strategy", e);
            }
        }

        httpClient = httpClient.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.getConnectTimeout())
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(config.getSocketTimeout(), TimeUnit.MILLISECONDS)));

        WebClient.Builder webClientBuilder = WebClient.builder();
        webClientBuilder = webClientBuilder.clientConnector(new ReactorClientHttpConnector(httpClient));

        this.webClient = webClientBuilder.build();
        this.initialRequestTimeout = Duration.ofMillis(config.getSocketTimeout());
    }

    public SpringWebClientFlowableHttpClient(WebClient.Builder builder) {
        this.webClient = builder.build();
        this.initialRequestTimeout = null;
    }

    @Override
    public AsyncExecutableHttpRequest prepareRequest(HttpRequest requestInfo) {
        try {
            WebClient.RequestHeadersSpec<?> headersSpec;
            URI uri = createUri(requestInfo.getUrl());
            switch (requestInfo.getMethod()) {
                case "GET": {
                    headersSpec = webClient.get().uri(uri);
                    break;
                }
                case "POST": {
                    WebClient.RequestBodySpec post = webClient.post().uri(uri);
                    setRequestEntity(requestInfo, post);
                    headersSpec = post;
                    break;
                }
                case "PUT": {
                    WebClient.RequestBodySpec put = webClient.put()
                            .uri(uri);
                    setRequestEntity(requestInfo, put);
                    headersSpec = put;
                    break;
                }
                case "PATCH": {
                    WebClient.RequestBodySpec patch = webClient.patch().uri(uri);
                    setRequestEntity(requestInfo, patch);
                    headersSpec = patch;
                    break;
                }
                case "DELETE": {
                    WebClient.RequestBodySpec delete = webClient.method(HttpMethod.DELETE).uri(uri);
                    setRequestEntity(requestInfo, delete);
                    headersSpec = delete;
                    break;
                }
                case "HEAD": {
                    headersSpec = webClient.head().uri(uri);
                    break;
                }
                case "OPTIONS": {
                    headersSpec = webClient.options().uri(uri);
                    break;
                }
                default: {
                    throw new FlowableException(requestInfo.getMethod() + " HTTP method not supported");
                }
            }

            setHeaders(headersSpec, requestInfo.getHttpHeaders());
            setHeaders(headersSpec, requestInfo.getSecureHttpHeaders());

            return new WebClientExecutableHttpRequest(headersSpec);
        } catch (URISyntaxException ex) {
            throw new FlowableException("Invalid URL exception occurred", ex);
        }
    }

    protected WebClient determineWebClient(HttpRequest requestInfo) {
        if (requestInfo.getTimeout() <= 0) {
            return webClient;
        }

        Duration requestTimeout = Duration.ofMillis(requestInfo.getTimeout());
        if (requestTimeout.equals(initialRequestTimeout)) {
            // If the request timeout is the same as the initial request timeout then there is nothing to do
            return webClient;
        }

        return webClient.mutate()
                .filter((request, next) -> next.exchange(request).timeout(requestTimeout))
                .build();

    }

    protected URI createUri(String url) throws URISyntaxException {
        String uri = SPACE_CHARACTER_PATTERN.matcher(url).replaceAll(ENCODED_SPACE_CHARACTER);
        return new URI(PLUS_CHARACTER_PATTERN.matcher(uri).replaceAll(ENCODED_PLUS_CHARACTER));
    }

    protected void setRequestEntity(HttpRequest requestInfo, WebClient.RequestBodySpec requestBodySpec) {
        if (requestInfo.getBody() != null) {
            if (StringUtils.isNotEmpty(requestInfo.getBodyEncoding())) {
                requestBodySpec.contentType(new MediaType(MediaType.TEXT_PLAIN, Charset.forName(requestInfo.getBodyEncoding())));
            }

            requestBodySpec.bodyValue(requestInfo.getBody());
        } else if (requestInfo.getMultiValueParts() != null) {
            MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
            for (MultiValuePart part : requestInfo.getMultiValueParts()) {
                String name = part.getName();
                Object value = part.getBody();


                if (value instanceof byte[]) {
                    value = new ByteArrayResourceWithFileName((byte[]) value, part.getFilename());
                }
                MultipartBodyBuilder.PartBuilder partBuilder;
                if (StringUtils.isNotBlank(part.getMimeType())) {
                    partBuilder = multipartBodyBuilder.part(name, value, MediaType.parseMediaType(part.getMimeType()));
                } else {
                    partBuilder = multipartBodyBuilder.part(name, value);
                }
                if (StringUtils.isNotEmpty(part.getFilename())) {
                    partBuilder.filename(part.getFilename());
                }
            }

            requestBodySpec.body(BodyInserters.fromMultipartData(multipartBodyBuilder.build()));
        } else if (requestInfo.getFormParameters() != null) {
            requestBodySpec.body(BodyInserters.fromFormData(CollectionUtils.toMultiValueMap(requestInfo.getFormParameters())));
        }
    }

    protected void setHeaders(WebClient.RequestHeadersSpec<?> base, org.flowable.http.common.api.HttpHeaders headers) {
        if (headers == null) {
            return;
        }
        base.headers(httpHeaders -> {
            httpHeaders.putAll(headers);
        });
    }

    protected HttpResponse toFlowableHttpResponse(ResponseEntity<ByteArrayResource> response) {
        HttpResponse responseInfo = new HttpResponse();

        HttpStatusCode statusCode = response.getStatusCode();
        responseInfo.setStatusCode(statusCode.value());
        if (statusCode instanceof HttpStatus httpStatus) {
            responseInfo.setReason(httpStatus.getReasonPhrase());
        }

        responseInfo.setHttpHeaders(toFlowableHeaders(response.getHeaders()));

        ByteArrayResource body = response.getBody();
        if (body != null) {
            MediaType contentType = response.getHeaders().getContentType();
            byte[] bodyBytes = body.getByteArray();

            if (contentType != null && contentType.getCharset() != null) {
                responseInfo.setBody(new String(bodyBytes, contentType.getCharset()));
            } else {
                responseInfo.setBody(new String(bodyBytes));
            }

            responseInfo.setBodyBytes(bodyBytes);
        }

        return responseInfo;

    }

    protected org.flowable.http.common.api.HttpHeaders toFlowableHeaders(HttpHeaders httpHeaders) {
        org.flowable.http.common.api.HttpHeaders headers = new org.flowable.http.common.api.HttpHeaders();
        for (Map.Entry<String, List<String>> entry : httpHeaders.entrySet()) {
            String headerName = entry.getKey();
            List<String> headerValues = entry.getValue();
            for (String headerValue : headerValues) {
                headers.add(headerName, headerValue);
            }
        }
        return headers;
    }

    protected class WebClientExecutableHttpRequest implements AsyncExecutableHttpRequest {

        protected final WebClient.RequestHeadersSpec<?> request;

        public WebClientExecutableHttpRequest(WebClient.RequestHeadersSpec<?> request) {
            this.request = request;
        }

        @Override
        public CompletableFuture<HttpResponse> callAsync() {
            return request
                    .exchangeToMono(response -> response.toEntity(ByteArrayResource.class))
                    .map(SpringWebClientFlowableHttpClient.this::toFlowableHttpResponse)
                    .toFuture();
        }

    }

    protected static class ByteArrayResourceWithFileName extends ByteArrayResource {

        protected final String filename;

        public ByteArrayResourceWithFileName(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
