package org.gnuhpc.fluss.cape.http.it;

import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class HttpCompatDataTypeIT extends HttpItBase {

    @Test
    void shouldRoundTripPrimitiveDataTypesInPrimaryKeyTable() throws Exception {
        String s = suffix();
        String db = "it_dtype_db_" + s;
        String table = "it_dtype_pk_" + s;

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
                "\"name\":\"flag\",\"type\":\"BOOLEAN\",\"nullable\":true" +
                "},{" +
                "\"name\":\"tiny_v\",\"type\":\"TINYINT\",\"nullable\":true" +
                "},{" +
                "\"name\":\"small_v\",\"type\":\"SMALLINT\",\"nullable\":true" +
                "},{" +
                "\"name\":\"score_i\",\"type\":\"INTEGER\",\"nullable\":true" +
                "},{" +
                "\"name\":\"score_f\",\"type\":\"FLOAT\",\"nullable\":true" +
                "},{" +
                "\"name\":\"ratio_d\",\"type\":\"DOUBLE\",\"nullable\":true" +
                "},{" +
                "\"name\":\"event_date\",\"type\":\"DATE\",\"nullable\":true" +
                "},{" +
                "\"name\":\"event_ts\",\"type\":\"TIMESTAMP\",\"nullable\":true" +
                "}]," +
                "\"primaryKey\":[\"id\"]," +
                "\"auth\":{\"username\":\"fluss_user\",\"password\":\"fluss_password\"}" +
                "}");

        postJson("/api/v1/databases/" + db + "/tables/" + table + "/rows", "{" +
                "\"rows\":[{" +
                "\"id\":1," +
                "\"flag\":true," +
                "\"tiny_v\":7," +
                "\"small_v\":32000," +
                "\"score_i\":42," +
                "\"score_f\":1.5," +
                "\"ratio_d\":2.75," +
                "\"event_date\":\"2026-05-04\"," +
                "\"event_ts\":\"2026-05-04T12:34:56\"" +
                "}]," +
                "\"flush\":true," +
                "\"auth\":{\"username\":\"fluss_user\",\"password\":\"fluss_password\"}" +
                "}");

        Map<String, Object> lookup = getJson("/api/v1/databases/" + db + "/tables/" + table + "/rows?key.id=1");
        Map<String, Object> data = data(lookup);
        assertEquals(Boolean.TRUE, data.get("found"));
        Map<?, ?> row = (Map<?, ?>) data.get("row");
        assertEquals(Boolean.TRUE, row.get("flag"));
        assertEquals(7, ((Number) row.get("tiny_v")).intValue());
        assertEquals(32000, ((Number) row.get("small_v")).intValue());
        assertEquals(42, ((Number) row.get("score_i")).intValue());
        assertEquals(1.5f, ((Number) row.get("score_f")).floatValue(), 0.0001f);
        assertEquals(2.75d, ((Number) row.get("ratio_d")).doubleValue(), 0.0001d);
        assertEquals("2026-05-04", row.get("event_date"));
        assertNotNull(row.get("event_ts"));
    }

    @Test
    void shouldRoundTripBigintMinAndMaxValues() throws Exception {
        String s = suffix();
        String db = "it_dtype_db_" + s;
        String table = "it_dtype_pk_" + s;

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
                "\"name\":\"value_v\",\"type\":\"BIGINT\",\"nullable\":true" +
                "}]," +
                "\"primaryKey\":[\"id\"]" +
                "}");

        postJson("/api/v1/databases/" + db + "/tables/" + table + "/rows", "{" +
                "\"rows\":[{\"id\":1,\"value_v\":-9223372036854775808}]," +
                "\"flush\":true" +
                "}");
        postJson("/api/v1/databases/" + db + "/tables/" + table + "/rows", "{" +
                "\"rows\":[{\"id\":2,\"value_v\":9223372036854775807}]," +
                "\"flush\":true" +
                "}");

        Map<?, ?> row1 = (Map<?, ?>) data(getJson("/api/v1/databases/" + db + "/tables/" + table + "/rows?key.id=1")).get("row");
        Map<?, ?> row2 = (Map<?, ?>) data(getJson("/api/v1/databases/" + db + "/tables/" + table + "/rows?key.id=2")).get("row");
        assertEquals(-9223372036854775808L, ((Number) row1.get("value_v")).longValue());
        assertEquals(9223372036854775807L, ((Number) row2.get("value_v")).longValue());
    }

    @Test
    void shouldRoundTripStringAndBinaryDataInLogTable() throws Exception {
        String s = suffix();
        String db = "it_dtype_db_" + s;
        String table = "it_dtype_log_" + s;

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
                "\"name\":\"kind\",\"type\":\"CHAR\",\"nullable\":true" +
                "},{" +
                "\"name\":\"title\",\"type\":\"VARCHAR\",\"nullable\":true" +
                "},{" +
                "\"name\":\"payload\",\"type\":\"BINARY\",\"nullable\":true" +
                "}]," +
                "\"auth\":{\"username\":\"fluss_user\",\"password\":\"fluss_password\"}" +
                "}");

        postJson("/api/v1/databases/" + db + "/tables/" + table + "/records", "{" +
                "\"records\":[{\"event_id\":\"e-001\",\"kind\":\"A\",\"title\":\"hello\",\"payload\":\"aGVsbG8=\"}]," +
                "\"flush\":true" +
                "}");

        Map<String, Object> sub = postJson("/api/v1/databases/" + db + "/tables/" + table + "/subscriptions", "{" +
                "\"buckets\":[0,1,2]," +
                "\"start\":{\"mode\":\"earliest\"}" +
                "}");
        String subId = String.valueOf(data(sub).get("subscriptionId"));
        Map<String, Object> poll = getJson("/api/v1/databases/" + db + "/tables/" + table + "/subscriptions/" + subId + "/records?limit=10&timeoutMs=30000");
        List<?> records = (List<?>) data(poll).get("records");
        assertEquals(1, records.size());
        Map<?, ?> first = (Map<?, ?>) ((Map<?, ?>) records.get(0)).get("row");
        assertEquals("A", first.get("kind"));
        assertEquals("hello", first.get("title"));
        assertEquals("aGVsbG8=", first.get("payload"));
    }

    @Test
    void shouldRejectInvalidTinyintAndFloatValues() throws Exception {
        String s = suffix();
        String db = "it_dtype_db_" + s;
        String table = "it_dtype_pk_" + s;

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
                "\"name\":\"tiny_v\",\"type\":\"TINYINT\",\"nullable\":true" +
                "},{" +
                "\"name\":\"score_f\",\"type\":\"FLOAT\",\"nullable\":true" +
                "}]," +
                "\"primaryKey\":[\"id\"]" +
                "}");

        HttpURLConnection badTiny = request("POST", "/api/v1/databases/" + db + "/tables/" + table + "/rows", "{" +
                "\"rows\":[{\"id\":1,\"tiny_v\":999}]," +
                "\"flush\":true" +
                "}");
        assertEquals("invalid_argument", ((Map<?, ?>) readErrorJsonResponse(badTiny, 400).get("error")).get("code"));

        HttpURLConnection badFloat = request("POST", "/api/v1/databases/" + db + "/tables/" + table + "/rows", "{" +
                "\"rows\":[{\"id\":2,\"score_f\":\"NaN?\"}]," +
                "\"flush\":true" +
                "}");
        assertEquals("invalid_argument", ((Map<?, ?>) readErrorJsonResponse(badFloat, 400).get("error")).get("code"));
    }

    @Test
    void shouldRejectInvalidDateAndTimestampValues() throws Exception {
        String s = suffix();
        String db = "it_dtype_db_" + s;
        String table = "it_dtype_pk_" + s;

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
                "\"name\":\"event_date\",\"type\":\"DATE\",\"nullable\":true" +
                "},{" +
                "\"name\":\"event_ts\",\"type\":\"TIMESTAMP\",\"nullable\":true" +
                "}]," +
                "\"primaryKey\":[\"id\"]" +
                "}");

        HttpURLConnection badDate = request("POST", "/api/v1/databases/" + db + "/tables/" + table + "/rows", "{" +
                "\"rows\":[{\"id\":1,\"event_date\":\"2026-99-99\"}]," +
                "\"flush\":true" +
                "}");
        assertEquals("invalid_argument", ((Map<?, ?>) readErrorJsonResponse(badDate, 400).get("error")).get("code"));

        HttpURLConnection badTs = request("POST", "/api/v1/databases/" + db + "/tables/" + table + "/rows", "{" +
                "\"rows\":[{\"id\":2,\"event_ts\":\"not-a-timestamp\"}]," +
                "\"flush\":true" +
                "}");
        assertEquals("invalid_argument", ((Map<?, ?>) readErrorJsonResponse(badTs, 400).get("error")).get("code"));
    }

    @Test
    void shouldAcceptLeapDayForDate() throws Exception {
        String s = suffix();
        String db = "it_dtype_db_" + s;
        String table = "it_dtype_pk_" + s;

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
                "\"name\":\"event_date\",\"type\":\"DATE\",\"nullable\":true" +
                "}]," +
                "\"primaryKey\":[\"id\"]" +
                "}");

        postJson("/api/v1/databases/" + db + "/tables/" + table + "/rows", "{" +
                "\"rows\":[{\"id\":1,\"event_date\":\"2024-02-29\"}]," +
                "\"flush\":true" +
                "}");
        Map<?, ?> row = (Map<?, ?>) data(getJson("/api/v1/databases/" + db + "/tables/" + table + "/rows?key.id=1")).get("row");
        assertEquals("2024-02-29", row.get("event_date"));
    }

    @Test
    void shouldRejectInvalidBinaryBase64AndAcceptEmptyBinary() throws Exception {
        String s = suffix();
        String db = "it_dtype_db_" + s;
        String table = "it_dtype_log_" + s;

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
                "\"name\":\"payload\",\"type\":\"BINARY\",\"nullable\":true" +
                "}]" +
                "}");

        HttpURLConnection invalidBase64 = request("POST", "/api/v1/databases/" + db + "/tables/" + table + "/records", "{" +
                "\"records\":[{\"event_id\":\"e-001\",\"payload\":\"%%%NOT_BASE64%%%\"}]," +
                "\"flush\":true" +
                "}");
        assertEquals("invalid_argument", ((Map<?, ?>) readErrorJsonResponse(invalidBase64, 400).get("error")).get("code"));

        postJson("/api/v1/databases/" + db + "/tables/" + table + "/records", "{" +
                "\"records\":[{\"event_id\":\"e-002\",\"payload\":\"\"}]," +
                "\"flush\":true" +
                "}");
    }

    @Test
    void shouldSupportTimestampNumericAndStringInput() throws Exception {
        String s = suffix();
        String db = "it_dtype_db_" + s;
        String table = "it_dtype_pk_" + s;

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
                "\"name\":\"event_ts\",\"type\":\"TIMESTAMP\",\"nullable\":true" +
                "}]," +
                "\"primaryKey\":[\"id\"]" +
                "}");

        postJson("/api/v1/databases/" + db + "/tables/" + table + "/rows", "{" +
                "\"rows\":[{\"id\":1,\"event_ts\":\"2026-05-04T10:11:12\"}]," +
                "\"flush\":true" +
                "}");
        postJson("/api/v1/databases/" + db + "/tables/" + table + "/rows", "{" +
                "\"rows\":[{\"id\":2,\"event_ts\":1714816800000000}]," +
                "\"flush\":true" +
                "}");

        Map<String, Object> lookup1 = getJson("/api/v1/databases/" + db + "/tables/" + table + "/rows?key.id=1");
        Map<?, ?> row1 = (Map<?, ?>) data(lookup1).get("row");
        assertNotNull(row1.get("event_ts"));
        Map<String, Object> lookup2 = getJson("/api/v1/databases/" + db + "/tables/" + table + "/rows?key.id=2");
        Map<?, ?> row2 = (Map<?, ?>) data(lookup2).get("row");
        assertNotNull(row2.get("event_ts"));
    }

    @Test
    void shouldRejectNullForNonNullableColumn() throws Exception {
        String s = suffix();
        String db = "it_dtype_db_" + s;
        String table = "it_dtype_pk_" + s;

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
                "\"name\":\"must_name\",\"type\":\"STRING\",\"nullable\":false" +
                "}]," +
                "\"primaryKey\":[\"id\"]" +
                "}");

        HttpURLConnection nullValue = request("POST", "/api/v1/databases/" + db + "/tables/" + table + "/rows", "{" +
                "\"rows\":[{\"id\":1,\"must_name\":null}]," +
                "\"flush\":true" +
                "}");
        Map<String, Object> error = readErrorJsonResponse(nullValue, 400);
        assertEquals("invalid_argument", ((Map<?, ?>) error.get("error")).get("code"));
    }

    @Test
    void shouldAcceptNullForNullableColumn() throws Exception {
        String s = suffix();
        String db = "it_dtype_db_" + s;
        String table = "it_dtype_pk_" + s;

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
                "\"name\":\"opt_name\",\"type\":\"STRING\",\"nullable\":true" +
                "}]," +
                "\"primaryKey\":[\"id\"]" +
                "}");

        postJson("/api/v1/databases/" + db + "/tables/" + table + "/rows", "{" +
                "\"rows\":[{\"id\":1,\"opt_name\":null}]," +
                "\"flush\":true" +
                "}");
        Map<?, ?> row = (Map<?, ?>) data(getJson("/api/v1/databases/" + db + "/tables/" + table + "/rows?key.id=1")).get("row");
        assertEquals(null, row.get("opt_name"));
    }

    @Test
    void shouldRejectOutOfRangeNumericBoundaries() throws Exception {
        String s = suffix();
        String db = "it_dtype_db_" + s;
        String table = "it_dtype_pk_" + s;

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
                "\"name\":\"tiny_v\",\"type\":\"TINYINT\",\"nullable\":true" +
                "},{" +
                "\"name\":\"small_v\",\"type\":\"SMALLINT\",\"nullable\":true" +
                "},{" +
                "\"name\":\"score_i\",\"type\":\"INTEGER\",\"nullable\":true" +
                "}]," +
                "\"primaryKey\":[\"id\"]" +
                "}");

        HttpURLConnection tinyOverflow = request("POST", "/api/v1/databases/" + db + "/tables/" + table + "/rows", "{" +
                "\"rows\":[{\"id\":1,\"tiny_v\":128}]," +
                "\"flush\":true" +
                "}");
        assertEquals("invalid_argument", ((Map<?, ?>) readErrorJsonResponse(tinyOverflow, 400).get("error")).get("code"));

        HttpURLConnection smallOverflow = request("POST", "/api/v1/databases/" + db + "/tables/" + table + "/rows", "{" +
                "\"rows\":[{\"id\":2,\"small_v\":32768}]," +
                "\"flush\":true" +
                "}");
        assertEquals("invalid_argument", ((Map<?, ?>) readErrorJsonResponse(smallOverflow, 400).get("error")).get("code"));

        HttpURLConnection intOverflow = request("POST", "/api/v1/databases/" + db + "/tables/" + table + "/rows", "{" +
                "\"rows\":[{\"id\":3,\"score_i\":2147483648}]," +
                "\"flush\":true" +
                "}");
        assertEquals("invalid_argument", ((Map<?, ?>) readErrorJsonResponse(intOverflow, 400).get("error")).get("code"));
    }

    @Test
    void shouldRejectInvalidBooleanText() throws Exception {
        String s = suffix();
        String db = "it_dtype_db_" + s;
        String table = "it_dtype_pk_" + s;

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
                "\"name\":\"flag\",\"type\":\"BOOLEAN\",\"nullable\":true" +
                "}]," +
                "\"primaryKey\":[\"id\"]" +
                "}");

        HttpURLConnection badBool = request("POST", "/api/v1/databases/" + db + "/tables/" + table + "/rows", "{" +
                "\"rows\":[{\"id\":1,\"flag\":\"truthy\"}]," +
                "\"flush\":true" +
                "}");
        assertEquals("invalid_argument", ((Map<?, ?>) readErrorJsonResponse(badBool, 400).get("error")).get("code"));
    }

    @Test
    void shouldRoundTripLargeBinaryPayloadInLogTable() throws Exception {
        String s = suffix();
        String db = "it_dtype_db_" + s;
        String table = "it_dtype_log_" + s;

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
                "\"name\":\"payload\",\"type\":\"BINARY\",\"nullable\":true" +
                "}]" +
                "}");

        byte[] raw = new byte[8192];
        for (int i = 0; i < raw.length; i++) {
            raw[i] = (byte) (i % 251);
        }
        String payload = Base64.getEncoder().encodeToString(raw);

        postJson("/api/v1/databases/" + db + "/tables/" + table + "/records", "{" +
                "\"records\":[{\"event_id\":\"e-big\",\"payload\":\"" + payload + "\"}]," +
                "\"flush\":true" +
                "}");

        Map<String, Object> sub = postJson("/api/v1/databases/" + db + "/tables/" + table + "/subscriptions", "{" +
                "\"buckets\":[0,1,2]," +
                "\"start\":{\"mode\":\"earliest\"}" +
                "}");
        String subId = String.valueOf(data(sub).get("subscriptionId"));
        Map<String, Object> poll = getJson("/api/v1/databases/" + db + "/tables/" + table + "/subscriptions/" + subId + "/records?limit=20&timeoutMs=30000");
        List<?> records = (List<?>) data(poll).get("records");
        assertEquals(1, records.size());
        Map<?, ?> row = (Map<?, ?>) ((Map<?, ?>) records.get(0)).get("row");
        assertEquals(payload, row.get("payload"));
    }
}
