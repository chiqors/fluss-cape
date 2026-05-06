package org.gnuhpc.fluss.cape.http.it;

import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpCompatWriteGuardIT extends HttpItBase {

    private static final boolean EXPECT_WRITE_FORBIDDEN = Boolean.parseBoolean(
            System.getProperty("http.compat.expect.write.forbidden",
                    System.getenv().getOrDefault("HTTP_COMPAT_EXPECT_WRITE_FORBIDDEN", "false")));
    private static final String PRESET_DB = System.getProperty("http.compat.guard.db",
            System.getenv().getOrDefault("HTTP_COMPAT_GUARD_DB", ""));
    private static final String PRESET_TABLE = System.getProperty("http.compat.guard.table",
            System.getenv().getOrDefault("HTTP_COMPAT_GUARD_TABLE", ""));

    @Test
    void shouldRejectAdminWriteWhenAdminWriteDisabled() throws Exception {
        if (!EXPECT_WRITE_FORBIDDEN) {
            return;
        }
        HttpURLConnection conn = request("POST", "/api/v1/databases", "{\"name\":\"it_guard_db_" + suffix() + "\"}");
        Map<String, Object> error = readErrorJsonResponse(conn, 403);
        assertEquals("forbidden", ((Map<?, ?>) error.get("error")).get("code"));
    }

    @Test
    void shouldRejectDataWriteWhenDataWriteDisabled() throws Exception {
        if (!EXPECT_WRITE_FORBIDDEN) {
            return;
        }
        if (PRESET_DB.isBlank() || PRESET_TABLE.isBlank()) {
            return;
        }

        HttpURLConnection conn = request("POST", "/api/v1/databases/" + PRESET_DB + "/tables/" + PRESET_TABLE + "/rows",
                "{\"rows\":[{\"id\":1}],\"flush\":true}");
        Map<String, Object> error = readErrorJsonResponse(conn, 403);
        assertEquals("forbidden", ((Map<?, ?>) error.get("error")).get("code"));
    }
}
