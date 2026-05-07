package org.gnuhpc.fluss.cape.http;

import org.gnuhpc.fluss.cape.common.configuration.CapeConfig;
import org.gnuhpc.fluss.cape.http.config.HTTPCompatConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HTTPCompatConfigTest {

    @Test
    void shouldReadHttpCompatConfigFromCapeConfig() {
        System.setProperty("http.compat.enabled", "true");
        System.setProperty("http.compat.bind.address", "127.0.0.1");
        System.setProperty("http.compat.bind.port", "19090");
        System.setProperty("http.compat.auth.token", "token-1");
        System.setProperty("http.compat.allow.admin.write", "true");
        System.setProperty("http.compat.allow.data.write", "true");
        System.setProperty("http.compat.max.request.bytes", "2048");
        System.setProperty("http.compat.default.limit", "11");
        System.setProperty("http.compat.max.limit", "22");
        System.setProperty("http.compat.subscription.max.count", "33");
        System.setProperty("http.compat.subscription.idle.timeout.ms", "444");

        try {
            CapeConfig capeConfig = new CapeConfig();
            HTTPCompatConfig cfg = HTTPCompatConfig.fromCapeConfig(capeConfig);

            assertTrue(cfg.isEnabled());
            assertEquals("127.0.0.1", cfg.getBindAddress());
            assertEquals(19090, cfg.getBindPort());
            assertEquals("token-1", cfg.getAuthToken());
            assertTrue(cfg.isAllowAdminWrite());
            assertTrue(cfg.isAllowDataWrite());
            assertEquals(2048, cfg.getMaxRequestBytes());
            assertEquals(11, cfg.getDefaultLimit());
            assertEquals(22, cfg.getMaxLimit());
            assertEquals(33, cfg.getSubscriptionMaxCount());
            assertEquals(444L, cfg.getSubscriptionIdleTimeoutMs());
        } finally {
            System.clearProperty("http.compat.enabled");
            System.clearProperty("http.compat.bind.address");
            System.clearProperty("http.compat.bind.port");
            System.clearProperty("http.compat.auth.token");
            System.clearProperty("http.compat.allow.admin.write");
            System.clearProperty("http.compat.allow.data.write");
            System.clearProperty("http.compat.max.request.bytes");
            System.clearProperty("http.compat.default.limit");
            System.clearProperty("http.compat.max.limit");
            System.clearProperty("http.compat.subscription.max.count");
            System.clearProperty("http.compat.subscription.idle.timeout.ms");
        }
    }
}
