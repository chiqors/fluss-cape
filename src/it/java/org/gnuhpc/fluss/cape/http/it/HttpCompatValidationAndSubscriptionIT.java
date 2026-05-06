package org.gnuhpc.fluss.cape.http.it;

import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpCompatValidationAndSubscriptionIT extends HttpItBase {

    @Test
    void shouldRejectMissingTableName() throws Exception {
        String db = "it_val_db_" + suffix();
        postJson("/api/v1/databases", "{" +
                "\"name\":\"" + db + "\"," +
                "\"ignoreIfExists\":true," +
                "\"auth\":{\"username\":\"fluss_user\",\"password\":\"fluss_password\"}" +
                "}");

        HttpURLConnection conn = request("POST", "/api/v1/databases/" + db + "/tables", "{" +
                "\"type\":\"primary_key\"," +
                "\"ignoreIfExists\":true," +
                "\"bucketCount\":3," +
                "\"schema\":[{\"name\":\"id\",\"type\":\"BIGINT\",\"nullable\":false}]," +
                "\"primaryKey\":[\"id\"]" +
                "}");
        Map<String, Object> error = readErrorJsonResponse(conn, 400);
        assertEquals("invalid_argument", ((Map<?, ?>) error.get("error")).get("code"));
    }

    @Test
    void shouldRejectInvalidBucketCount() throws Exception {
        String db = "it_val_db_" + suffix();
        postJson("/api/v1/databases", "{" +
                "\"name\":\"" + db + "\"," +
                "\"ignoreIfExists\":true," +
                "\"auth\":{\"username\":\"fluss_user\",\"password\":\"fluss_password\"}" +
                "}");

        HttpURLConnection conn = request("POST", "/api/v1/databases/" + db + "/tables", "{" +
                "\"name\":\"it_bad_bucket\"," +
                "\"type\":\"primary_key\"," +
                "\"ignoreIfExists\":true," +
                "\"bucketCount\":0," +
                "\"schema\":[{\"name\":\"id\",\"type\":\"BIGINT\",\"nullable\":false}]," +
                "\"primaryKey\":[\"id\"]" +
                "}");
        Map<String, Object> error = readErrorJsonResponse(conn, 400);
        assertEquals("invalid_argument", ((Map<?, ?>) error.get("error")).get("code"));
    }

    @Test
    void shouldRejectMissingPrimaryKeyForPrimaryTable() throws Exception {
        String db = "it_val_db_" + suffix();
        postJson("/api/v1/databases", "{" +
                "\"name\":\"" + db + "\"," +
                "\"ignoreIfExists\":true," +
                "\"auth\":{\"username\":\"fluss_user\",\"password\":\"fluss_password\"}" +
                "}");

        HttpURLConnection conn = request("POST", "/api/v1/databases/" + db + "/tables", "{" +
                "\"name\":\"it_pk_missing\"," +
                "\"type\":\"primary_key\"," +
                "\"ignoreIfExists\":true," +
                "\"bucketCount\":3," +
                "\"schema\":[{\"name\":\"id\",\"type\":\"BIGINT\",\"nullable\":false}]" +
                "}");
        Map<String, Object> error = readErrorJsonResponse(conn, 400);
        assertEquals("missing_primary_key", ((Map<?, ?>) error.get("error")).get("code"));
    }

    @Test
    void shouldRejectPrimaryKeyOnLogTable() throws Exception {
        String db = "it_val_db_" + suffix();
        postJson("/api/v1/databases", "{" +
                "\"name\":\"" + db + "\"," +
                "\"ignoreIfExists\":true," +
                "\"auth\":{\"username\":\"fluss_user\",\"password\":\"fluss_password\"}" +
                "}");

        HttpURLConnection conn = request("POST", "/api/v1/databases/" + db + "/tables", "{" +
                "\"name\":\"it_log_bad\"," +
                "\"type\":\"log\"," +
                "\"ignoreIfExists\":true," +
                "\"bucketCount\":3," +
                "\"schema\":[{\"name\":\"event_id\",\"type\":\"STRING\",\"nullable\":false}]," +
                "\"primaryKey\":[\"event_id\"]" +
                "}");
        Map<String, Object> error = readErrorJsonResponse(conn, 400);
        assertEquals("invalid_argument", ((Map<?, ?>) error.get("error")).get("code"));
    }

    @Test
    void shouldCompleteReadRecordsAndSubscriptionLifecycle() throws Exception {
        String s = suffix();
        String db = "it_flow_db_" + s;
        String table = "it_flow_log_" + s;

        postJson("/api/v1/databases", "{" +
                "\"name\":\"" + db + "\"," +
                "\"ignoreIfExists\":true," +
                "\"auth\":{\"username\":\"fluss_user\",\"password\":\"fluss_password\"}" +
                "}");
        postJson("/api/v1/databases/" + db + "/tables", "{" +
                "\"name\":\"" + table + "\"," +
                "\"type\":\"log\"," +
                "\"ignoreIfExists\":true," +
                "\"bucketCount\":3," +
                "\"schema\":[{" +
                "\"name\":\"event_id\",\"type\":\"STRING\",\"nullable\":false" +
                "},{" +
                "\"name\":\"payload\",\"type\":\"STRING\",\"nullable\":true" +
                "}]" +
                "}");

        Map<String, Object> append = postJson("/api/v1/databases/" + db + "/tables/" + table + "/records", "{" +
                "\"records\":[{\"event_id\":\"e-001\",\"payload\":\"hello\"}]," +
                "\"flush\":true" +
                "}");
        assertEquals(1, ((Number) data(append).get("affectedRecords")).intValue());

        Map<String, Object> read = getJson("/api/v1/databases/" + db + "/tables/" + table + "/records?bucket=0&offset=0&limit=100&timeoutMs=1");
        Map<String, Object> readData = data(read);
        assertTrue(readData.containsKey("records"));
        assertTrue(readData.containsKey("nextOffset"));

        Map<String, Object> sub = postJson("/api/v1/databases/" + db + "/tables/" + table + "/subscriptions", "{" +
                "\"buckets\":[0]," +
                "\"start\":{\"mode\":\"earliest\"}" +
                "}");
        String subId = String.valueOf(data(sub).get("subscriptionId"));

        Map<String, Object> poll = getJson("/api/v1/databases/" + db + "/tables/" + table + "/subscriptions/" + subId + "/records?limit=100&timeoutMs=30000");
        Map<String, Object> pollData = data(poll);
        assertEquals(subId, pollData.get("subscriptionId"));

        Map<String, Object> deleted = deleteJson("/api/v1/databases/" + db + "/tables/" + table + "/subscriptions/" + subId);
        assertEquals(Boolean.TRUE, data(deleted).get("closed"));

        HttpURLConnection pollAfterDelete = request("GET", "/api/v1/databases/" + db + "/tables/" + table + "/subscriptions/" + subId + "/records?limit=1&timeoutMs=1", null);
        Map<String, Object> error = readErrorJsonResponse(pollAfterDelete, 404);
        assertEquals("subscription_not_found", ((Map<?, ?>) error.get("error")).get("code"));
    }

    @Test
    void shouldRejectUnknownFieldInUpsertRow() throws Exception {
        String s = suffix();
        String db = "it_type_db_" + s;
        String table = "it_type_pk_" + s;

        postJson("/api/v1/databases", "{" +
                "\"name\":\"" + db + "\"," +
                "\"ignoreIfExists\":true," +
                "\"auth\":{\"username\":\"fluss_user\",\"password\":\"fluss_password\"}" +
                "}");
        postJson("/api/v1/databases/" + db + "/tables", "{" +
                "\"name\":\"" + table + "\"," +
                "\"type\":\"primary_key\"," +
                "\"ignoreIfExists\":true," +
                "\"bucketCount\":3," +
                "\"schema\":[{" +
                "\"name\":\"id\",\"type\":\"BIGINT\",\"nullable\":false" +
                "},{" +
                "\"name\":\"name\",\"type\":\"STRING\",\"nullable\":true" +
                "}]," +
                "\"primaryKey\":[\"id\"]" +
                "}");

        HttpURLConnection conn = request("POST", "/api/v1/databases/" + db + "/tables/" + table + "/rows", "{" +
                "\"rows\":[{\"id\":1,\"name\":\"A\",\"unknown_col\":\"X\"}]," +
                "\"flush\":true" +
                "}");
        Map<String, Object> error = readErrorJsonResponse(conn, 400);
        assertEquals("unknown_field", ((Map<?, ?>) error.get("error")).get("code"));
    }

    @Test
    void shouldRejectInvalidBigintInUpsertRow() throws Exception {
        String s = suffix();
        String db = "it_type_db_" + s;
        String table = "it_type_pk_" + s;

        postJson("/api/v1/databases", "{" +
                "\"name\":\"" + db + "\"," +
                "\"ignoreIfExists\":true," +
                "\"auth\":{\"username\":\"fluss_user\",\"password\":\"fluss_password\"}" +
                "}");
        postJson("/api/v1/databases/" + db + "/tables", "{" +
                "\"name\":\"" + table + "\"," +
                "\"type\":\"primary_key\"," +
                "\"ignoreIfExists\":true," +
                "\"bucketCount\":3," +
                "\"schema\":[{" +
                "\"name\":\"id\",\"type\":\"BIGINT\",\"nullable\":false" +
                "},{" +
                "\"name\":\"name\",\"type\":\"STRING\",\"nullable\":true" +
                "}]," +
                "\"primaryKey\":[\"id\"]" +
                "}");

        HttpURLConnection conn = request("POST", "/api/v1/databases/" + db + "/tables/" + table + "/rows", "{" +
                "\"rows\":[{\"id\":\"not-a-number\",\"name\":\"A\"}]," +
                "\"flush\":true" +
                "}");
        Map<String, Object> error = readErrorJsonResponse(conn, 400);
        assertEquals("invalid_argument", ((Map<?, ?>) error.get("error")).get("code"));
    }

    @Test
    void shouldRejectMissingPrimaryKeyInUpsertRow() throws Exception {
        String s = suffix();
        String db = "it_type_db_" + s;
        String table = "it_type_pk_" + s;

        postJson("/api/v1/databases", "{" +
                "\"name\":\"" + db + "\"," +
                "\"ignoreIfExists\":true," +
                "\"auth\":{\"username\":\"fluss_user\",\"password\":\"fluss_password\"}" +
                "}");
        postJson("/api/v1/databases/" + db + "/tables", "{" +
                "\"name\":\"" + table + "\"," +
                "\"type\":\"primary_key\"," +
                "\"ignoreIfExists\":true," +
                "\"bucketCount\":3," +
                "\"schema\":[{" +
                "\"name\":\"id\",\"type\":\"BIGINT\",\"nullable\":false" +
                "},{" +
                "\"name\":\"name\",\"type\":\"STRING\",\"nullable\":true" +
                "}]," +
                "\"primaryKey\":[\"id\"]" +
                "}");

        HttpURLConnection conn = request("POST", "/api/v1/databases/" + db + "/tables/" + table + "/rows", "{" +
                "\"rows\":[{\"name\":\"A\"}]," +
                "\"flush\":true" +
                "}");
        Map<String, Object> error = readErrorJsonResponse(conn, 400);
        assertEquals("missing_primary_key", ((Map<?, ?>) error.get("error")).get("code"));
    }

    @Test
    void shouldRejectInvalidPrimaryKeyTypeInLookup() throws Exception {
        String s = suffix();
        String db = "it_type_db_" + s;
        String table = "it_type_pk_" + s;

        postJson("/api/v1/databases", "{" +
                "\"name\":\"" + db + "\"," +
                "\"ignoreIfExists\":true," +
                "\"auth\":{\"username\":\"fluss_user\",\"password\":\"fluss_password\"}" +
                "}");
        postJson("/api/v1/databases/" + db + "/tables", "{" +
                "\"name\":\"" + table + "\"," +
                "\"type\":\"primary_key\"," +
                "\"ignoreIfExists\":true," +
                "\"bucketCount\":3," +
                "\"schema\":[{" +
                "\"name\":\"id\",\"type\":\"BIGINT\",\"nullable\":false" +
                "}]," +
                "\"primaryKey\":[\"id\"]" +
                "}");

        HttpURLConnection conn = request("GET", "/api/v1/databases/" + db + "/tables/" + table + "/rows?key.id=abc", null);
        Map<String, Object> error = readErrorJsonResponse(conn, 400);
        assertEquals("invalid_argument", ((Map<?, ?>) error.get("error")).get("code"));
    }

    @Test
    void shouldRejectInvalidPrimaryKeyTypeInDeleteRow() throws Exception {
        String s = suffix();
        String db = "it_type_db_" + s;
        String table = "it_type_pk_" + s;

        postJson("/api/v1/databases", "{" +
                "\"name\":\"" + db + "\"," +
                "\"ignoreIfExists\":true," +
                "\"auth\":{\"username\":\"fluss_user\",\"password\":\"fluss_password\"}" +
                "}");
        postJson("/api/v1/databases/" + db + "/tables", "{" +
                "\"name\":\"" + table + "\"," +
                "\"type\":\"primary_key\"," +
                "\"ignoreIfExists\":true," +
                "\"bucketCount\":3," +
                "\"schema\":[{" +
                "\"name\":\"id\",\"type\":\"BIGINT\",\"nullable\":false" +
                "}]," +
                "\"primaryKey\":[\"id\"]" +
                "}");

        HttpURLConnection conn = request("DELETE", "/api/v1/databases/" + db + "/tables/" + table + "/rows?key.id=abc", null);
        Map<String, Object> error = readErrorJsonResponse(conn, 400);
        assertEquals("invalid_argument", ((Map<?, ?>) error.get("error")).get("code"));
    }

    @Test
    void shouldRejectDeleteRowWhenPrimaryKeyMissing() throws Exception {
        String s = suffix();
        String db = "it_type_db_" + s;
        String table = "it_type_pk_" + s;

        postJson("/api/v1/databases", "{" +
                "\"name\":\"" + db + "\"," +
                "\"ignoreIfExists\":true," +
                "\"auth\":{\"username\":\"fluss_user\",\"password\":\"fluss_password\"}" +
                "}");
        postJson("/api/v1/databases/" + db + "/tables", "{" +
                "\"name\":\"" + table + "\"," +
                "\"type\":\"primary_key\"," +
                "\"ignoreIfExists\":true," +
                "\"bucketCount\":3," +
                "\"schema\":[{" +
                "\"name\":\"id\",\"type\":\"BIGINT\",\"nullable\":false" +
                "}]," +
                "\"primaryKey\":[\"id\"]" +
                "}");

        HttpURLConnection conn = request("DELETE", "/api/v1/databases/" + db + "/tables/" + table + "/rows", null);
        Map<String, Object> error = readErrorJsonResponse(conn, 400);
        assertEquals("missing_primary_key", ((Map<?, ?>) error.get("error")).get("code"));
    }
}
