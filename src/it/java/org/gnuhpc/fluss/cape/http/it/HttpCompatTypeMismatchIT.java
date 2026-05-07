package org.gnuhpc.fluss.cape.http.it;

import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpCompatTypeMismatchIT extends HttpItBase {

    @Test
    void shouldRejectAppendOnPrimaryKeyTable() throws Exception {
        String s = suffix();
        String db = "it_mismatch_" + s;
        String table = "it_pk_" + s;

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
                "\"primaryKey\":[\"id\"]," +
                "\"auth\":{\"username\":\"fluss_user\",\"password\":\"fluss_password\"}" +
                "}");

        HttpURLConnection conn = request("POST", "/api/v1/databases/" + db + "/tables/" + table + "/records", "{" +
                "\"records\":[{\"event_id\":\"e-001\"}]," +
                "\"flush\":true," +
                "\"auth\":{\"username\":\"fluss_user\",\"password\":\"fluss_password\"}" +
                "}");
        Map<String, Object> error = readErrorJsonResponse(conn, 409);
        assertEquals("table_type_mismatch", ((Map<?, ?>) error.get("error")).get("code"));
    }
}
