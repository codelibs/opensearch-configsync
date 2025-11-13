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
package org.codelibs.opensearch.configsync.action;

import org.opensearch.action.support.clustermanager.AcknowledgedResponse;
import junit.framework.TestCase;

public class ConfigResetSyncResponseTest extends TestCase {

    public void test_constructor_acknowledged_true() {
        ConfigResetSyncResponse response = new ConfigResetSyncResponse(true);
        assertTrue(response.isAcknowledged());
    }

    public void test_constructor_acknowledged_false() {
        ConfigResetSyncResponse response = new ConfigResetSyncResponse(false);
        assertFalse(response.isAcknowledged());
    }

    public void test_instance_of_acknowledged_response() {
        ConfigResetSyncResponse response = new ConfigResetSyncResponse(true);
        assertTrue(response instanceof AcknowledgedResponse);
    }

    public void test_multiple_instances_independent() {
        ConfigResetSyncResponse response1 = new ConfigResetSyncResponse(true);
        ConfigResetSyncResponse response2 = new ConfigResetSyncResponse(false);

        assertTrue(response1.isAcknowledged());
        assertFalse(response2.isAcknowledged());
    }
}
