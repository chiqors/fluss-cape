package org.gnuhpc.fluss.cape.http;

import org.apache.fluss.client.Connection;
import org.apache.fluss.client.admin.Admin;
import org.apache.fluss.metadata.Schema;
import org.apache.fluss.metadata.TableDescriptor;
import org.apache.fluss.metadata.TableInfo;
import org.apache.fluss.metadata.DatabaseDescriptor;
import org.apache.fluss.metadata.TablePath;
import org.apache.fluss.types.DataTypes;
import org.gnuhpc.fluss.cape.http.config.HTTPCompatConfig;
import org.gnuhpc.fluss.cape.http.server.HTTPCompatServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class HTTPCompatServerTest {
    private HTTPCompatServer server;
    private Admin admin;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void shouldServeHealthAndReady() throws Exception {
        server = newServer(0, false, false, "");
        server.start();
        int port = server.getBoundPort();

        assertContains(get(port, "/health", null), "\"ok\":true");
        assertContains(get(port, "/ready", null), "\"ok\":true");
    }

    @Test
    void shouldRejectMissingAuthWhenEnabled() throws Exception {
        server = newServer(0, true, true, "token-1");
        server.start();
        int port = server.getBoundPort();

        HttpURLConnection conn = post(port, "/api/v1/databases", "{\"name\":\"app\",\"ignoreIfExists\":true}", null);
        assertEquals(401, conn.getResponseCode());
        assertContains(read(conn.getErrorStream()), "unauthorized");
    }

    @Test
    void shouldAcceptAuthHeaderAndBodyAuthPlaceholder() throws Exception {
        server = newServer(0, true, true, "token-1");
        when(serverAdmin().databaseExists("app")).thenReturn(CompletableFuture.completedFuture(false));
        server.start();
        int port = server.getBoundPort();

        HttpURLConnection conn = post(
                port,
                "/api/v1/databases",
                "{\"name\":\"app\",\"ignoreIfExists\":true,\"auth\":{\"username\":\"u\",\"password\":\"p\"}}",
                "Bearer token-1"
        );
        assertEquals(201, conn.getResponseCode());
        assertContains(read(conn.getInputStream()), "\"created\":true");
    }

    @Test
    void shouldRejectWritesWhenDisabled() throws Exception {
        server = newServer(0, false, false, "");
        server.start();
        int port = server.getBoundPort();

        HttpURLConnection conn = post(port, "/api/v1/databases", "{\"name\":\"app\",\"ignoreIfExists\":true}", null);
        assertEquals(403, conn.getResponseCode());
        assertContains(read(conn.getErrorStream()), "forbidden");
    }

    @Test
    void shouldReturn404And405ForUnknownAndUnsupportedRoutes() throws Exception {
        server = newServer(0, true, true, "");
        server.start();
        int port = server.getBoundPort();

        assertEquals(404, getStatus(port, "GET", "/api/v1/unknown", null, null));
        assertEquals(405, getStatus(port, "GET", "/api/v1/databases/db/tables/t/subscriptions/sub-1", null, null));
    }

    @Test
    void shouldRejectInvalidJsonAndOversizeBody() throws Exception {
        server = newServer(0, true, true, "");
        server.start();
        int port = server.getBoundPort();

        HttpURLConnection badJson = post(port, "/api/v1/databases", "{invalid", null);
        assertEquals(400, badJson.getResponseCode());
        assertContains(read(badJson.getErrorStream()), "invalid_json");

        assertEquals(413, invokeReadRequestBodyTooLarge());
    }

    @Test
    void shouldHandleListAndGetRoutes() throws Exception {
        server = newServer(0, true, true, "");
        TablePath tablePath = TablePath.of("app", "t1");
        TableInfo tableInfo = tableInfo(tablePath, true);
        when(serverAdmin().listDatabases()).thenReturn(CompletableFuture.completedFuture(java.util.List.of("default")));
        when(serverAdmin().databaseExists("app")).thenReturn(CompletableFuture.completedFuture(true));
        when(serverAdmin().listTables("app")).thenReturn(CompletableFuture.completedFuture(java.util.List.of("t1")));
        when(serverAdmin().tableExists(tablePath)).thenReturn(CompletableFuture.completedFuture(true));
        when(serverAdmin().getTableInfo(tablePath)).thenReturn(CompletableFuture.completedFuture(tableInfo));
        server.start();
        int port = server.getBoundPort();

        assertContains(get(port, "/api/v1/databases", null), "\"default\"");
        assertContains(get(port, "/api/v1/databases/app", null), "\"database\":\"app\"");
        assertContains(get(port, "/api/v1/databases/app/tables", null), "\"t1\"");
        assertContains(get(port, "/api/v1/databases/app/tables/t1", null), "\"table\":\"t1\"");
    }

    private HTTPCompatServer newServer(int port, boolean allowAdminWrite, boolean allowDataWrite, String authToken) throws Exception {
        Admin admin = Mockito.mock(Admin.class);
        Connection connection = Mockito.mock(Connection.class);
        when(admin.databaseExists(Mockito.anyString())).thenReturn(CompletableFuture.completedFuture(true));
        when(admin.createDatabase(Mockito.anyString(), Mockito.any(DatabaseDescriptor.class), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(admin.tableExists(Mockito.any(TablePath.class))).thenReturn(CompletableFuture.completedFuture(false));
        when(admin.listDatabases()).thenReturn(CompletableFuture.completedFuture(Collections.emptyList()));
        when(admin.listTables(Mockito.anyString())).thenReturn(CompletableFuture.completedFuture(Collections.emptyList()));
        when(connection.getAdmin()).thenReturn(admin);
        this.admin = admin;
        HTTPCompatConfig cfg = new HTTPCompatConfig(
                true,
                "127.0.0.1",
                port,
                authToken,
                allowAdminWrite,
                allowDataWrite,
                1024,
                10,
                100,
                10,
                1000L);
        return new HTTPCompatServer(cfg, connection, admin);
    }

    private Admin serverAdmin() {
        return admin;
    }

    private String get(int port, String path, String auth) throws Exception {
        HttpURLConnection conn = request("GET", port, path, null, auth);
        return read(conn.getInputStream());
    }

    private int getStatus(int port, String method, String path, String body, String auth) throws Exception {
        HttpURLConnection conn = request(method, port, path, body, auth);
        InputStream ok = null;
        InputStream err = null;
        int status = conn.getResponseCode();
        if (status >= 400) {
            err = conn.getErrorStream();
            read(err);
        } else {
            ok = conn.getInputStream();
            read(ok);
        }
        return status;
    }

    private HttpURLConnection post(int port, String path, String body, String auth) throws Exception {
        return request("POST", port, path, body, auth);
    }

    private HttpURLConnection request(String method, int port, String path, String body, String auth) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL("http://127.0.0.1:" + port + path).openConnection();
        conn.setRequestMethod(method);
        if (auth != null) {
            conn.setRequestProperty("Authorization", auth);
        }
        if (body != null) {
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        return conn;
    }

    private String read(InputStream in) throws Exception {
        if (in == null) {
            return "";
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int n;
        while ((n = in.read(buf)) >= 0) {
            out.write(buf, 0, n);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "expected to contain " + expected + " but was " + actual);
    }

    private int invokeReadRequestBodyTooLarge() throws Exception {
        Class<?> handlerClass = Class.forName("org.gnuhpc.fluss.cape.http.server.HTTPCompatServer$Handler");
        Constructor<?> ctor = handlerClass.getDeclaredConstructor(HTTPCompatServer.class);
        ctor.setAccessible(true);
        Object handler = ctor.newInstance(server);
        Method method = handlerClass.getDeclaredMethod("readRequestBody", InputStream.class, int.class);
        method.setAccessible(true);
        try {
            method.invoke(handler, new ByteArrayInputStream(new byte[2048]), 1024);
            fail("expected ApiException");
            return -1;
        } catch (InvocationTargetException e) {
            assertTrue(e.getCause() instanceof org.gnuhpc.fluss.cape.http.model.ApiException);
            return ((org.gnuhpc.fluss.cape.http.model.ApiException) e.getCause()).getStatus();
        }
    }

    private TableInfo tableInfo(TablePath path, boolean primaryKey) {
        Schema.Builder schema = Schema.newBuilder().column("id", DataTypes.BIGINT());
        if (primaryKey) {
            schema.primaryKey("id");
            schema.column("v", DataTypes.STRING());
        } else {
            schema.column("v", DataTypes.STRING());
        }
        TableDescriptor descriptor = TableDescriptor.builder().schema(schema.build()).build().withBucketCount(1);
        return TableInfo.of(path, 1L, 1, descriptor, 1L, 1L);
    }
}
