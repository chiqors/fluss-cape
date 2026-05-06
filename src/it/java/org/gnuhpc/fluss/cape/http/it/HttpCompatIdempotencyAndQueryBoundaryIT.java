package org.gnuhpc.fluss.cape.http.it;

import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpCompatIdempotencyAndQueryBoundaryIT extends HttpItBase {

    @Test
    void shouldCreateDatabaseIdempotentlyWhenIgnoreIfExistsTrue() throws Exception {
        String db = "it_idem_db_" + suffix();

        Map<String, Object> first = postJson("/api/v1/databases", "{\"name\":\"" + db + "\",\"ignoreIfExists\":true}");
        assertEquals(Boolean.TRUE, data(first).get("created"));

        Map<String, Object> second = postJson("/api/v1/databases", "{\"name\":\"" + db + "\",\"ignoreIfExists\":true}");
        assertEquals(Boolean.FALSE, data(second).get("created"));
    }

    @Test
    void shouldCreateTableIdempotentlyWhenIgnoreIfExistsTrue() throws Exception {
        String s = suffix();
        String db = "it_idem_db_" + s;
        String table = "it_idem_pk_" + s;

        postJson("/api/v1/databases", "{\"name\":\"" + db + "\",\"ignoreIfExists\":true}");

        String createTableBody = "{" +
                "\"name\":\"" + table + "\"," +
                "\"type\":\"primary_key\"," +
                "\"ignoreIfExists\":true," +
                "\"bucketCount\":3," +
                "\"schema\":[{\"name\":\"id\",\"type\":\"BIGINT\",\"nullable\":false}]," +
                "\"primaryKey\":[\"id\"]" +
                "}";

        Map<String, Object> first = postJson("/api/v1/databases/" + db + "/tables", createTableBody);
        assertEquals(Boolean.TRUE, data(first).get("created"));

        Map<String, Object> second = postJson("/api/v1/databases/" + db + "/tables", createTableBody);
        assertEquals(Boolean.FALSE, data(second).get("created"));
    }

    @Test
    void shouldRejectNonNumericBucketAndKeepErrorContract() throws Exception {
        String s = suffix();
        String db = "it_bound_db_" + s;
        String table = "it_bound_log_" + s;

        postJson("/api/v1/databases", "{\"name\":\"" + db + "\",\"ignoreIfExists\":true}");
        postJson("/api/v1/databases/" + db + "/tables", "{" +
                "\"name\":\"" + table + "\"," +
                "\"type\":\"log\"," +
                "\"ignoreIfExists\":true," +
                "\"bucketCount\":3," +
                "\"schema\":[{\"name\":\"event_id\",\"type\":\"STRING\",\"nullable\":false},{\"name\":\"payload\",\"type\":\"STRING\",\"nullable\":true}]" +
                "}");

        HttpURLConnection conn = request("GET", "/api/v1/databases/" + db + "/tables/" + table + "/records?bucket=abc&offset=0&limit=10&timeoutMs=1", null);
        Map<String, Object> error = readErrorJsonResponse(conn, 400);
        assertEquals(Boolean.FALSE, error.get("ok"));
        Map<?, ?> err = (Map<?, ?>) error.get("error");
        assertNotNull(err);
        assertEquals("invalid_argument", err.get("code"));
        assertNotNull(err.get("message"));
    }

    @Test
    void shouldHandleLimitBoundariesWithoutServerError() throws Exception {
        String s = suffix();
        String db = "it_bound_db_" + s;
        String table = "it_bound_log_" + s;

        postJson("/api/v1/databases", "{\"name\":\"" + db + "\",\"ignoreIfExists\":true}");
        postJson("/api/v1/databases/" + db + "/tables", "{" +
                "\"name\":\"" + table + "\"," +
                "\"type\":\"log\"," +
                "\"ignoreIfExists\":true," +
                "\"bucketCount\":3," +
                "\"schema\":[{\"name\":\"event_id\",\"type\":\"STRING\",\"nullable\":false},{\"name\":\"payload\",\"type\":\"STRING\",\"nullable\":true}]" +
                "}");

        postJson("/api/v1/databases/" + db + "/tables/" + table + "/records", "{\"records\":[{\"event_id\":\"e-1\",\"payload\":\"p1\"}],\"flush\":true}");

        Map<String, Object> lowLimit = getJson("/api/v1/databases/" + db + "/tables/" + table + "/records?bucket=0&offset=0&limit=0&timeoutMs=1000");
        Map<String, Object> lowData = data(lowLimit);
        assertTrue(lowData.containsKey("records"));
        assertTrue(lowData.containsKey("nextOffset"));

        Map<String, Object> highLimit = getJson("/api/v1/databases/" + db + "/tables/" + table + "/records?bucket=0&offset=0&limit=999999&timeoutMs=1000");
        Map<String, Object> highData = data(highLimit);
        assertTrue(highData.containsKey("records"));
        assertTrue(highData.containsKey("nextOffset"));
    }
}
