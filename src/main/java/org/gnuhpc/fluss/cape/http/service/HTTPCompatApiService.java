package org.gnuhpc.fluss.cape.http.service;

import org.apache.fluss.client.Connection;
import org.apache.fluss.client.admin.Admin;
import org.apache.fluss.client.lookup.LookupResult;
import org.apache.fluss.client.lookup.Lookuper;
import org.apache.fluss.client.table.Table;
import org.apache.fluss.client.table.scanner.ScanRecord;
import org.apache.fluss.client.table.scanner.log.ScanRecords;
import org.apache.fluss.client.table.writer.AppendWriter;
import org.apache.fluss.client.table.writer.UpsertWriter;
import org.apache.fluss.metadata.DatabaseDescriptor;
import org.apache.fluss.metadata.Schema;
import org.apache.fluss.metadata.TableDescriptor;
import org.apache.fluss.metadata.TableInfo;
import org.apache.fluss.metadata.TablePath;
import org.apache.fluss.row.BinaryString;
import org.apache.fluss.row.GenericRow;
import org.apache.fluss.row.InternalRow;
import org.apache.fluss.row.TimestampLtz;
import org.apache.fluss.row.TimestampNtz;
import org.apache.fluss.types.DataField;
import org.apache.fluss.types.DataType;
import org.apache.fluss.types.DataTypeRoot;
import org.apache.fluss.types.DataTypes;
import org.apache.fluss.types.RowType;
import org.gnuhpc.fluss.cape.http.HttpApiConst;
import org.gnuhpc.fluss.cape.http.model.ApiException;
import org.gnuhpc.fluss.cape.http.model.RequestAuth;
import org.gnuhpc.fluss.cape.http.subscription.HTTPSubscriptionManager;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class HTTPCompatApiService {
    private final Connection connection;
    private final Admin admin;
    private final HTTPSubscriptionManager subscriptionManager;

    public HTTPCompatApiService(Connection connection, Admin admin, HTTPSubscriptionManager subscriptionManager) {
        this.connection = connection;
        this.admin = admin;
        this.subscriptionManager = subscriptionManager;
    }

    public Map<String, Object> listDatabases() throws Exception {
        List<String> databases = admin.listDatabases().get();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(HttpApiConst.DATABASES, databases);
        return data;
    }

    public Map<String, Object> createDatabase(String name, boolean ignoreIfExists, RequestAuth auth) throws Exception {
        boolean existed = admin.databaseExists(name).get();
        admin.createDatabase(name, DatabaseDescriptor.EMPTY, ignoreIfExists).get();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(HttpApiConst.DATABASE, name);
        data.put(HttpApiConst.CREATED, !existed);
        return data;
    }

    public Map<String, Object> getDatabase(String db) throws Exception {
        boolean exists = admin.databaseExists(db).get();
        if (!exists) {
            throw new ApiException(404, "database_not_found", "Database not found: " + db);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(HttpApiConst.DATABASE, db);
        data.put(HttpApiConst.EXISTS, true);
        return data;
    }

    public Map<String, Object> listTables(String db) throws Exception {
        ensureDatabaseExists(db);
        List<String> tables = admin.listTables(db).get();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(HttpApiConst.DATABASE, db);
        data.put(HttpApiConst.TABLES, tables);
        return data;
    }

    public Map<String, Object> getTable(String db, String table) throws Exception {
        TableInfo info = getTableInfo(db, table);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(HttpApiConst.DATABASE, db);
        data.put(HttpApiConst.TABLE, table);
        data.put(HttpApiConst.TABLE_ID, info.getTableId());
        data.put(HttpApiConst.BUCKET_COUNT, info.getNumBuckets());
        data.put(HttpApiConst.PRIMARY_KEY, info.getPrimaryKeys());
        data.put(HttpApiConst.TYPE, inferTableType(info));
        data.put(HttpApiConst.SCHEMA, schemaJson(info.getRowType()));
        data.put(HttpApiConst.PROPERTIES, new LinkedHashMap<>());
        return data;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> createTable(String db, Map<String, Object> req, RequestAuth auth) throws Exception {
        ensureDatabaseExists(db);
        String table = string(req.get(HttpApiConst.NAME), "name is required");
        String type = string(req.get(HttpApiConst.TYPE), "type is required");
        boolean ignoreIfExists = bool(req.get(HttpApiConst.IGNORE_IF_EXISTS), true);
        int bucketCount = intVal(req.get(HttpApiConst.BUCKET_COUNT), 3);

        List<Object> schemaList = list(req.get(HttpApiConst.SCHEMA), "schema is required");
        List<String> primaryKey = new ArrayList<>();
        if (req.get(HttpApiConst.PRIMARY_KEY) instanceof List) {
            for (Object v : (List<Object>) req.get(HttpApiConst.PRIMARY_KEY)) {
                primaryKey.add(String.valueOf(v));
            }
        }

        if (bucketCount <= 0) {
            throw new ApiException(400, "invalid_argument", "bucketCount must be > 0");
        }
        if ("primary_key".equals(type) && primaryKey.isEmpty()) {
            throw new ApiException(400, "missing_primary_key", "primaryKey is required for primary_key table");
        }
        if ("log".equals(type) && !primaryKey.isEmpty()) {
            throw new ApiException(400, "invalid_argument", "primaryKey is not allowed for log table");
        }

        Schema.Builder sb = Schema.newBuilder();
        for (Object o : schemaList) {
            if (!(o instanceof Map)) {
                throw new ApiException(400, "invalid_argument", "schema item must be object");
            }
            Map<String, Object> c = (Map<String, Object>) o;
            String colName = string(c.get("name"), "schema.name is required");
            String colType = string(c.get("type"), "schema.type is required");
            boolean nullable = bool(c.get("nullable"), true);
            DataType dt = parseType(colType);
            if (!nullable) {
                dt = dt.copy(false);
            }
            sb.column(colName, dt);
        }
        if (!primaryKey.isEmpty()) {
            sb.primaryKey(primaryKey.toArray(new String[0]));
        }

        TablePath tablePath = TablePath.of(db, table);
        TableDescriptor descriptor = TableDescriptor.builder().schema(sb.build()).build().withBucketCount(bucketCount);
        boolean existed = admin.tableExists(tablePath).get();
        admin.createTable(tablePath, descriptor, ignoreIfExists).get();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put(HttpApiConst.DATABASE, db);
        data.put(HttpApiConst.TABLE, table);
        data.put("type", type);
        data.put(HttpApiConst.CREATED, !existed);
        return data;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> upsertRows(String db, String table, Map<String, Object> req, RequestAuth auth) throws Exception {
        TableInfo info = getTableInfo(db, table);
        ensurePrimaryKeyTable(info);

        List<Object> rows = list(req.get(HttpApiConst.ROWS_FIELD), "rows is required");
        boolean flush = bool(req.get(HttpApiConst.FLUSH), true);
        RowType rowType = info.getRowType();

        Table t = connection.getTable(TablePath.of(db, table));
        UpsertWriter writer = t.newUpsert().createWriter();
        int affected = 0;
        for (Object rowObj : rows) {
            if (!(rowObj instanceof Map)) {
                throw new ApiException(400, "invalid_argument", "row must be object");
            }
            Map<String, Object> rowMap = (Map<String, Object>) rowObj;
            validatePkPresent(info, rowMap);
            GenericRow row = convertRow(rowType, rowMap);
            writer.upsert(row).join();
            affected++;
        }
        if (flush) {
            writer.flush();
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put(HttpApiConst.DATABASE, db);
        data.put(HttpApiConst.TABLE, table);
        data.put(HttpApiConst.AFFECTED_ROWS, affected);
        data.put(HttpApiConst.FLUSHED, flush);
        return data;
    }

    public Map<String, Object> getRowByPrimaryKey(String db, String table, Map<String, String> keyParams)
            throws Exception {
        TableInfo info = getTableInfo(db, table);
        ensurePrimaryKeyTable(info);
        RowType rowType = info.getRowType();

        GenericRow keyRow = new GenericRow(rowType.getFieldCount());
        Map<String, Integer> indexByName = indexByName(rowType);
        for (String pk : info.getPrimaryKeys()) {
            String val = keyParams.get(HttpApiConst.KEY_PREFIX + pk);
            if (val == null) {
                throw new ApiException(400, "missing_primary_key", "Missing key." + pk);
            }
            int idx = indexByName.get(pk);
            keyRow.setField(idx, convertStringValue(val, rowType.getFields().get(idx).getType()));
        }

        Table t = connection.getTable(TablePath.of(db, table));
        Lookuper lookuper = t.newLookup().createLookuper();
        LookupResult result = lookuper.lookup(keyRow).get();
        List<InternalRow> rows = result == null ? List.of() : result.getRowList();
        boolean found = rows != null && !rows.isEmpty();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put(HttpApiConst.DATABASE, db);
        data.put(HttpApiConst.TABLE, table);
        data.put(HttpApiConst.FOUND, found);
        data.put("row", found ? extractRow(rows.get(0), rowType) : null);
        return data;
    }

    public Map<String, Object> deleteRowByPrimaryKey(
            String db, String table, Map<String, String> keyParams, boolean flush) throws Exception {
        TableInfo info = getTableInfo(db, table);
        ensurePrimaryKeyTable(info);
        RowType rowType = info.getRowType();

        GenericRow keyRow = new GenericRow(rowType.getFieldCount());
        Map<String, Integer> indexByName = indexByName(rowType);
        for (String pk : info.getPrimaryKeys()) {
            String val = keyParams.get(HttpApiConst.KEY_PREFIX + pk);
            if (val == null) {
                throw new ApiException(400, "missing_primary_key", "Missing key." + pk);
            }
            int idx = indexByName.get(pk);
            keyRow.setField(idx, convertStringValue(val, rowType.getFields().get(idx).getType()));
        }

        Table t = connection.getTable(TablePath.of(db, table));
        UpsertWriter writer = t.newUpsert().createWriter();
        writer.delete(keyRow).join();
        if (flush) writer.flush();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put(HttpApiConst.DATABASE, db);
        data.put(HttpApiConst.TABLE, table);
        data.put("affectedRows", 1);
        data.put(HttpApiConst.FLUSHED, flush);
        return data;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> appendRecords(String db, String table, Map<String, Object> req, RequestAuth auth) throws Exception {
        TableInfo info = getTableInfo(db, table);
        ensureLogTable(info);

        List<Object> records = list(req.get("records"), "records is required");
        boolean flush = bool(req.get(HttpApiConst.FLUSH), true);

        Table t = connection.getTable(TablePath.of(db, table));
        AppendWriter writer = t.newAppend().createWriter();
        int affected = 0;
        for (Object recObj : records) {
            if (!(recObj instanceof Map)) {
                throw new ApiException(400, "invalid_argument", "record must be object");
            }
            GenericRow row = convertRow(info.getRowType(), (Map<String, Object>) recObj);
            writer.append(row).get();
            affected++;
        }
        if (flush) writer.flush();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put(HttpApiConst.DATABASE, db);
        data.put(HttpApiConst.TABLE, table);
        data.put("affectedRecords", affected);
        data.put(HttpApiConst.FLUSHED, flush);
        return data;
    }

    public Map<String, Object> readRecords(
            String db, String table, int bucket, long offset, int limit, long timeoutMs) throws Exception {
        TableInfo info = getTableInfo(db, table);
        ensureLogTable(info);

        Table t = connection.getTable(TablePath.of(db, table));
        org.apache.fluss.client.table.scanner.log.LogScanner scanner = t.newScan().createLogScanner();
        scanner.subscribe(bucket, offset);

        ScanRecords polled = scanner.poll(Duration.ofMillis(timeoutMs));
        List<Map<String, Object>> out = new ArrayList<>();
        long nextOffset = offset;
        int count = 0;
        if (polled != null) {
            for (org.apache.fluss.metadata.TableBucket tableBucket : polled.buckets()) {
                for (ScanRecord r : polled.records(tableBucket)) {
                    if (count >= limit) break;
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("offset", r.logOffset());
                    item.put("row", extractRow(r.getRow(), info.getRowType()));
                    out.add(item);
                    nextOffset = Math.max(nextOffset, r.logOffset() + 1);
                    count++;
                }
            }
        }
        scanner.close();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put(HttpApiConst.DATABASE, db);
        data.put(HttpApiConst.TABLE, table);
        data.put("bucket", bucket);
        data.put("startOffset", offset);
        data.put("nextOffset", nextOffset);
        data.put("records", out);
        data.put("truncated", count >= limit);
        return data;
    }

    public Map<String, Object> createSubscription(
            String db,
            String table,
            List<Integer> buckets,
            String mode,
            Map<Integer, Long> offsets,
            Map<String, Object> req,
            RequestAuth auth)
            throws Exception {
        TableInfo info = getTableInfo(db, table);
        ensureLogTable(info);

        Table t = connection.getTable(TablePath.of(db, table));
        long idleTimeoutMs = reqOrNullMaxIdle(req);
        String subId = subscriptionManager.create(db, table, t, buckets, mode, offsets, idleTimeoutMs);

        Map<String, Object> start = new LinkedHashMap<>();
        start.put("mode", mode);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put(HttpApiConst.DATABASE, db);
        data.put(HttpApiConst.TABLE, table);
        data.put("subscriptionId", subId);
        data.put("buckets", buckets);
        data.put("start", start);
        return data;
    }

    private long reqOrNullMaxIdle(Map<String, Object> req) {
        Object maxIdle = req.get("maxIdleMs");
        if (maxIdle instanceof Number) {
            return ((Number) maxIdle).longValue();
        }
        if (maxIdle instanceof String && !String.valueOf(maxIdle).isBlank()) {
            return Long.parseLong(String.valueOf(maxIdle));
        }
        return -1L;
    }

    public Map<String, Object> pollSubscription(
            String db, String table, String subId, int limit, long timeoutMs) throws Exception {
        HTTPSubscriptionManager.Subscription s = subscriptionManager.get(subId, db, table);
        if (s == null) {
            throw new ApiException(404, "subscription_not_found", "Subscription not found: " + subId);
        }

        TableInfo info = getTableInfo(db, table);
        ScanRecords polled = s.scanner.poll(Duration.ofMillis(timeoutMs));
        List<Map<String, Object>> records = new ArrayList<>();
        int count = 0;
        if (polled != null) {
            for (org.apache.fluss.metadata.TableBucket tableBucket : polled.buckets()) {
                for (ScanRecord r : polled.records(tableBucket)) {
                    if (count >= limit) break;
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("bucket", tableBucket.getBucket());
                    item.put("offset", r.logOffset());
                    item.put("row", extractRow(r.getRow(), info.getRowType()));
                    records.add(item);
                    s.positions.put(tableBucket.getBucket(), r.logOffset() + 1);
                    count++;
                }
            }
        }

        Map<String, Object> positions = new LinkedHashMap<>();
        for (Map.Entry<Integer, Long> e : s.positions.entrySet()) {
            positions.put(String.valueOf(e.getKey()), e.getValue());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("subscriptionId", subId);
        data.put("records", records);
        data.put("positions", positions);
        return data;
    }

    public Map<String, Object> deleteSubscription(String db, String table, String subId) {
        boolean closed = subscriptionManager.remove(subId, db, table);
        if (!closed) {
            throw new ApiException(404, "subscription_not_found", "Subscription not found: " + subId);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("subscriptionId", subId);
        data.put("closed", true);
        return data;
    }

    private TableInfo getTableInfo(String db, String table) throws Exception {
        TablePath path = TablePath.of(db, table);
        if (!admin.tableExists(path).get()) {
            throw new ApiException(404, "table_not_found", "Table not found: " + db + "." + table);
        }
        return admin.getTableInfo(path).get();
    }

    private void ensureDatabaseExists(String db) throws Exception {
        if (!admin.databaseExists(db).get()) {
            throw new ApiException(404, "database_not_found", "Database not found: " + db);
        }
    }

    private void ensurePrimaryKeyTable(TableInfo info) {
        if (info.getPrimaryKeys() == null || info.getPrimaryKeys().isEmpty()) {
            throw new ApiException(409, "table_type_mismatch", "Table is not a primary_key table");
        }
    }

    private void ensureLogTable(TableInfo info) {
        if (info.getPrimaryKeys() != null && !info.getPrimaryKeys().isEmpty()) {
            throw new ApiException(409, "table_type_mismatch", "Table is not a log table");
        }
    }

    private String inferTableType(TableInfo info) {
        return (info.getPrimaryKeys() == null || info.getPrimaryKeys().isEmpty()) ? "log" : "primary_key";
    }

    private List<Map<String, Object>> schemaJson(RowType rowType) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (DataField f : rowType.getFields()) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("name", f.getName());
            c.put("type", f.getType().toString());
            c.put("nullable", true);
            out.add(c);
        }
        return out;
    }

    private GenericRow convertRow(RowType rowType, Map<String, Object> values) {
        GenericRow row = new GenericRow(rowType.getFieldCount());
        Map<String, Integer> indexByName = indexByName(rowType);

        for (String k : values.keySet()) {
            if (!indexByName.containsKey(k)) {
                throw new ApiException(400, "unknown_field", "Unknown field: " + k);
            }
        }

        for (DataField f : rowType.getFields()) {
            Integer idx = indexByName.get(f.getName());
            Object v = values.get(f.getName());
            if (v == null && !f.getType().isNullable()) {
                throw new ApiException(400, "invalid_argument", "Field is not nullable: " + f.getName());
            }
            row.setField(idx, convertValue(v, f.getType()));
        }
        return row;
    }

    private Map<String, Integer> indexByName(RowType rowType) {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i < rowType.getFieldCount(); i++) {
            m.put(rowType.getFields().get(i).getName(), i);
        }
        return m;
    }

    private void validatePkPresent(TableInfo info, Map<String, Object> rowMap) {
        for (String pk : info.getPrimaryKeys()) {
            if (!rowMap.containsKey(pk)) {
                throw new ApiException(400, "missing_primary_key", "Missing primary key field: " + pk);
            }
        }
    }

    private Map<String, Object> extractRow(InternalRow row, RowType rowType) {
        Map<String, Object> m = new LinkedHashMap<>();
        InternalRow.FieldGetter[] getters = InternalRow.createFieldGetters(rowType);
        for (int i = 0; i < rowType.getFieldCount(); i++) {
            DataField f = rowType.getFields().get(i);
            Object v = row.isNullAt(i) ? null : getters[i].getFieldOrNull(row);
            m.put(f.getName(), toJsonValue(v, f.getType()));
        }
        return m;
    }

    private Object toJsonValue(Object value, DataType dataType) {
        if (value == null) return null;
        DataTypeRoot root = dataType.getTypeRoot();
        if (value instanceof BinaryString) return value.toString();
        if (root == DataTypeRoot.BYTES || root == DataTypeRoot.BINARY) {
            return Base64.getEncoder().encodeToString((byte[]) value);
        }
        if (root == DataTypeRoot.DATE) {
            if (value instanceof LocalDate) {
                return value.toString();
            }
            if (value instanceof Number) {
                long days = ((Number) value).longValue();
                return LocalDate.of(2000, 1, 1).plusDays(days).toString();
            }
        }
        if (root == DataTypeRoot.TIMESTAMP_WITHOUT_TIME_ZONE || root == DataTypeRoot.TIMESTAMP_WITH_LOCAL_TIME_ZONE) {
            if (value instanceof LocalDateTime) {
                return value.toString();
            }
            if (value instanceof Number) {
                long micros = ((Number) value).longValue();
                return LocalDateTime.of(2000, 1, 1, 0, 0).plusNanos(micros * 1000L).toString();
            }
        }
        if (value instanceof LocalDate) return value.toString();
        if (value instanceof LocalDateTime) return value.toString();
        return value;
    }

    private Object convertStringValue(String value, DataType type) {
        return convertValue(value, type);
    }

    private Object convertValue(Object value, DataType type) {
        if (value == null) return null;
        DataTypeRoot root = type.getTypeRoot();
        String s = String.valueOf(value);
        try {
            switch (root) {
                case BOOLEAN:
                    if (value instanceof Boolean) {
                        return value;
                    }
                    if ("true".equalsIgnoreCase(s)) {
                        return true;
                    }
                    if ("false".equalsIgnoreCase(s)) {
                        return false;
                    }
                    throw new ApiException(400, "invalid_argument", "Invalid value for type BOOLEAN: " + value);
                case TINYINT:
                    return Byte.parseByte(s);
                case SMALLINT:
                    return Short.parseShort(s);
                case INTEGER:
                    return Integer.parseInt(s);
                case BIGINT:
                    return Long.parseLong(s);
                case FLOAT:
                    return Float.parseFloat(s);
                case DOUBLE:
                    return Double.parseDouble(s);
                case CHAR:
                case STRING:
                    return BinaryString.fromString(s);
                case BYTES:
                case BINARY:
                    if (value instanceof String) {
                        return Base64.getDecoder().decode(s);
                    }
                    if (value instanceof byte[]) {
                        return value;
                    }
                    return Base64.getDecoder().decode(String.valueOf(value));
                case DATE:
                    return (int) (LocalDate.parse(s).toEpochDay() - LocalDate.of(2000, 1, 1).toEpochDay());
                case TIMESTAMP_WITHOUT_TIME_ZONE:
                case TIMESTAMP_WITH_LOCAL_TIME_ZONE:
                    if (value instanceof Number) {
                        long millis = ((Number) value).longValue();
                        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
                        return root == DataTypeRoot.TIMESTAMP_WITH_LOCAL_TIME_ZONE
                                ? TimestampLtz.fromEpochMicros(ChronoUnit.MICROS.between(LocalDateTime.of(2000, 1, 1, 0, 0), dateTime))
                                : TimestampNtz.fromMicros(ChronoUnit.MICROS.between(LocalDateTime.of(2000, 1, 1, 0, 0), dateTime));
                    }
                    long micros = ChronoUnit.MICROS.between(
                            LocalDateTime.of(2000, 1, 1, 0, 0),
                            LocalDateTime.parse(s.replace(' ', 'T')));
                    return root == DataTypeRoot.TIMESTAMP_WITH_LOCAL_TIME_ZONE
                            ? TimestampLtz.fromEpochMicros(micros)
                            : TimestampNtz.fromMicros(micros);
                default:
                    throw new ApiException(400, "unsupported_type", "Unsupported type in MVP: " + root);
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(400, "invalid_argument", "Invalid value for type " + root + ": " + value);
        }
    }

    private DataType parseType(String type) {
        String t = type.trim().toUpperCase();
        switch (t) {
            case "BOOLEAN": return DataTypes.BOOLEAN();
            case "TINYINT": return DataTypes.TINYINT();
            case "SMALLINT": return DataTypes.SMALLINT();
            case "INT":
            case "INTEGER": return DataTypes.INT();
            case "BIGINT": return DataTypes.BIGINT();
            case "FLOAT": return DataTypes.FLOAT();
            case "DOUBLE": return DataTypes.DOUBLE();
            case "CHAR": return DataTypes.CHAR(255);
            case "VARCHAR":
            case "STRING": return DataTypes.STRING();
            case "BYTES":
            case "BINARY": return DataTypes.BYTES();
            case "DATE": return DataTypes.DATE();
            case "TIMESTAMP": return DataTypes.TIMESTAMP(3);
            default:
                throw new ApiException(400, "unsupported_type", "Unsupported type in MVP: " + t);
        }
    }

    private static String string(Object v, String err) {
        if (v == null) throw new ApiException(400, "invalid_argument", err);
        return String.valueOf(v);
    }

    private static boolean bool(Object v, boolean defVal) {
        if (v == null) return defVal;
        if (v instanceof Boolean) return (Boolean) v;
        return Boolean.parseBoolean(String.valueOf(v));
    }

    private static int intVal(Object v, int defVal) {
        if (v == null) return defVal;
        if (v instanceof Number) return ((Number) v).intValue();
        return Integer.parseInt(String.valueOf(v));
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object v, String err) {
        if (v == null) throw new ApiException(400, "invalid_argument", err);
        if (!(v instanceof List)) throw new ApiException(400, "invalid_argument", err);
        return (List<Object>) v;
    }
}
