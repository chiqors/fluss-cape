package org.gnuhpc.fluss.cape.http.config;

import org.gnuhpc.fluss.cape.common.configuration.CapeConfig;

public class HTTPCompatConfig {
    private final boolean enabled;
    private final String bindAddress;
    private final int bindPort;
    private final String authToken;
    private final boolean allowAdminWrite;
    private final boolean allowDataWrite;
    private final int maxRequestBytes;
    private final int defaultLimit;
    private final int maxLimit;
    private final int subscriptionMaxCount;
    private final long subscriptionIdleTimeoutMs;

    public HTTPCompatConfig(
            boolean enabled,
            String bindAddress,
            int bindPort,
            String authToken,
            boolean allowAdminWrite,
            boolean allowDataWrite,
            int maxRequestBytes,
            int defaultLimit,
            int maxLimit,
            int subscriptionMaxCount,
            long subscriptionIdleTimeoutMs) {
        this.enabled = enabled;
        this.bindAddress = bindAddress;
        this.bindPort = bindPort;
        this.authToken = authToken;
        this.allowAdminWrite = allowAdminWrite;
        this.allowDataWrite = allowDataWrite;
        this.maxRequestBytes = maxRequestBytes;
        this.defaultLimit = defaultLimit;
        this.maxLimit = maxLimit;
        this.subscriptionMaxCount = subscriptionMaxCount;
        this.subscriptionIdleTimeoutMs = subscriptionIdleTimeoutMs;
    }

    public static HTTPCompatConfig fromCapeConfig(CapeConfig capeConfig) {
        return new HTTPCompatConfig(
                capeConfig.isHttpCompatEnabled(),
                capeConfig.getHttpCompatBindAddress(),
                capeConfig.getHttpCompatBindPort(),
                capeConfig.getHttpCompatAuthToken(),
                capeConfig.isHttpCompatAllowAdminWrite(),
                capeConfig.isHttpCompatAllowDataWrite(),
                capeConfig.getHttpCompatMaxRequestBytes(),
                capeConfig.getHttpCompatDefaultLimit(),
                capeConfig.getHttpCompatMaxLimit(),
                capeConfig.getHttpCompatSubscriptionMaxCount(),
                capeConfig.getHttpCompatSubscriptionIdleTimeoutMs());
    }

    public boolean isEnabled() { return enabled; }
    public String getBindAddress() { return bindAddress; }
    public int getBindPort() { return bindPort; }
    public String getAuthToken() { return authToken; }
    public boolean isAllowAdminWrite() { return allowAdminWrite; }
    public boolean isAllowDataWrite() { return allowDataWrite; }
    public int getMaxRequestBytes() { return maxRequestBytes; }
    public int getDefaultLimit() { return defaultLimit; }
    public int getMaxLimit() { return maxLimit; }
    public int getSubscriptionMaxCount() { return subscriptionMaxCount; }
    public long getSubscriptionIdleTimeoutMs() { return subscriptionIdleTimeoutMs; }
}
