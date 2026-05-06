package org.gnuhpc.fluss.cape.http;

import org.gnuhpc.fluss.cape.http.codec.FlussJsonCodec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FlussJsonCodecTest {

    @Test
    void shouldParseAndSerializeJsonObject() {
        String input = "{\"a\":1,\"b\":true,\"c\":[\"x\",2],\"d\":{\"k\":\"v\"}}";
        Object parsed = FlussJsonCodec.parse(input);
        assertTrue(parsed instanceof Map);

        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) parsed;
        assertEquals(1, ((Number) map.get("a")).intValue());
        assertEquals(Boolean.TRUE, map.get("b"));
        assertTrue(map.get("c") instanceof List);

        String out = FlussJsonCodec.toJson(map);
        assertNotNull(out);
        assertTrue(out.contains("\"a\":1"));
        assertTrue(out.contains("\"b\":true"));
    }

    @Test
    void shouldThrowOnInvalidJson() {
        assertThrows(IllegalArgumentException.class, () -> FlussJsonCodec.parse("{invalid"));
    }
}
