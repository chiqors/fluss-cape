package org.gnuhpc.fluss.cape.http.subscription;

import org.apache.fluss.client.table.Table;
import org.apache.fluss.client.table.scanner.log.LogScanner;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HTTPSubscriptionManager implements AutoCloseable {
    public static class Subscription {
        public final String id;
        public final String database;
        public final String table;
        public final LogScanner scanner;
        public final Map<Integer, Long> positions;
        public final long idleTimeoutMs;
        public volatile long lastAccess;

        public Subscription(
                String id,
                String database,
                String table,
                LogScanner scanner,
                Map<Integer, Long> positions,
                long idleTimeoutMs) {
            this.id = id;
            this.database = database;
            this.table = table;
            this.scanner = scanner;
            this.positions = positions;
            this.idleTimeoutMs = idleTimeoutMs;
            this.lastAccess = System.currentTimeMillis();
        }
    }

    private final int maxCount;
    private final long idleTimeoutMs;
    private final ConcurrentHashMap<String, Subscription> subscriptions = new ConcurrentHashMap<>();

    public HTTPSubscriptionManager(int maxCount, long idleTimeoutMs) {
        this.maxCount = maxCount;
        this.idleTimeoutMs = idleTimeoutMs;
    }

    public String create(
            String database,
            String table,
            Table flussTable,
            List<Integer> buckets,
            String mode,
            Map<Integer, Long> offsets,
            long idleTimeoutOverrideMs) throws Exception {
        evictExpired();
        if (subscriptions.size() >= maxCount) {
            throw new IllegalStateException("subscription max count exceeded");
        }

        String id = "sub_" + UUID.randomUUID().toString().replace("-", "");
        LogScanner scanner = flussTable.newScan().createLogScanner();
        Map<Integer, Long> positions = new HashMap<>();
        long effectiveIdleTimeoutMs = idleTimeoutOverrideMs > 0 ? idleTimeoutOverrideMs : idleTimeoutMs;
        for (Integer b : buckets) {
            if ("offset".equals(mode)) {
                long off = offsets.getOrDefault(b, 0L);
                scanner.subscribe(b, off);
                positions.put(b, off);
            } else {
                scanner.subscribeFromBeginning(b);
                positions.put(b, 0L);
            }
        }

        subscriptions.put(id, new Subscription(id, database, table, scanner, positions, effectiveIdleTimeoutMs));
        return id;
    }

    public Subscription get(String id, String database, String table) {
        evictExpired();
        Subscription s = subscriptions.get(id);
        if (s == null) return null;
        if (!s.database.equals(database) || !s.table.equals(table)) return null;
        s.lastAccess = System.currentTimeMillis();
        return s;
    }

    public boolean remove(String id, String database, String table) {
        Subscription s = get(id, database, table);
        if (s == null) {
            return false;
        }
        subscriptions.remove(id);
        try {
            s.scanner.close();
        } catch (Exception ignore) {
        }
        return true;
    }

    private void evictExpired() {
        long now = System.currentTimeMillis();
        List<String> expired = new ArrayList<>();
        for (Map.Entry<String, Subscription> e : subscriptions.entrySet()) {
            long timeout = e.getValue().idleTimeoutMs > 0 ? e.getValue().idleTimeoutMs : idleTimeoutMs;
            if (now - e.getValue().lastAccess > timeout) {
                expired.add(e.getKey());
            }
        }
        for (String id : expired) {
            Subscription s = subscriptions.remove(id);
            if (s != null) {
                try {
                    s.scanner.poll(Duration.ZERO);
                } catch (Exception ignore) {
                }
                try {
                    s.scanner.close();
                } catch (Exception ignore) {
                }
            }
        }
    }

    @Override
    public void close() {
        for (Subscription s : subscriptions.values()) {
            try {
                s.scanner.close();
            } catch (Exception ignore) {
            }
        }
        subscriptions.clear();
    }
}
