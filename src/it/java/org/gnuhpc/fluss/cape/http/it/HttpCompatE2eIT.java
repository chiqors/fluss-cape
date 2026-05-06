package org.gnuhpc.fluss.cape.http.it;

import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;
import java.util.UUID;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HttpCompatE2eIT extends HttpItBase {

    @Test
    void shouldCompleteHttpClosedLoop() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String db = "it_app_" + suffix;
        String pkTable = "it_users_" + suffix;
        String logTable = "it_events_" + suffix;

        Map<String, Object> createDb = postJson("/api/v1/databases", "{" +
                "\"name\":\"" + db + "\"," +
                "\"ignoreIfExists\":true," +
                "\"auth\":{\"username\":\"fluss_user\",\"password\":\"fluss_password\"}" +
                "}");
        Map<String, Object> createDbData = data(createDb);
        assertEquals(db, createDbData.get("database"));
        assertEquals(Boolean.TRUE, createDbData.get("created"));

        Map<String, Object> createPk = postJson("/api/v1/databases/" + db + "/tables", "{" +
                "\"name\":\"" + pkTable + "\"," +
                "\"type\":\"primary_key\"," +
                "\"ignoreIfExists\":true," +
                "\"bucketCount\":3," +
                "\"schema\":[{" +
                "\"name\":\"id\",\"type\":\"BIGINT\",\"nullable\":false" +
                "},{" +
                "\"name\":\"name\",\"type\":\"STRING\",\"nullable\":true" +
                "}]," +
                "\"primaryKey\":[\"id\"]," +
                "\"auth\":{\"username\":\"fluss_user\",\"password\":\"fluss_password\"}" +
                "}");
        Map<String, Object> createPkData = data(createPk);
        assertEquals(pkTable, createPkData.get("table"));

        Map<String, Object> upsert = postJson("/api/v1/databases/" + db + "/tables/" + pkTable + "/rows", "{" +
                "\"rows\":[{\"id\":1001,\"name\":\"Alice\"}]," +
                "\"flush\":true," +
                "\"auth\":{\"username\":\"fluss_user\",\"password\":\"fluss_password\"}" +
                "}");
        Map<String, Object> upsertData = data(upsert);
        assertEquals(1, ((Number) upsertData.get("affectedRows")).intValue());

        Map<String, Object> lookup = getJson("/api/v1/databases/" + db + "/tables/" + pkTable + "/rows?key.id=1001");
        Map<String, Object> lookupData = data(lookup);
        assertEquals(Boolean.TRUE, lookupData.get("found"));
        assertEquals("Alice", ((Map<?, ?>) lookupData.get("row")).get("name"));

        Map<String, Object> createLog = postJson("/api/v1/databases/" + db + "/tables", "{" +
                "\"name\":\"" + logTable + "\"," +
                "\"type\":\"log\"," +
                "\"ignoreIfExists\":true," +
                "\"bucketCount\":3," +
                "\"schema\":[{" +
                "\"name\":\"event_id\",\"type\":\"STRING\",\"nullable\":false" +
                "},{" +
                "\"name\":\"payload\",\"type\":\"STRING\",\"nullable\":true" +
                "}]," +
                "\"auth\":{\"username\":\"fluss_user\",\"password\":\"fluss_password\"}" +
                "}");
        Map<String, Object> createLogData = data(createLog);
        assertEquals(logTable, createLogData.get("table"));

        Map<String, Object> sub = postJson("/api/v1/databases/" + db + "/tables/" + logTable + "/subscriptions", "{" +
                "\"buckets\":[0,1,2]," +
                "\"start\":{\"mode\":\"earliest\"}," +
                "\"auth\":{\"username\":\"fluss_user\",\"password\":\"fluss_password\"}" +
                "}");
        Map<String, Object> subData = data(sub);
        String subscriptionId = String.valueOf(subData.get("subscriptionId"));
        assertNotNull(subscriptionId);

        Map<String, Object> append = postJson("/api/v1/databases/" + db + "/tables/" + logTable + "/records", "{" +
                "\"records\":[{\"event_id\":\"e-001\",\"payload\":\"hello\"}]," +
                "\"flush\":true," +
                "\"auth\":{\"username\":\"fluss_user\",\"password\":\"fluss_password\"}" +
                "}");
        Map<String, Object> appendData = data(append);
        assertEquals(1, ((Number) appendData.get("affectedRecords")).intValue());

        Map<String, Object> poll = getJson("/api/v1/databases/" + db + "/tables/" + logTable + "/subscriptions/" + subscriptionId + "/records?limit=100&timeoutMs=30000");
        Map<String, Object> pollData = data(poll);
        assertEquals(subscriptionId, pollData.get("subscriptionId"));
        assertEquals(1, ((List<?>) pollData.get("records")).size());
    }

    @Test
    void shouldReturnTableTypeMismatch() throws Exception {
        String db = "it_app2";
        String pkTable = "it_users2";

        postJson("/api/v1/databases", "{" +
                "\"name\":\"" + db + "\"," +
                "\"ignoreIfExists\":true," +
                "\"auth\":{\"username\":\"fluss_user\",\"password\":\"fluss_password\"}" +
                "}");
        postJson("/api/v1/databases/" + db + "/tables", "{" +
                "\"name\":\"" + pkTable + "\"," +
                "\"type\":\"primary_key\"," +
                "\"ignoreIfExists\":true," +
                "\"bucketCount\":3," +
                "\"schema\":[{" +
                "\"name\":\"id\",\"type\":\"BIGINT\",\"nullable\":false" +
                "}]," +
                "\"primaryKey\":[\"id\"]," +
                "\"auth\":{\"username\":\"fluss_user\",\"password\":\"fluss_password\"}" +
                "}");

        HttpURLConnection conn = request("POST", "/api/v1/databases/" + db + "/tables/" + pkTable + "/records", "{" +
                "\"records\":[{\"event_id\":\"e-001\"}]," +
                "\"flush\":true," +
                "\"auth\":{\"username\":\"fluss_user\",\"password\":\"fluss_password\"}" +
                "}");
        Map<String, Object> error = readErrorJsonResponse(conn, 409);
        assertEquals("table_type_mismatch", ((Map<?, ?>) error.get("error")).get("code"));
    }

}
