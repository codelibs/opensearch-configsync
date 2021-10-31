package org.codelibs.opensearch.configsync.action;

import org.opensearch.action.support.master.AcknowledgedResponse;

public class ConfigFileFlushResponse extends AcknowledgedResponse {
    public ConfigFileFlushResponse(final boolean acknowledged) {
        super(acknowledged);
    }
}
