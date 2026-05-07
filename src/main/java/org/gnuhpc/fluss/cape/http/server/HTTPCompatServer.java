package org.gnuhpc.fluss.cape.http.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.fluss.client.Connection;
import org.apache.fluss.client.admin.Admin;
import org.gnuhpc.fluss.cape.common.server.ProtocolServer;
import org.gnuhpc.fluss.cape.http.codec.FlussJsonCodec;
import org.gnuhpc.fluss.cape.http.config.HTTPCompatConfig;
import org.gnuhpc.fluss.cape.http.model.ApiException;
import org.gnuhpc.fluss.cape.http.model.ApiResponse;
import org.gnuhpc.fluss.cape.http.model.RequestAuth;
import org.gnuhpc.fluss.cape.http.service.HTTPCompatApiService;
import org.gnuhpc.fluss.cape.http.HttpApiConst;
import org.gnuhpc.fluss.cape.http.subscription.HTTPSubscriptionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HTTPCompatServer implements ProtocolServer {
    private static final Logger LOG = LoggerFactory.getLogger(HTTPCompatServer.class);

    private final HTTPCompatConfig config;
    private final HTTPCompatApiService service;
    private final HTTPSubscriptionManager subscriptionManager;
    private HttpServer server;

    public HTTPCompatServer(HTTPCompatConfig config, Connection connection, Admin admin) {
        this.config = config;
        this.subscriptionManager = new HTTPSubscriptionManager(
                config.getSubscriptionMaxCount(), config.getSubscriptionIdleTimeoutMs());
        this.service = new HTTPCompatApiService(connection, admin, subscriptionManager);
    }

    @Override
    public void start() throws Exception {
        this.server = HttpServer.create(new InetSocketAddress(config.getBindAddress(), config.getBindPort()), 0);
        this.server.createContext("/", new Handler());
        this.server.setExecutor(null);
        this.server.start();
        LOG.info("HTTPCompatServer started on {}:{}", config.getBindAddress(), getBoundPort());
    }

    @Override
    public void close() {
        if (server != null) {
            server.stop(0);
        }
        subscriptionManager.close();
        LOG.info("HTTPCompatServer stopped");
    }

    @Override
    public int getBoundPort() {
        if (server == null || server.getAddress() == null) {
            return config.getBindPort();
        }
        return server.getAddress().getPort();
    }

    private final class Handler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                route(exchange);
            } catch (ApiException e) {
                writeJson(exchange, e.getStatus(), ApiResponse.error(e.getCode(), e.getMessage()));
            } catch (Exception e) {
                LOG.error("HTTPCompat request failed", e);
                writeJson(exchange, 500, ApiResponse.error("internal_error", e.getMessage()));
            } finally {
                exchange.close();
            }
        }

        private void route(HttpExchange exchange) throws Exception {
            String method = exchange.getRequestMethod();
            URI uri = exchange.getRequestURI();
            String path = uri.getPath();
            Map<String, String> query = parseQuery(uri.getRawQuery());

            if (HttpApiConst.HEALTH.equals(path)) {
                writeJson(exchange, 200, ApiResponse.ok(mapOf(HttpApiConst.STATUS, HttpApiConst.OK)));
                return;
            }
            if (HttpApiConst.READY.equals(path)) {
                writeJson(exchange, 200, ApiResponse.ok(mapOf(HttpApiConst.STATUS, HttpApiConst.READY_VALUE)));
                return;
            }

            ensureAuth(exchange);

            String[] seg = splitPath(path);
            if (seg.length < 2 || !"api".equals(seg[0]) || !"v1".equals(seg[1])) {
                throw new ApiException(404, "not_found", "Not found: " + path);
            }

            // /api/v1/databases
            if (seg.length == 3 && HttpApiConst.DATABASES.equals(seg[2])) {
                if ("GET".equals(method)) {
                    writeJson(exchange, 200, ApiResponse.ok(service.listDatabases()));
                    return;
                }
                if ("POST".equals(method)) {
                    ensureAdminWriteEnabled();
                    Map<String, Object> body = readBodyJson(exchange);
                    String name = requireString(body, HttpApiConst.NAME);
                    boolean ignore = body.get(HttpApiConst.IGNORE_IF_EXISTS) == null || Boolean.TRUE.equals(body.get(HttpApiConst.IGNORE_IF_EXISTS));
                    Map<String, Object> data = service.createDatabase(name, ignore, parseAuth(body));
                    writeJson(exchange, 201, ApiResponse.ok(data));
                    return;
                }
            }

            // /api/v1/databases/{db}
            if (seg.length == 4 && HttpApiConst.DATABASES.equals(seg[2])) {
                String db = decode(seg[3]);
                if ("GET".equals(method)) {
                    writeJson(exchange, 200, ApiResponse.ok(service.getDatabase(db)));
                    return;
                }
            }

            // /api/v1/databases/{db}/tables
            if (seg.length == 5 && HttpApiConst.DATABASES.equals(seg[2]) && HttpApiConst.TABLES.equals(seg[4])) {
                String db = decode(seg[3]);
                if ("GET".equals(method)) {
                    writeJson(exchange, 200, ApiResponse.ok(service.listTables(db)));
                    return;
                }
                if ("POST".equals(method)) {
                    ensureAdminWriteEnabled();
                    Map<String, Object> body = readBodyJson(exchange);
                    writeJson(exchange, 201, ApiResponse.ok(service.createTable(db, body, parseAuth(body))));
                    return;
                }
            }

            // /api/v1/databases/{db}/tables/{table}
            if (seg.length == 6 && HttpApiConst.DATABASES.equals(seg[2]) && HttpApiConst.TABLES.equals(seg[4])) {
                String db = decode(seg[3]);
                String table = decode(seg[5]);
                if ("GET".equals(method)) {
                    writeJson(exchange, 200, ApiResponse.ok(service.getTable(db, table)));
                    return;
                }
            }

            // /api/v1/databases/{db}/tables/{table}/rows
            if (seg.length == 7 && HttpApiConst.DATABASES.equals(seg[2]) && HttpApiConst.TABLES.equals(seg[4]) && HttpApiConst.ROWS.equals(seg[6])) {
                String db = decode(seg[3]);
                String table = decode(seg[5]);
                if ("POST".equals(method)) {
                    ensureDataWriteEnabled();
                    Map<String, Object> body = readBodyJson(exchange);
                    writeJson(exchange, 200, ApiResponse.ok(service.upsertRows(db, table, body, parseAuth(body))));
                    return;
                }
                if ("GET".equals(method)) {
                    writeJson(exchange, 200, ApiResponse.ok(service.getRowByPrimaryKey(db, table, query)));
                    return;
                }
                if ("DELETE".equals(method)) {
                    ensureDataWriteEnabled();
                    boolean flush = !query.containsKey(HttpApiConst.FLUSH) || Boolean.parseBoolean(query.get(HttpApiConst.FLUSH));
                    writeJson(exchange, 200, ApiResponse.ok(service.deleteRowByPrimaryKey(db, table, query, flush)));
                    return;
                }
            }

            // /api/v1/databases/{db}/tables/{table}/records
            if (seg.length == 7 && HttpApiConst.DATABASES.equals(seg[2]) && HttpApiConst.TABLES.equals(seg[4]) && HttpApiConst.RECORDS.equals(seg[6])) {
                String db = decode(seg[3]);
                String table = decode(seg[5]);
                if ("POST".equals(method)) {
                    ensureDataWriteEnabled();
                    Map<String, Object> body = readBodyJson(exchange);
                    writeJson(exchange, 200, ApiResponse.ok(service.appendRecords(db, table, body, parseAuth(body))));
                    return;
                }
                if ("GET".equals(method)) {
                    int bucket = parseInt(query.get("bucket"), 0);
                    long offset = parseLong(query.get(HttpApiConst.OFFSET), 0L);
                    int limit = normalizeLimit(parseInt(query.get(HttpApiConst.LIMIT), config.getDefaultLimit()));
                    long timeoutMs = parseLong(query.get(HttpApiConst.TIMEOUT_MS), 3000L);
                    writeJson(exchange, 200, ApiResponse.ok(service.readRecords(db, table, bucket, offset, limit, timeoutMs)));
                    return;
                }
            }

            // /api/v1/databases/{db}/tables/{table}/subscriptions
            if (seg.length == 7 && HttpApiConst.DATABASES.equals(seg[2]) && HttpApiConst.TABLES.equals(seg[4]) && HttpApiConst.SUBSCRIPTIONS.equals(seg[6])) {
                if (!"POST".equals(method)) {
                    throw new ApiException(405, "method_not_allowed", "Method not allowed");
                }
                String db = decode(seg[3]);
                String table = decode(seg[5]);
                Map<String, Object> body = readBodyJson(exchange);

                @SuppressWarnings("unchecked")
                List<Object> bucketsRaw = body.get(HttpApiConst.BUCKETS) instanceof List ? (List<Object>) body.get(HttpApiConst.BUCKETS) : Collections.emptyList();
                List<Integer> buckets = new ArrayList<>();
                for (Object v : bucketsRaw) buckets.add(((Number) v).intValue());
                if (buckets.isEmpty()) {
                    buckets.add(0);
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> start = body.get(HttpApiConst.START) instanceof Map ? (Map<String, Object>) body.get(HttpApiConst.START) : new LinkedHashMap<>();
                String mode = start.get(HttpApiConst.MODE) == null ? "earliest" : String.valueOf(start.get(HttpApiConst.MODE));

                Map<Integer, Long> offsets = new HashMap<>();
                if (HttpApiConst.OFFSET.equals(mode) && start.get(HttpApiConst.OFFSETS) instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> offs = (Map<String, Object>) start.get(HttpApiConst.OFFSETS);
                    for (Map.Entry<String, Object> e : offs.entrySet()) {
                        offsets.put(Integer.parseInt(e.getKey()), ((Number) e.getValue()).longValue());
                    }
                }

                writeJson(exchange, 201, ApiResponse.ok(service.createSubscription(db, table, buckets, mode, offsets, body, parseAuth(body))));
                return;
            }

            // /api/v1/databases/{db}/tables/{table}/subscriptions/{id}/records
            if (seg.length == 9 && HttpApiConst.DATABASES.equals(seg[2]) && HttpApiConst.TABLES.equals(seg[4])
                    && HttpApiConst.SUBSCRIPTIONS.equals(seg[6]) && HttpApiConst.RECORDS.equals(seg[8])) {
                String db = decode(seg[3]);
                String table = decode(seg[5]);
                String subId = decode(seg[7]);
                int limit = normalizeLimit(parseInt(query.get(HttpApiConst.LIMIT), config.getDefaultLimit()));
                long timeoutMs = parseLong(query.get(HttpApiConst.TIMEOUT_MS), 30000L);
                writeJson(exchange, 200, ApiResponse.ok(service.pollSubscription(db, table, subId, limit, timeoutMs)));
                return;
            }

            // /api/v1/databases/{db}/tables/{table}/subscriptions/{id}
            if (seg.length == 8 && HttpApiConst.DATABASES.equals(seg[2]) && HttpApiConst.TABLES.equals(seg[4]) && HttpApiConst.SUBSCRIPTIONS.equals(seg[6])) {
                if (!"DELETE".equals(method)) {
                    throw new ApiException(405, "method_not_allowed", "Method not allowed");
                }
                String db = decode(seg[3]);
                String table = decode(seg[5]);
                String subId = decode(seg[7]);
                writeJson(exchange, 200, ApiResponse.ok(service.deleteSubscription(db, table, subId)));
                return;
            }

            throw new ApiException(404, "not_found", "Not found: " + path);
        }

        private void ensureAuth(HttpExchange exchange) {
            String required = config.getAuthToken();
            if (required == null || required.isBlank()) {
                return;
            }
            String header = exchange.getRequestHeaders().getFirst(HttpApiConst.AUTHORIZATION);
            String expected = HttpApiConst.BEARER_PREFIX + required;
            if (header == null || !expected.equals(header)) {
                throw new ApiException(401, "unauthorized", "Unauthorized");
            }
        }

        private void ensureAdminWriteEnabled() {
            if (!config.isAllowAdminWrite()) {
                throw new ApiException(403, "forbidden", "Admin write is disabled");
            }
        }

        private void ensureDataWriteEnabled() {
            if (!config.isAllowDataWrite()) {
                throw new ApiException(403, "forbidden", "Data write is disabled");
            }
        }

        private int normalizeLimit(int limit) {
            int l = Math.max(1, limit);
            return Math.min(l, config.getMaxLimit());
        }

        private Map<String, Object> readBodyJson(HttpExchange exchange) throws IOException {
            byte[] bytes = readRequestBody(exchange.getRequestBody(), config.getMaxRequestBytes());
            if (bytes.length == 0) {
                return new LinkedHashMap<>();
            }
            Object parsed;
            try {
                parsed = FlussJsonCodec.parse(bytes);
            } catch (Exception e) {
                throw new ApiException(400, "invalid_json", "Invalid JSON request body");
            }
            if (!(parsed instanceof Map)) {
                throw new ApiException(400, "invalid_json", "JSON body must be object");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) parsed;
            return map;
        }

        private RequestAuth parseAuth(Map<String, Object> body) {
            Object authObj = body.get(HttpApiConst.AUTH);
            if (!(authObj instanceof Map)) {
                return new RequestAuth(null, null);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> auth = (Map<String, Object>) authObj;
            return new RequestAuth(
                    auth.get(HttpApiConst.USERNAME) == null ? null : String.valueOf(auth.get(HttpApiConst.USERNAME)),
                    auth.get(HttpApiConst.PASSWORD) == null ? null : String.valueOf(auth.get(HttpApiConst.PASSWORD)));
        }

        private byte[] readRequestBody(InputStream in, int maxBytes) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int total = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > maxBytes) {
                    throw new ApiException(413, "payload_too_large", "request body too large");
                }
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }

        private void writeJson(HttpExchange exchange, int status, Map<String, Object> body) throws IOException {
            byte[] bytes = FlussJsonCodec.toJsonBytes(body);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        private String[] splitPath(String path) {
            if (path == null || path.isEmpty() || "/".equals(path)) {
                return new String[0];
            }
            String p = path.startsWith("/") ? path.substring(1) : path;
            if (p.endsWith("/")) {
                p = p.substring(0, p.length() - 1);
            }
            return p.isEmpty() ? new String[0] : p.split("/");
        }

        private Map<String, String> parseQuery(String rawQuery) {
            if (rawQuery == null || rawQuery.isEmpty()) {
                return new HashMap<>();
            }
            Map<String, String> q = new HashMap<>();
            for (String pair : rawQuery.split("&")) {
                if (pair.isEmpty()) continue;
                int eq = pair.indexOf('=');
                String k = eq < 0 ? pair : pair.substring(0, eq);
                String v = eq < 0 ? "" : pair.substring(eq + 1);
                q.put(decode(k), decode(v));
            }
            return q;
        }

        private String decode(String s) {
            try {
                return URLDecoder.decode(s, StandardCharsets.UTF_8.name());
            } catch (Exception e) {
                return s;
            }
        }

        private String requireString(Map<String, Object> m, String key) {
            Object v = m.get(key);
            if (v == null) throw new ApiException(400, "invalid_argument", key + " is required");
            return String.valueOf(v);
        }

        private int parseInt(String s, int defVal) {
            if (s == null || s.isBlank()) return defVal;
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                throw new ApiException(400, "invalid_argument", "invalid integer: " + s);
            }
        }

        private long parseLong(String s, long defVal) {
            if (s == null || s.isBlank()) return defVal;
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException e) {
                throw new ApiException(400, "invalid_argument", "invalid long: " + s);
            }
        }

        private Map<String, Object> mapOf(String k, Object v) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put(k, v);
            return m;
        }
    }
}
