package org.gnuhpc.fluss.cape.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HttpApiConstTest {
    @Test
    void shouldExposeExpectedConstants() {
        assertEquals("/api/v1", HttpApiConst.API_V1);
        assertEquals("databases", HttpApiConst.DATABASES);
        assertEquals("subscriptionId", HttpApiConst.SUBSCRIPTION_ID);
        assertEquals("Authorization", HttpApiConst.AUTHORIZATION);
    }
}
