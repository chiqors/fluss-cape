package org.gnuhpc.fluss.cape.http.it;

import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class HttpCompatAuthAndContractIT extends HttpItBase {

    @Test
    void shouldRejectWrongTokenWhenTokenConfigured() throws Exception {
        if (AUTH_TOKEN.isBlank()) {
            return;
        }
        HttpURLConnection conn = requestWithBearerToken("GET", "/api/v1/databases", null, AUTH_TOKEN + "_wrong");
        Map<String, Object> error = readErrorJsonResponse(conn, 401);
        assertEquals(Boolean.FALSE, error.get("ok"));
        Map<?, ?> err = (Map<?, ?>) error.get("error");
        assertNotNull(err);
        assertEquals("unauthorized", err.get("code"));
        assertNotNull(err.get("message"));
    }

    @Test
    void shouldKeepErrorContractForInvalidQueryArgument() throws Exception {
        HttpURLConnection conn = request("GET", "/api/v1/databases/not_exist_db/tables/not_exist_table/records?bucket=not_a_number", null);
        Map<String, Object> error = readErrorJsonResponse(conn, 400);
        assertEquals(Boolean.FALSE, error.get("ok"));
        Map<?, ?> err = (Map<?, ?>) error.get("error");
        assertNotNull(err);
        assertEquals("invalid_argument", err.get("code"));
        assertNotNull(err.get("message"));
    }
}
