/*
 * Copyright 2012-2025 CodeLibs Project and the Others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.codelibs.opensearch.configsync.rest;

import org.opensearch.OpenSearchException;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestRequest;
import org.opensearch.transport.client.node.NodeClient;

import junit.framework.TestCase;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

public class RestConfigSyncActionTest extends TestCase {

    private TestRestConfigSyncAction action;
    private RestChannel mockChannel;
    private RestRequest mockRequest;
    private XContentBuilder mockBuilder;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        action = new TestRestConfigSyncAction();
        mockChannel = mock(RestChannel.class);
        mockRequest = mock(RestRequest.class);
        // Use RETURNS_SELF to make the builder fluent
        mockBuilder = mock(XContentBuilder.class, RETURNS_SELF);

        // BytesRestResponse constructor calls these methods on channel
        when(mockChannel.request()).thenReturn(mockRequest);
        when(mockChannel.newErrorBuilder()).thenReturn(mockBuilder);
        when(mockChannel.detailedErrorsEnabled()).thenReturn(false);
    }

    public void test_instance_of_base_rest_handler() {
        assertTrue(action instanceof BaseRestHandler);
    }

    public void test_sendResponse_with_null_params() throws IOException {
        action.sendResponse(mockChannel, null);

        verify(mockChannel, times(1)).sendResponse(any(BytesRestResponse.class));
    }

    public void test_sendResponse_with_empty_params() throws IOException {
        Map<String, Object> params = new HashMap<>();

        action.sendResponse(mockChannel, params);

        verify(mockChannel, times(1)).sendResponse(any(BytesRestResponse.class));
    }

    public void test_sendResponse_with_multiple_params() throws IOException {
        Map<String, Object> params = new HashMap<>();
        params.put("key1", "value1");
        params.put("key2", 123);
        params.put("key3", true);

        action.sendResponse(mockChannel, params);

        verify(mockChannel, times(1)).sendResponse(any(BytesRestResponse.class));
    }

    public void test_sendErrorResponse_with_exception() {
        Exception exception = new RuntimeException("Test exception");

        action.sendErrorResponse(mockChannel, exception);

        verify(mockChannel, times(1)).sendResponse(any(BytesRestResponse.class));
    }

    public void test_sendErrorResponse_with_opensearch_exception() {
        OpenSearchException exception = new OpenSearchException("OpenSearch test exception");

        action.sendErrorResponse(mockChannel, exception);

        verify(mockChannel, times(1)).sendResponse(any(BytesRestResponse.class));
    }

    public void test_sendErrorResponse_when_channel_throws_exception() {
        Exception exception = new RuntimeException("Test exception");
        doThrow(new RuntimeException("Channel error")).when(mockChannel).sendResponse(any(BytesRestResponse.class));

        // Should not throw an exception, but log the error
        action.sendErrorResponse(mockChannel, exception);

        verify(mockChannel, times(1)).sendResponse(any(BytesRestResponse.class));
    }

    public void test_logger_is_not_null() {
        assertNotNull(action.logger);
    }

    // Test implementation of RestConfigSyncAction for testing purposes
    private static class TestRestConfigSyncAction extends RestConfigSyncAction {
        @Override
        public String getName() {
            return "test_configsync_action";
        }

        @Override
        public List<Route> routes() {
            return List.of();
        }

        @Override
        protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
            return channel -> {};
        }
    }
}
