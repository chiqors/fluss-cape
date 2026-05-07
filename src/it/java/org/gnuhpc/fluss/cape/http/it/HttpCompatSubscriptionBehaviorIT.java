package org.gnuhpc.fluss.cape.http.it;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpCompatSubscriptionBehaviorIT extends HttpItBase {

    @Test
    void shouldKeepSubscriptionContractAcrossPollsAfterWrites() throws Exception {
        String s = suffix();
        String db = "it_sub_db_" + s;
        String table = "it_sub_log_" + s;

        postJson("/api/v1/databases", "{\"name\":\"" + db + "\",\"ignoreIfExists\":true}");
        postJson("/api/v1/databases/" + db + "/tables", "{" +
                "\"name\":\"" + table + "\"," +
                "\"type\":\"log\"," +
                "\"ignoreIfExists\":true," +
                "\"bucketCount\":3," +
                "\"schema\":[{\"name\":\"event_id\",\"type\":\"STRING\",\"nullable\":false},{\"name\":\"payload\",\"type\":\"STRING\",\"nullable\":true}]" +
                "}");

        Map<String, Object> sub = postJson("/api/v1/databases/" + db + "/tables/" + table + "/subscriptions", "{\"buckets\":[0,1,2],\"start\":{\"mode\":\"earliest\"}}");
        String subId = String.valueOf(data(sub).get("subscriptionId"));

        postJson("/api/v1/databases/" + db + "/tables/" + table + "/records", "{\"records\":[{\"event_id\":\"e-1\",\"payload\":\"p1\"}],\"flush\":true}");
        Map<String, Object> data1 = data(getJson("/api/v1/databases/" + db + "/tables/" + table + "/subscriptions/" + subId + "/records?limit=10&timeoutMs=30000"));
        assertEquals(subId, data1.get("subscriptionId"));
        assertTrue(data1.containsKey("records"));
        assertTrue(data1.containsKey("positions"));
        long pos1 = sumPositions((Map<?, ?>) data1.get("positions"));

        postJson("/api/v1/databases/" + db + "/tables/" + table + "/records", "{\"records\":[{\"event_id\":\"e-2\",\"payload\":\"p2\"}],\"flush\":true}");
        Map<String, Object> data2 = data(getJson("/api/v1/databases/" + db + "/tables/" + table + "/subscriptions/" + subId + "/records?limit=10&timeoutMs=30000"));
        assertEquals(subId, data2.get("subscriptionId"));
        assertTrue(data2.containsKey("records"));
        assertTrue(data2.containsKey("positions"));
        long pos2 = sumPositions((Map<?, ?>) data2.get("positions"));
        assertTrue(pos2 >= pos1);
    }

    @Test
    void shouldReturnStableEmptyRecordsOnIdlePoll() throws Exception {
        String s = suffix();
        String db = "it_sub_db_" + s;
        String table = "it_sub_log_" + s;

        postJson("/api/v1/databases", "{\"name\":\"" + db + "\",\"ignoreIfExists\":true}");
        postJson("/api/v1/databases/" + db + "/tables", "{" +
                "\"name\":\"" + table + "\"," +
                "\"type\":\"log\"," +
                "\"ignoreIfExists\":true," +
                "\"bucketCount\":3," +
                "\"schema\":[{\"name\":\"event_id\",\"type\":\"STRING\",\"nullable\":false},{\"name\":\"payload\",\"type\":\"STRING\",\"nullable\":true}]" +
                "}");

        Map<String, Object> sub = postJson("/api/v1/databases/" + db + "/tables/" + table + "/subscriptions", "{\"buckets\":[0,1,2],\"start\":{\"mode\":\"earliest\"}}");
        String subId = String.valueOf(data(sub).get("subscriptionId"));
        Map<String, Object> poll = getJson("/api/v1/databases/" + db + "/tables/" + table + "/subscriptions/" + subId + "/records?limit=10&timeoutMs=1");
        Map<String, Object> d = data(poll);
        assertEquals(subId, d.get("subscriptionId"));
        assertTrue(d.containsKey("records"));
        assertTrue(d.containsKey("positions"));
    }

    private long sumPositions(Map<?, ?> positions) {
        long sum = 0L;
        for (Object value : positions.values()) {
            sum += Long.parseLong(String.valueOf(value));
        }
        return sum;
    }

}
