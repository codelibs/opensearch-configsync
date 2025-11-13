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
package org.codelibs.opensearch.configsync;

import org.codelibs.opensearch.configsync.rest.RestConfigSyncFileAction;
import org.codelibs.opensearch.configsync.rest.RestConfigSyncFlushAction;
import org.codelibs.opensearch.configsync.rest.RestConfigSyncResetAction;
import org.codelibs.opensearch.configsync.rest.RestConfigSyncWaitAction;
import org.codelibs.opensearch.configsync.service.ConfigSyncService;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.common.lifecycle.LifecycleComponent;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.IndexScopedSettings;
import org.opensearch.common.settings.Setting;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.settings.SettingsFilter;
import org.opensearch.cluster.metadata.IndexNameExpressionResolver;
import org.opensearch.indices.SystemIndexDescriptor;
import org.opensearch.plugins.ActionPlugin;
import org.opensearch.plugins.Plugin;
import org.opensearch.plugins.SystemIndexPlugin;
import org.opensearch.rest.RestController;
import org.opensearch.rest.RestHandler;

import junit.framework.TestCase;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import static org.mockito.Mockito.*;

public class ConfigSyncPluginUnitTest extends TestCase {

    private ConfigSyncPlugin plugin;
    private ConfigSyncService mockConfigSyncService;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        plugin = new ConfigSyncPlugin();
        mockConfigSyncService = mock(ConfigSyncService.class);
        plugin.pluginComponent.setConfigSyncService(mockConfigSyncService);
    }

    public void test_plugin_is_instance_of_plugin() {
        assertTrue(plugin instanceof Plugin);
    }

    public void test_plugin_is_instance_of_action_plugin() {
        assertTrue(plugin instanceof ActionPlugin);
    }

    public void test_plugin_is_instance_of_system_index_plugin() {
        assertTrue(plugin instanceof SystemIndexPlugin);
    }

    public void test_getRestHandlers_returns_four_handlers() {
        Settings settings = Settings.EMPTY;
        RestController restController = mock(RestController.class);
        ClusterSettings clusterSettings = mock(ClusterSettings.class);
        IndexScopedSettings indexScopedSettings = mock(IndexScopedSettings.class);
        SettingsFilter settingsFilter = mock(SettingsFilter.class);
        IndexNameExpressionResolver indexNameExpressionResolver = mock(IndexNameExpressionResolver.class);
        @SuppressWarnings("unchecked")
        Supplier<DiscoveryNodes> nodesInCluster = mock(Supplier.class);

        List<RestHandler> handlers = plugin.getRestHandlers(settings, restController, clusterSettings,
                indexScopedSettings, settingsFilter, indexNameExpressionResolver, nodesInCluster);

        assertNotNull(handlers);
        assertEquals(4, handlers.size());

        assertTrue(handlers.get(0) instanceof RestConfigSyncFileAction);
        assertTrue(handlers.get(1) instanceof RestConfigSyncResetAction);
        assertTrue(handlers.get(2) instanceof RestConfigSyncFlushAction);
        assertTrue(handlers.get(3) instanceof RestConfigSyncWaitAction);
    }

    public void test_getGuiceServiceClasses_returns_config_sync_service() {
        Collection<Class<? extends LifecycleComponent>> services = plugin.getGuiceServiceClasses();

        assertNotNull(services);
        assertEquals(1, services.size());
        assertTrue(services.contains(ConfigSyncService.class));
    }

    public void test_createComponents_returns_plugin_component() {
        Collection<Object> components = plugin.createComponents(null, null, null, null, null,
                null, null, null, null, null, null);

        assertNotNull(components);
        assertEquals(1, components.size());
        assertTrue(components.iterator().next() instanceof ConfigSyncPlugin.PluginComponent);
    }

    public void test_getSettings_returns_all_settings() {
        List<Setting<?>> settings = plugin.getSettings();

        assertNotNull(settings);
        assertEquals(7, settings.size());

        assertTrue(settings.contains(ConfigSyncService.INDEX_SETTING));
        assertTrue(settings.contains(ConfigSyncService.XPACK_SECURITY_SETTING));
        assertTrue(settings.contains(ConfigSyncService.CONFIG_PATH_SETTING));
        assertTrue(settings.contains(ConfigSyncService.SCROLL_TIME_SETTING));
        assertTrue(settings.contains(ConfigSyncService.SCROLL_SIZE_SETTING));
        assertTrue(settings.contains(ConfigSyncService.FLUSH_INTERVAL_SETTING));
        assertTrue(settings.contains(ConfigSyncService.FILE_UPDATER_ENABLED_SETTING));
    }

    public void test_getSystemIndexDescriptors_returns_configsync_descriptor() {
        Settings settings = Settings.EMPTY;
        Collection<SystemIndexDescriptor> descriptors = plugin.getSystemIndexDescriptors(settings);

        assertNotNull(descriptors);
        assertEquals(1, descriptors.size());

        SystemIndexDescriptor descriptor = descriptors.iterator().next();
        assertEquals(".configsync", descriptor.getIndexPattern());
        assertEquals("Contains config sync data", descriptor.getDescription());
    }

    public void test_plugin_component_get_set_config_sync_service() {
        ConfigSyncPlugin.PluginComponent component = new ConfigSyncPlugin.PluginComponent();
        ConfigSyncService mockService = mock(ConfigSyncService.class);

        assertNull(component.getConfigSyncService());

        component.setConfigSyncService(mockService);
        assertSame(mockService, component.getConfigSyncService());
    }

    public void test_plugin_component_multiple_set_operations() {
        ConfigSyncPlugin.PluginComponent component = new ConfigSyncPlugin.PluginComponent();
        ConfigSyncService mockService1 = mock(ConfigSyncService.class);
        ConfigSyncService mockService2 = mock(ConfigSyncService.class);

        component.setConfigSyncService(mockService1);
        assertSame(mockService1, component.getConfigSyncService());

        component.setConfigSyncService(mockService2);
        assertSame(mockService2, component.getConfigSyncService());
    }
}
