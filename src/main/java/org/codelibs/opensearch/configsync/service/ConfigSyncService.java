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
package org.codelibs.opensearch.configsync.service;

import static org.opensearch.core.action.ActionListener.wrap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Base64OutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.opensearch.configsync.ConfigSyncPlugin.PluginComponent;
import org.codelibs.opensearch.configsync.action.ConfigFileFlushResponse;
import org.codelibs.opensearch.configsync.action.ConfigResetSyncResponse;
import org.opensearch.OpenSearchException;
import org.opensearch.action.admin.cluster.health.ClusterHealthResponse;
import org.opensearch.action.bulk.BulkRequestBuilder;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.WriteRequest.RefreshPolicy;
import org.opensearch.action.support.clustermanager.AcknowledgedResponse;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.health.ClusterHealthStatus;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.io.Streams;
import org.opensearch.common.lifecycle.AbstractLifecycleComponent;
import org.opensearch.common.lifecycle.LifecycleListener;
import org.opensearch.common.settings.Setting;
import org.opensearch.common.settings.Setting.Property;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.env.Environment;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.sort.SortOrder;
import org.opensearch.tasks.Task;
import org.opensearch.threadpool.Scheduler.ScheduledCancellable;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.threadpool.ThreadPool.Names;
import org.opensearch.transport.TransportChannel;
import org.opensearch.transport.TransportException;
import org.opensearch.transport.TransportRequest;
import org.opensearch.transport.TransportRequestHandler;
import org.opensearch.transport.TransportResponseHandler;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class ConfigSyncService extends AbstractLifecycleComponent {
    private static final Logger logger = LogManager.getLogger(ConfigSyncService.class);

    public static final Setting<Boolean> FILE_UPDATER_ENABLED_SETTING =
            Setting.boolSetting("configsync.file_updater.enabled", true, Property.NodeScope);

    public static final Setting<TimeValue> FLUSH_INTERVAL_SETTING =
            Setting.timeSetting("configsync.flush_interval", TimeValue.timeValueMinutes(1), Property.NodeScope, Property.Dynamic);

    public static final Setting<Integer> SCROLL_SIZE_SETTING = Setting.intSetting("configsync.scroll_size", 1, Property.NodeScope);

    public static final Setting<TimeValue> SCROLL_TIME_SETTING =
            Setting.timeSetting("configsync.scroll_time", TimeValue.timeValueMinutes(1), Property.NodeScope);

    public static final Setting<String> CONFIG_PATH_SETTING = Setting.simpleString("configsync.config_path", Property.NodeScope);

    public static final Setting<String> INDEX_SETTING =
            new Setting<>("configsync.index", s -> "configsync", Function.identity(), Property.NodeScope);

    public static final Setting<String> XPACK_SECURITY_SETTING =
            new Setting<>("configsync.xpack.security.user", s -> "", ConfigSyncService::xpackSecurityToken, Property.NodeScope);

    public static final String ACTION_CONFIG_FLUSH = "cluster:admin/configsync/flush";

    public static final String ACTION_CONFIG_RESET = "cluster:admin/configsync/reset_sync";

    private static final String FILE_MAPPING_JSON = "configsync/file_mapping.json";

    public static final String TIMESTAMP = "@timestamp";

    public static final String CONTENT = "content";

    public static final String PATH = "path";

    private final Client client;

    private final String index;

    private String configPath;

    private final ThreadPool threadPool;

    private final TimeValue scrollForUpdate;

    private final int sizeForUpdate;

    private Date lastChecked = new Date(0);

    private ConfigFileUpdater configFileUpdater;

    private final ClusterService clusterService;

    private final TransportService transportService;

    private volatile ScheduledCancellable scheduledCancellable;

    private final boolean fileUpdaterEnabled;

    private final TimeValue flushInterval;

    private final String authorizationToken;

    private static String xpackSecurityToken(final String s) {
        if (s == null || s.trim().length() == 0) {
            return "";
        }
        final String basicAuth = java.util.Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
        return "Basic " + basicAuth;
    }

    @Inject
    public ConfigSyncService(final Settings settings, final Client client, final ClusterService clusterService,
            final TransportService transportService, final Environment env, final ThreadPool threadPool,
            final PluginComponent pluginComponent) {
        this.client = client;
        this.clusterService = clusterService;
        this.transportService = transportService;
        this.threadPool = threadPool;

        if (logger.isDebugEnabled()) {
            logger.debug("Creating ConfigSyncService");
        }

        index = INDEX_SETTING.get(settings);
        configPath = CONFIG_PATH_SETTING.get(settings);
        if (configPath.length() == 0) {
            configPath = env.configDir().toFile().getAbsolutePath();
        }
        scrollForUpdate = SCROLL_TIME_SETTING.get(settings);
        sizeForUpdate = SCROLL_SIZE_SETTING.get(settings);
        fileUpdaterEnabled = FILE_UPDATER_ENABLED_SETTING.get(settings);
        flushInterval = FLUSH_INTERVAL_SETTING.get(settings);
        authorizationToken = XPACK_SECURITY_SETTING.get(settings);

        transportService.registerRequestHandler(ACTION_CONFIG_FLUSH, ThreadPool.Names.GENERIC, FileFlushRequest::new,
                new ConfigFileFlushRequestHandler());
        transportService.registerRequestHandler(ACTION_CONFIG_RESET, ThreadPool.Names.GENERIC, ResetSyncRequest::new,
                new ConfigSyncResetRequestHandler());

        pluginComponent.setConfigSyncService(this);
    }

    private Client client() {
        if (authorizationToken.length() > 0) {
            return client.filterWithHeader(Collections.singletonMap("Authorization", authorizationToken));
        }
        return this.client;
    }

    private TimeValue startUpdater() {
        configFileUpdater = new ConfigFileUpdater();

        if (scheduledCancellable != null) {
            scheduledCancellable.cancel();
        }

        final TimeValue interval =
                clusterService.state().getMetadata().settings().getAsTime(FLUSH_INTERVAL_SETTING.getKey(), flushInterval);
        if (interval.millis() < 0) {
            if (logger.isDebugEnabled()) {
                logger.debug("ConfigFileUpdater is not scheduled.");
            }
        } else {
            scheduledCancellable = threadPool.schedule(configFileUpdater, interval, Names.SAME);
            if (logger.isDebugEnabled()) {
                logger.debug("Scheduled ConfigFileUpdater with {}", interval);
            }
        }
        return interval;
    }

    @Override
    protected void doStart() {
        if (logger.isDebugEnabled()) {
            logger.debug("Starting ConfigSyncService");
        }

        if (fileUpdaterEnabled) {
            clusterService.addLifecycleListener(new LifecycleListener() {
                @Override
                public void afterStart() {
                    waitForClusterReady();
                }
            });
        }
    }

    private void waitForClusterReady() {
        client.admin().cluster().prepareHealth().setWaitForGreenStatus().execute(wrap(res -> {
            if (res.isTimedOut()) {
                logger.warn("Cluster service was timeouted.");
            }
            checkIfIndexExists(wrap(response -> {
                final TimeValue time = startUpdater();
                if (time.millis() >= 0) {
                    logger.info("ConfigFileUpdater is started at {} intervals.", time);
                }
            }, e -> {
                logger.warn("Could not create {}. Retrying to start it.", index, e);
                threadPool.schedule(() -> waitForClusterReady(), TimeValue.timeValueSeconds(15), Names.GENERIC);
            }));
        }, e -> {
            logger.warn("Could not start ConfigFileUpdater. Retrying to start it.", e);
            threadPool.schedule(() -> waitForClusterReady(), TimeValue.timeValueSeconds(15), Names.GENERIC);
        }));
    }

    private void checkIfIndexExists(final ActionListener<ActionResponse> listener) {
        client().admin().indices().prepareExists(index).execute(wrap(response -> {
            if (response.isExists()) {
                if (logger.isDebugEnabled()) {
                    logger.debug("{} exists.", index);
                }
                listener.onResponse(response);
            } else {
                createIndex(listener);
            }
        }, e -> {
            if (e instanceof IndexNotFoundException) {
                createIndex(listener);
            } else {
                listener.onFailure(e);
            }
        }));
    }

    private void createIndex(final ActionListener<ActionResponse> listener) {
        try (final Reader in = new InputStreamReader(ConfigSyncService.class.getClassLoader().getResourceAsStream(FILE_MAPPING_JSON),
                StandardCharsets.UTF_8)) {
            final String source = Streams.copyToString(in);
            final XContentBuilder settingsBuilder = XContentFactory.jsonBuilder()//
                    .startObject()//
                    .startObject("index")//
                    .field("number_of_shards", 1)//
                    .field("number_of_replicas", 0)//
                    .field("auto_expand_replicas", "0-all")//
                    .endObject()//
                    .endObject();
            client().admin().indices().prepareCreate(index).setSettings(settingsBuilder)
                    .setMapping(source)
                    .execute(wrap(response -> waitForIndex(listener), listener::onFailure));
        } catch (final IOException e) {
            listener.onFailure(e);
        }
    }

    private void waitForIndex(final ActionListener<ActionResponse> listener) {
        client.admin().cluster().prepareHealth(index).setWaitForYellowStatus().execute(wrap(response -> {
            listener.onResponse(response);
            migrateFromOldIndex();
        }, listener::onFailure));
    }

    private void migrateFromOldIndex() {
        final String oldIndex = ".configsync";
        client().admin().indices().prepareExists(oldIndex).execute(wrap(response -> {
            if (response.isExists()) {
                if (logger.isDebugEnabled()) {
                    logger.debug("{} exists.", oldIndex);
                }
                copyIndex(client(), oldIndex, index);
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("{} does not exist.", oldIndex);
                }
            }
        }, e -> {
            if (e instanceof IndexNotFoundException) {
                if (logger.isDebugEnabled()) {
                    logger.debug("{} does not exist.", oldIndex);
                }
            } else {
                logger.debug("Failed to check if {} exists.", oldIndex, e);
            }
        }));
    }

    private static void copyIndex(final Client client, final String oldIndex, final String newIndex) {
        logger.info("Copy from {} to {}", oldIndex, newIndex);
        final String timeout = "1m";
        SearchResponse searchResponse =
                client.prepareSearch(oldIndex).setScroll(timeout).setQuery(QueryBuilders.matchAllQuery()).execute().actionGet(timeout);
        String scrollId = searchResponse.getScrollId();
        try {
            while (scrollId != null) {
                final SearchHits searchHits = searchResponse.getHits();
                final SearchHit[] hits = searchHits.getHits();
                if (hits.length == 0) {
                    break;
                }

                final BulkRequestBuilder bulkRequest = client.prepareBulk();
                for (final SearchHit hit : hits) {
                    bulkRequest.add(
                            client.prepareIndex().setIndex(newIndex).setId(hit.getId()).setSource(hit.getSourceRef(), XContentType.JSON));
                    logger.info("Copying id:{}", hit.getId());
                }

                BulkResponse bulkResponse = bulkRequest.execute().actionGet(timeout);
                if (bulkResponse.hasFailures()) {
                    logger.warn(bulkResponse.buildFailureMessage());
                }

                searchResponse = client.prepareSearchScroll(scrollId).setScroll(timeout).execute().actionGet(timeout);
                if (!scrollId.equals(searchResponse.getScrollId())) {
                    client.prepareClearScroll().addScrollId(scrollId)
                            .execute(wrap(res -> {}, e -> logger.warn("Failed to clear the scroll context.", e)));
                }
                scrollId = searchResponse.getScrollId();
            }
        } catch (Exception e) {
            logger.warn("Failed to copy from {} to {}.", oldIndex, newIndex, e);
        } finally {
            if (scrollId != null) {
                client.prepareClearScroll().addScrollId(scrollId)
                        .execute(wrap(res -> {}, e -> logger.warn("Failed to clear the scroll context.", e)));
            }
        }
    }

    @Override
    protected void doStop() {
        if (configFileUpdater != null) {
            configFileUpdater.terminate();
        }
    }

    @Override
    protected void doClose() {
    }

    public void store(final String path, final byte[] contentArray, final ActionListener<IndexResponse> listener) {
        checkIfIndexExists(wrap(response -> {
            try {
                final String id = getId(path);
                final XContentBuilder builder = JsonXContent.contentBuilder();
                builder.startObject();
                builder.field(PATH, path);
                builder.field(CONTENT, contentArray);
                builder.field(TIMESTAMP, new Date());
                builder.endObject();
                client().prepareIndex(index).setId(id).setSource(builder).setRefreshPolicy(RefreshPolicy.IMMEDIATE).execute(listener);
            } catch (final IOException e) {
                throw new OpenSearchException("Failed to register " + path, e);
            }
        }, listener::onFailure));
    }

    public void getPaths(final int from, final int size, final String[] fields, final String sortField, final String sortOrder,
            final ActionListener<List<Object>> listener) {
        checkIfIndexExists(wrap(res -> {
            final boolean hasFields = !(fields == null || fields.length == 0);
            client().prepareSearch(index).setSize(size).setFrom(from)
                    .setFetchSource(hasFields ? fields : new String[] { PATH }, null)
                    .addSort(sortField, SortOrder.DESC.toString().equalsIgnoreCase(sortOrder) ? SortOrder.DESC : SortOrder.ASC)
                    .execute(wrap(response -> {
                        final List<Object> objList = new ArrayList<>();
                        for (final SearchHit hit : response.getHits().getHits()) {
                            if (hasFields) {
                                final Map<String, Object> objMap = new HashMap<>();
                                for (final String field : fields) {
                                    objMap.put(field, hit.getSourceAsMap().get(field));
                                }
                                objList.add(objMap);
                            } else {
                                objList.add(hit.getSourceAsMap().get(PATH));
                            }
                        }
                        listener.onResponse(objList);
                    }, listener::onFailure));
        }, listener::onFailure));
    }

    private String getId(final String path) {
        return Base64.encodeBase64URLSafeString(path.getBytes(StandardCharsets.UTF_8));
    }

    public void resetSync(final ActionListener<ConfigResetSyncResponse> listener) {
        checkIfIndexExists(wrap(response -> {
            final ClusterState state = clusterService.state();
            final DiscoveryNodes nodes = state.nodes();
            final Iterator<DiscoveryNode> nodesIt = nodes.getDataNodes().values().iterator();
            resetSync(nodesIt, listener);
        }, listener::onFailure));
    }

    private void resetSync(final Iterator<DiscoveryNode> nodesIt, final ActionListener<ConfigResetSyncResponse> listener) {
        if (!nodesIt.hasNext()) {
            listener.onResponse(new ConfigResetSyncResponse(true));
        } else {
            final DiscoveryNode node = nodesIt.next();
            transportService.sendRequest(node, ACTION_CONFIG_RESET, new ResetSyncRequest(),
                    new TransportResponseHandler<ResetSyncResponse>() {

                        @Override
                        public ResetSyncResponse read(StreamInput in) throws IOException {
                            return new ResetSyncResponse(in);
                        }

                        @Override
                        public void handleResponse(final ResetSyncResponse response) {
                            resetSync(nodesIt, listener);
                        }

                        @Override
                        public void handleException(final TransportException exp) {
                            listener.onFailure(exp);
                        }

                        @Override
                        public String executor() {
                            return ThreadPool.Names.GENERIC;
                        }
                    });
        }
    }

    private void restartUpdater(final ActionListener<ActionResponse> listener) {
        if (logger.isDebugEnabled()) {
            logger.debug("Restarting ConfigFileUpdater...");
        }
        try {
            if (configFileUpdater != null) {
                configFileUpdater.terminate();
            }
            checkIfIndexExists(wrap(response -> {
                final TimeValue time = startUpdater();
                if (time.millis() >= 0) {
                    logger.info("ConfigFileUpdater is started at {} intervals.", time);
                }
                listener.onResponse(response);
            }, e -> {
                logger.error("Failed to restart ConfigFileUpdater.", e);
                listener.onFailure(e);
            }));
        } catch (final Exception e) {
            listener.onFailure(e);
        }
    }

    public void flush(final ActionListener<ConfigFileFlushResponse> listener) {
        checkIfIndexExists(wrap(response -> {
            final ClusterState state = clusterService.state();
            final DiscoveryNodes nodes = state.nodes();
            final Iterator<DiscoveryNode> nodesIt = nodes.getDataNodes().values().iterator();
            flushOnNode(nodesIt, listener);
        }, listener::onFailure));
    }

    private void flushOnNode(final Iterator<DiscoveryNode> nodesIt, final ActionListener<ConfigFileFlushResponse> listener) {
        if (!nodesIt.hasNext()) {
            listener.onResponse(new ConfigFileFlushResponse(true));
        } else {
            final DiscoveryNode node = nodesIt.next();
            transportService.sendRequest(node, ACTION_CONFIG_FLUSH, new FileFlushRequest(),
                    new TransportResponseHandler<FileFlushResponse>() {

                        @Override
                        public FileFlushResponse read(StreamInput in) throws IOException {
                            return new FileFlushResponse(in);
                        }

                        @Override
                        public void handleResponse(final FileFlushResponse response) {
                            flushOnNode(nodesIt, listener);
                        }

                        @Override
                        public void handleException(final TransportException exp) {
                            listener.onFailure(exp);
                        }

                        @Override
                        public String executor() {
                            return ThreadPool.Names.GENERIC;
                        }
                    });
        }
    }

    public void getContent(final String path, final ActionListener<byte[]> listener) {
        checkIfIndexExists(wrap(res -> {
            client().prepareGet(index, getId(path)).execute(wrap(response -> {
                if (response.isExists()) {
                    final byte[] configContent = Base64.decodeBase64((String) response.getSource().get(ConfigSyncService.CONTENT));
                    listener.onResponse(configContent);
                } else {
                    listener.onResponse(null);
                }
            }, listener::onFailure));
        }, listener::onFailure));
    }

    public void delete(final String path, final ActionListener<DeleteResponse> listener) {
        checkIfIndexExists(
                wrap(response -> client().prepareDelete(index, getId(path)).setRefreshPolicy(RefreshPolicy.IMMEDIATE).execute(listener),
                        listener::onFailure));
    }

    public void waitForStatus(final String waitForStatus, final String timeout, final ActionListener<ClusterHealthResponse> listener) {
        try {
            client.admin().cluster().prepareHealth(index).setWaitForStatus(ClusterHealthStatus.fromString(waitForStatus))
                    .setTimeout(timeout).execute(listener);
        } catch (final Exception e) {
            listener.onFailure(e);
        }
    }

    private void updateConfigFile(final Map<String, Object> source) {
        try {
            final Date timestamp = getTimestamp(source.get(TIMESTAMP));
            final String path = (String) source.get(PATH);
            final Path filePath = Paths.get(configPath, path.replace("..", ""));
            if (logger.isDebugEnabled()) {
                logger.debug("Checking {}", filePath);
            }
            final Exception e = AccessController.doPrivileged((PrivilegedAction<Exception>) () -> {
                try {
                    if (logger.isDebugEnabled()) {
                        logger.debug("timestamp(index): {}", timestamp.getTime());
                        if (Files.exists(filePath)) {
                            logger.debug("timestamp(file):  {}", Files.getLastModifiedTime(filePath).toMillis());
                        }
                    }
                    if (!Files.exists(filePath) || Files.getLastModifiedTime(filePath).toMillis() < timestamp.getTime()) {
                        final String content = (String) source.get(CONTENT);
                        final File parentFile = filePath.toFile().getParentFile();
                        if (!parentFile.exists() && !parentFile.mkdirs()) {
                            logger.warn("Failed to create " + parentFile.getAbsolutePath());
                        }
                        final String absolutePath = filePath.toFile().getAbsolutePath();
                        decodeToFile(content, absolutePath);
                        logger.info("Updated " + absolutePath);
                    }
                } catch (final Exception e1) {
                    return e1;
                }
                return null;
            });
            if (e != null) {
                throw e;
            }
        } catch (final Exception e) {
            logger.warn("Failed to update " + source.get(PATH), e);
        }
    }

    private Date getTimestamp(final Object value) throws ParseException {
        if (value instanceof Date) {
            return (Date) value;
        } else if (value instanceof Number) {
            return new Date(((Number) value).longValue());
        } else {
            final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            return sdf.parse(value.toString());
        }
    }

    class ConfigFileUpdater implements Runnable {

        ConfigFileWriter writer = new ConfigFileWriter();

        @Override
        public void run() {
            if (writer.terminated.get()) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Terminated {}", this);
                }
                return;
            }

            if (logger.isDebugEnabled()) {
                logger.debug("Processing ConfigFileUpdater.");
            }

            writer.execute(wrap(response -> startUpdater(), e -> {
                logger.error("Failed to process ConfigFileUpdater.", e);
                startUpdater();
            }));
        }

        public void terminate() {
            writer.terminate();
        }
    }

    class ConfigFileWriter implements ActionListener<SearchResponse> {

        private final AtomicBoolean terminated = new AtomicBoolean(false);

        private ActionListener<Void> listener;

        public void execute(final ActionListener<Void> listener) {
            this.listener = listener;

            final Date now = new Date();
            final QueryBuilder queryBuilder =
                    QueryBuilders.boolQuery().filter(QueryBuilders.rangeQuery(TIMESTAMP).from(lastChecked.getTime()));
            lastChecked = now;
            client().prepareSearch(index).setQuery(queryBuilder).setScroll(scrollForUpdate).setSize(sizeForUpdate)
                    .execute(this);
        }

        public void terminate() {
            terminated.set(true);
        }

        @Override
        public void onResponse(final SearchResponse response) {
            if (terminated.get()) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Terminated {}", this);
                }
                listener.onFailure(new OpenSearchException("Config Writing process was terminated."));
                return;
            }

            final SearchHits searchHits = response.getHits();
            final SearchHit[] hits = searchHits.getHits();
            if (hits.length == 0) {
                listener.onResponse(null);
            } else {
                for (final SearchHit hit : hits) {
                    final Map<String, Object> source = hit.getSourceAsMap();
                    updateConfigFile(source);
                }
                final String scrollId = response.getScrollId();
                client().prepareSearchScroll(scrollId).setScroll(scrollForUpdate).execute(this);
            }
        }

        @Override
        public void onFailure(final Exception e) {
            listener.onFailure(e);
        }
    }

    class ConfigFileFlushRequestHandler implements TransportRequestHandler<FileFlushRequest> {

        @Override
        public void messageReceived(final FileFlushRequest request, final TransportChannel channel, final Task task) throws Exception {
            new ConfigFileWriter().execute(wrap(response -> {
                try {
                    channel.sendResponse(new FileFlushResponse(true));
                } catch (final IOException e) {
                    throw new OpenSearchException("Failed to write a response.", e);
                }
            }, e -> {
                logger.error("Failed to flush config files.", e);
                try {
                    channel.sendResponse(e);
                } catch (final IOException e1) {
                    throw new OpenSearchException("Failed to write a response.", e1);
                }
            }));
        }
    }

    public static class FileFlushRequest extends TransportRequest {
        FileFlushRequest() {
            super();
        }

        FileFlushRequest(final StreamInput in) throws IOException {
            super(in);
        }
    }

    private static class FileFlushResponse extends AcknowledgedResponse {

        FileFlushResponse(final StreamInput in) throws IOException {
            super(in);
        }

        FileFlushResponse(final boolean acknowledged) {
            super(acknowledged);
        }
    }

    class ConfigSyncResetRequestHandler implements TransportRequestHandler<ResetSyncRequest> {

        @Override
        public void messageReceived(final ResetSyncRequest request, final TransportChannel channel, final Task task) throws Exception {
            restartUpdater(wrap(response -> {
                try {
                    channel.sendResponse(new ResetSyncResponse(true));
                } catch (final IOException e) {
                    throw new OpenSearchException(e);
                }
            }, e -> {
                try {
                    channel.sendResponse(e);
                } catch (final IOException ioe) {
                    logger.error("Failed to send Reset response.", ioe);
                }
            }));
        }
    }

    public static class ResetSyncRequest extends TransportRequest {
        ResetSyncRequest() {
            super();
        }

        ResetSyncRequest(final StreamInput in) throws IOException {
            super(in);
        }
    }

    private static class ResetSyncResponse extends AcknowledgedResponse {

        ResetSyncResponse(final StreamInput in) throws IOException {
            super(in);
        }

        ResetSyncResponse(final boolean acknowledged) {
            super(acknowledged);
        }
    }

    private static void decodeToFile(final String dataToDecode, final String filename) throws java.io.IOException {
        try (final Base64OutputStream os = new Base64OutputStream(new FileOutputStream(filename), false)) {
            os.write(dataToDecode.getBytes(StandardCharsets.UTF_8));
        }
    }
}
