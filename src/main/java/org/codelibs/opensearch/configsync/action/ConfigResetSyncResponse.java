package org.codelibs.opensearch.configsync.action;

import org.opensearch.action.support.master.AcknowledgedResponse;

public class ConfigResetSyncResponse extends AcknowledgedResponse {
    public ConfigResetSyncResponse(final boolean acknowledged) {
        super(acknowledged);
    }
}
