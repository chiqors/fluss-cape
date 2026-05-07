package org.gnuhpc.fluss.cape.http.it;

import org.gnuhpc.fluss.cape.http.codec.FlussJsonCodec;
import org.junit.jupiter.api.BeforeAll;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.Map;

abstract class HttpItBase {
    protected static final String BASE_URL = config("http.compat.base-url", "HTTP_COMPAT_BASE_URL", "http://127.0.0.1:18080");
    protected static final String AUTH_TOKEN = config("http.compat.auth-token", "HTTP_COMPAT_AUTH_TOKEN", "");

    @BeforeAll
    static void validateEnvironment() {
        if (BASE_URL == null || BASE_URL.isBlank()) {
            throw new IllegalStateException("Missing HTTP compat base URL");
        }
    }

    protected HttpURLConnection request(String method, String path, String body) throws Exception {
        return request(method, path, body, true);
    }

    protected HttpURLConnection requestWithoutAuth(String method, String path, String body) throws Exception {
        return request(method, path, body, false);
    }

    protected HttpURLConnection requestWithBearerToken(String method, String path, String body, String token) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(BASE_URL + path).openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        if (body != null) {
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        return conn;
    }

    private HttpURLConnection request(String method, String path, String body, boolean withAuth) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(BASE_URL + path).openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Content-Type", "application/json");
        if (withAuth && !AUTH_TOKEN.isBlank()) {
            conn.setRequestProperty("Authorization", "Bearer " + AUTH_TOKEN);
        }
        if (body != null) {
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        return conn;
    }

    protected Map<String, Object> postJson(String path, String body) throws Exception {
        return readJsonResponse(request("POST", path, body));
    }

    protected Map<String, Object> getJson(String path) throws Exception {
        return readJsonResponse(request("GET", path, null));
    }

    protected Map<String, Object> deleteJson(String path) throws Exception {
        return readJsonResponse(request("DELETE", path, null));
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> data(Map<String, Object> root) {
        Object d = root.get("data");
        if (!(d instanceof Map)) {
            throw new AssertionError("response.data is not a JSON object: " + root);
        }
        return (Map<String, Object>) d;
    }

    protected String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    protected void assertStatus(HttpURLConnection conn, int expected) throws Exception {
        int actual = conn.getResponseCode();
        if (actual != expected) {
            throw new AssertionError("expected status " + expected + " but was " + actual + ": " + readBody(conn));
        }
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> readJsonResponse(HttpURLConnection conn) throws Exception {
        int status = conn.getResponseCode();
        String body = readBody(conn);
        if (status >= 400) {
            throw new AssertionError("HTTP " + status + ": " + body);
        }
        Object parsed = FlussJsonCodec.parse(body);
        if (!(parsed instanceof Map)) {
            throw new AssertionError("response is not a JSON object: " + body);
        }
        return (Map<String, Object>) parsed;
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> readErrorJsonResponse(HttpURLConnection conn, int expectedStatus) throws Exception {
        int actual = conn.getResponseCode();
        String body = readBody(conn);
        if (actual != expectedStatus) {
            throw new AssertionError("expected status " + expectedStatus + " but was " + actual + ": " + body);
        }
        Object parsed = FlussJsonCodec.parse(body);
        if (!(parsed instanceof Map)) {
            throw new AssertionError("response is not a JSON object: " + body);
        }
        return (Map<String, Object>) parsed;
    }

    protected String readBody(HttpURLConnection conn) throws Exception {
        InputStream in = conn.getResponseCode() >= 400 ? conn.getErrorStream() : conn.getInputStream();
        return read(in);
    }

    protected String read(InputStream in) throws Exception {
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

    protected String env(String name) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("Missing required env var: " + name);
        }
        return v;
    }

    protected static String config(String property, String env, String defaultValue) {
        String fromProperty = System.getProperty(property);
        if (fromProperty != null && !fromProperty.isBlank()) {
            return fromProperty;
        }
        String fromEnv = System.getenv(env);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        return defaultValue;
    }

}
