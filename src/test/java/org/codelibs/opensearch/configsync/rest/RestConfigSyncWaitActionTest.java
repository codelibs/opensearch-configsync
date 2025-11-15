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

import org.codelibs.opensearch.configsync.service.ConfigSyncService;
import org.opensearch.common.settings.Settings;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestController;
import org.opensearch.rest.RestRequest;

import junit.framework.TestCase;

import java.util.List;

import static org.mockito.Mockito.*;

public class RestConfigSyncWaitActionTest extends TestCase {

    private RestConfigSyncWaitAction action;
    private ConfigSyncService mockConfigSyncService;
    private Settings settings;
    private RestController mockRestController;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        settings = Settings.EMPTY;
        mockRestController = mock(RestController.class);
        mockConfigSyncService = mock(ConfigSyncService.class);
        action = new RestConfigSyncWaitAction(settings, mockRestController, mockConfigSyncService);
    }

    public void test_instance_of_rest_configsync_action() {
        assertTrue(action instanceof RestConfigSyncAction);
    }

    public void test_instance_of_base_rest_handler() {
        assertTrue(action instanceof BaseRestHandler);
    }

    public void test_getName() {
        assertEquals("configsync_wait_action", action.getName());
    }

    public void test_routes_returns_one_route() {
        List<BaseRestHandler.Route> routes = action.routes();

        assertNotNull(routes);
        assertEquals(1, routes.size());
    }

    public void test_routes_contains_get_method() {
        List<BaseRestHandler.Route> routes = action.routes();

        BaseRestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.GET, route.getMethod());
        assertEquals("/_configsync/wait", route.getPath());
    }

    public void test_routes_are_unmodifiable() {
        List<BaseRestHandler.Route> routes = action.routes();

        try {
            routes.add(new BaseRestHandler.Route(RestRequest.Method.POST, "/_test"));
            fail("Routes list should be unmodifiable");
        } catch (UnsupportedOperationException e) {
            // Expected
        }
    }

    public void test_constructor_stores_config_sync_service() {
        assertNotNull(action);
    }

    public void test_multiple_instances_are_independent() {
        ConfigSyncService mockService2 = mock(ConfigSyncService.class);
        RestConfigSyncWaitAction action2 = new RestConfigSyncWaitAction(settings, mockRestController, mockService2);

        assertNotNull(action);
        assertNotNull(action2);
        assertNotSame(action, action2);
    }

    public void test_constructor_with_different_settings() {
        Settings customSettings = Settings.builder()
                .put("configsync.scroll_time", "5m")
                .build();

        RestConfigSyncWaitAction customAction = new RestConfigSyncWaitAction(customSettings, mockRestController, mockConfigSyncService);

        assertNotNull(customAction);
        assertEquals("configsync_wait_action", customAction.getName());
    }
}
