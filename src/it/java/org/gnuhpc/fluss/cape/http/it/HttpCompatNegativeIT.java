package org.gnuhpc.fluss.cape.http.it;

import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpCompatNegativeIT extends HttpItBase {

    @Test
    void shouldRejectUnauthorizedWhenTokenConfigured() throws Exception {
        if (AUTH_TOKEN.isBlank()) {
            return;
        }
        HttpURLConnection conn = requestWithoutAuth("GET", "/api/v1/databases", null);
        Map<String, Object> error = readErrorJsonResponse(conn, 401);
        assertEquals("unauthorized", ((Map<?, ?>) error.get("error")).get("code"));
    }

    @Test
    void shouldRejectUnknownDatabase() throws Exception {
        HttpURLConnection conn = request("GET", "/api/v1/databases/not-exist", null);
        Map<String, Object> error = readErrorJsonResponse(conn, 404);
        assertEquals("database_not_found", ((Map<?, ?>) error.get("error")).get("code"));
    }

    @Test
    void shouldRejectInvalidJsonBody() throws Exception {
        HttpURLConnection conn = request("POST", "/api/v1/databases", "{invalid");
        Map<String, Object> error = readErrorJsonResponse(conn, 400);
        assertEquals("invalid_json", ((Map<?, ?>) error.get("error")).get("code"));
    }
}
