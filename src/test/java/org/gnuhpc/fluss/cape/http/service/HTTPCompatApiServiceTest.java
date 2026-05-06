package org.gnuhpc.fluss.cape.http.service;

import org.apache.fluss.client.Connection;
import org.apache.fluss.client.admin.Admin;
import org.apache.fluss.client.lookup.LookupResult;
import org.apache.fluss.client.lookup.Lookup;
import org.apache.fluss.client.lookup.Lookuper;
import org.apache.fluss.client.table.Table;
import org.apache.fluss.client.table.scanner.ScanRecord;
import org.apache.fluss.client.table.scanner.log.LogScanner;
import org.apache.fluss.client.table.scanner.log.ScanRecords;
import org.apache.fluss.client.table.writer.Append;
import org.apache.fluss.client.table.writer.AppendWriter;
import org.apache.fluss.client.table.writer.DeleteResult;
import org.apache.fluss.client.table.writer.Upsert;
import org.apache.fluss.client.table.writer.UpsertWriter;
import org.apache.fluss.config.Configuration;
import org.apache.fluss.metadata.DatabaseDescriptor;
import org.apache.fluss.metadata.Schema;
import org.apache.fluss.metadata.TableDescriptor;
import org.apache.fluss.metadata.TableInfo;
import org.apache.fluss.metadata.TablePath;
import org.apache.fluss.metadata.TableBucket;
import org.apache.fluss.record.ChangeType;
import org.apache.fluss.row.BinaryString;
import org.apache.fluss.row.GenericRow;
import org.apache.fluss.row.InternalRow;
import org.apache.fluss.types.DataTypes;
import org.apache.fluss.types.RowType;
import org.gnuhpc.fluss.cape.http.HttpApiConst;
import org.gnuhpc.fluss.cape.http.model.ApiException;
import org.gnuhpc.fluss.cape.http.model.RequestAuth;
import org.gnuhpc.fluss.cape.http.subscription.HTTPSubscriptionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HTTPCompatApiServiceTest {
    private Admin admin;
    private Connection connection;
    private HTTPSubscriptionManager subscriptionManager;
    private HTTPCompatApiService service;

    @BeforeEach
    void setUp() {
        admin = mock(Admin.class);
        connection = mock(Connection.class);
        subscriptionManager = mock(HTTPSubscriptionManager.class);
        service = new HTTPCompatApiService(connection, admin, subscriptionManager);
    }

    @Test
    void shouldListAndCreateDatabase() throws Exception {
        when(admin.listDatabases()).thenReturn(CompletableFuture.completedFuture(List.of("default")));
        when(admin.databaseExists("app")).thenReturn(CompletableFuture.completedFuture(false));
        when(admin.createDatabase("app", DatabaseDescriptor.EMPTY, true)).thenReturn(CompletableFuture.completedFuture(null));

        assertEquals(List.of("default"), service.listDatabases().get(HttpApiConst.DATABASES));
        assertEquals(Boolean.TRUE, service.createDatabase("app", true, new RequestAuth("u", "p")).get(HttpApiConst.CREATED));
    }

    @Test
    void shouldRejectMissingDatabaseAndTable() throws Exception {
        when(admin.databaseExists("missing")).thenReturn(CompletableFuture.completedFuture(false));
        when(admin.tableExists(TablePath.of("db", "missing"))).thenReturn(CompletableFuture.completedFuture(false));

        assertEquals(404, assertThrows(ApiException.class, () -> service.getDatabase("missing")).getStatus());
        assertEquals(404, assertThrows(ApiException.class, () -> service.getTable("db", "missing")).getStatus());
    }

    @Test
    void shouldValidateCreateTableAndReadBackMeta() throws Exception {
        TablePath path = TablePath.of("db", "tbl");
        TableInfo info = mockTableInfo(path, true);

        when(admin.databaseExists("db")).thenReturn(CompletableFuture.completedFuture(true));
        when(admin.tableExists(path)).thenReturn(CompletableFuture.completedFuture(false));
        when(admin.createTable(eq(path), any(TableDescriptor.class), eq(true))).thenReturn(CompletableFuture.completedFuture(null));
        when(admin.tableExists(path)).thenReturn(CompletableFuture.completedFuture(true));
        when(admin.getTableInfo(path)).thenReturn(CompletableFuture.completedFuture(info));

        Map<String, Object> req = new LinkedHashMap<>();
        req.put(HttpApiConst.NAME, "tbl");
        req.put(HttpApiConst.TYPE, "primary_key");
        req.put(HttpApiConst.PRIMARY_KEY, List.of("id"));
        req.put(HttpApiConst.SCHEMA, List.of(Map.of("name", "id", "type", "BIGINT")));

        Map<String, Object> created = service.createTable("db", req, null);
        assertEquals("tbl", created.get(HttpApiConst.TABLE));
        assertEquals("primary_key", created.get(HttpApiConst.TYPE));

        Map<String, Object> table = service.getTable("db", "tbl");
        assertEquals("tbl", table.get(HttpApiConst.TABLE));
        assertEquals(1L, table.get(HttpApiConst.TABLE_ID));
    }

    @Test
    void shouldUpsertLookupDeleteAndAppend() throws Exception {
        TablePath pkPath = TablePath.of("db", "pk");
        TablePath logPath = TablePath.of("db", "log");
        TableInfo pkInfo = mockTableInfo(pkPath, true);
        TableInfo logInfo = mockTableInfo(logPath, false);

        Table pkTable = mock(Table.class);
        Lookup pkLookup = mock(Lookup.class);
        Upsert pkUpsert = mock(Upsert.class);
        UpsertWriter pkWriter = mock(UpsertWriter.class);
        Lookuper lookuper = mock(Lookuper.class);
        LookupResult lookupResult = new LookupResult(List.of(GenericRow.of(1L, BinaryString.fromString("v1"))));

        Table logTable = mock(Table.class);
        Append logAppend = mock(Append.class);
        AppendWriter logWriter = mock(AppendWriter.class);

        when(admin.tableExists(pkPath)).thenReturn(CompletableFuture.completedFuture(true));
        when(admin.getTableInfo(pkPath)).thenReturn(CompletableFuture.completedFuture(pkInfo));
        when(admin.tableExists(logPath)).thenReturn(CompletableFuture.completedFuture(true));
        when(admin.getTableInfo(logPath)).thenReturn(CompletableFuture.completedFuture(logInfo));
        when(connection.getTable(pkPath)).thenReturn(pkTable);
        when(connection.getTable(logPath)).thenReturn(logTable);
        when(pkTable.newUpsert()).thenReturn(pkUpsert);
        when(pkUpsert.createWriter()).thenReturn(pkWriter);
        when(pkTable.newLookup()).thenReturn(pkLookup);
        when(pkLookup.createLookuper()).thenReturn(lookuper);
        when(lookuper.lookup(any())).thenReturn(CompletableFuture.completedFuture(lookupResult));
        when(logTable.newAppend()).thenReturn(logAppend);
        when(logAppend.createWriter()).thenReturn(logWriter);
        when(pkWriter.upsert(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(pkWriter.delete(any())).thenReturn(CompletableFuture.completedFuture(new DeleteResult(new TableBucket(1L, 0), 0L)));
        doNothing().when(pkWriter).flush();
        when(logWriter.append(any())).thenReturn(CompletableFuture.completedFuture(null));
        doNothing().when(logWriter).flush();

        Map<String, Object> upsertReq = Map.of(HttpApiConst.ROWS_FIELD, List.of(Map.of("id", 1L, "v", "v1")));
        Map<String, Object> upsert = service.upsertRows("db", "pk", upsertReq, null);
        assertEquals(1, upsert.get(HttpApiConst.AFFECTED_ROWS));

        Map<String, String> keyParams = Map.of(HttpApiConst.KEY_PREFIX + "id", "1");
        Map<String, Object> lookup = service.getRowByPrimaryKey("db", "pk", keyParams);
        assertEquals(Boolean.TRUE, lookup.get(HttpApiConst.FOUND));

        Map<String, Object> delete = service.deleteRowByPrimaryKey("db", "pk", keyParams, true);
        assertEquals(1, delete.get(HttpApiConst.AFFECTED_ROWS));

        Map<String, Object> appendReq = Map.of(HttpApiConst.RECORDS_FIELD, List.of(Map.of("id", 1L, "v", "x")));
        Map<String, Object> append = service.appendRecords("db", "log", appendReq, null);
        assertEquals(1, append.get(HttpApiConst.AFFECTED_RECORDS));
    }

    @Test
    void shouldReadRecordsAndManageSubscriptions() throws Exception {
        TablePath logPath = TablePath.of("db", "log");
        TableInfo logInfo = mockTableInfo(logPath, false);
        Table logTable = mock(Table.class);
        org.apache.fluss.client.table.scanner.Scan scan = mock(org.apache.fluss.client.table.scanner.Scan.class);
        LogScanner scanner = mock(LogScanner.class);
        ScanRecords records = new ScanRecords(Map.of(new org.apache.fluss.metadata.TableBucket(1L, 0), List.of(
                new ScanRecord(1L, 100L, ChangeType.INSERT, GenericRow.of(10L, BinaryString.fromString("a"))),
                new ScanRecord(2L, 101L, ChangeType.INSERT, GenericRow.of(11L, BinaryString.fromString("b")))
        )));

        when(admin.tableExists(logPath)).thenReturn(CompletableFuture.completedFuture(true));
        when(admin.getTableInfo(logPath)).thenReturn(CompletableFuture.completedFuture(logInfo));
        when(connection.getTable(logPath)).thenReturn(logTable);
        when(logTable.newScan()).thenReturn(scan);
        when(scan.createLogScanner()).thenReturn(scanner);
        when(scanner.poll(any(Duration.class))).thenReturn(records);
        doNothing().when(scanner).close();

        Map<String, Object> read = service.readRecords("db", "log", 0, 0, 1, 100L);
        assertEquals(0, read.get(HttpApiConst.BUCKET));
        assertEquals(1, ((List<?>) read.get(HttpApiConst.RECORDS_FIELD)).size());
        assertEquals(2L, read.get("nextOffset"));
        assertEquals(Boolean.TRUE, read.get("truncated"));

        when(subscriptionManager.create(eq("db"), eq("log"), eq(logTable), anyList(), eq("earliest"), anyMap(), eq(-1L)))
                .thenReturn("sub-1");
        Map<String, Object> created = service.createSubscription("db", "log", List.of(0), "earliest", Map.of(), Map.of(), null);
        assertEquals("sub-1", created.get(HttpApiConst.SUBSCRIPTION_ID));

        HTTPSubscriptionManager.Subscription sub = new HTTPSubscriptionManager.Subscription(
                "sub-1", "db", "log", scanner, new LinkedHashMap<>(), 1000L);
        when(subscriptionManager.get("sub-1", "db", "log")).thenReturn(sub);
        Map<String, Object> polled = service.pollSubscription("db", "log", "sub-1", 10, 100L);
        assertEquals("sub-1", polled.get(HttpApiConst.SUBSCRIPTION_ID));

        when(subscriptionManager.remove("sub-1", "db", "log")).thenReturn(true);
        assertEquals(Boolean.TRUE, service.deleteSubscription("db", "log", "sub-1").get("closed"));
    }

    @Test
    void shouldRejectTypeAndFieldErrors() throws Exception {
        when(admin.databaseExists("db")).thenReturn(CompletableFuture.completedFuture(true));

        Map<String, Object> badCreate = new LinkedHashMap<>();
        badCreate.put(HttpApiConst.NAME, "tbl");
        badCreate.put(HttpApiConst.TYPE, "primary_key");
        badCreate.put(HttpApiConst.SCHEMA, List.of(Map.of("name", "id", "type", "NOPE")));
        assertThrows(ApiException.class, () -> service.createTable("db", badCreate, null));

        TablePath path = TablePath.of("db", "pk");
        TableInfo info = mockTableInfo(path, true);
        Table table = mock(Table.class);
        Upsert upsert = mock(Upsert.class);
        UpsertWriter writer = mock(UpsertWriter.class);
        when(admin.tableExists(path)).thenReturn(CompletableFuture.completedFuture(true));
        when(admin.getTableInfo(path)).thenReturn(CompletableFuture.completedFuture(info));
        when(connection.getTable(path)).thenReturn(table);
        when(table.newUpsert()).thenReturn(upsert);
        when(upsert.createWriter()).thenReturn(writer);

        Map<String, Object> badRow = Map.of(HttpApiConst.ROWS_FIELD, List.of(Map.of("unknown", 1)));
        assertThrows(ApiException.class, () -> service.upsertRows("db", "pk", badRow, null));
    }

    @Test
    void shouldRejectCreateTableValidationErrors() throws Exception {
        when(admin.databaseExists("db")).thenReturn(CompletableFuture.completedFuture(true));

        Map<String, Object> zeroBucket = new LinkedHashMap<>();
        zeroBucket.put(HttpApiConst.NAME, "tbl");
        zeroBucket.put(HttpApiConst.TYPE, "primary_key");
        zeroBucket.put(HttpApiConst.PRIMARY_KEY, List.of("id"));
        zeroBucket.put(HttpApiConst.BUCKET_COUNT, 0);
        zeroBucket.put(HttpApiConst.SCHEMA, List.of(Map.of("name", "id", "type", "BIGINT")));
        assertEquals(400, assertThrows(ApiException.class, () -> service.createTable("db", zeroBucket, null)).getStatus());

        Map<String, Object> missingPk = new LinkedHashMap<>();
        missingPk.put(HttpApiConst.NAME, "tbl");
        missingPk.put(HttpApiConst.TYPE, "primary_key");
        missingPk.put(HttpApiConst.SCHEMA, List.of(Map.of("name", "id", "type", "BIGINT")));
        assertEquals(400, assertThrows(ApiException.class, () -> service.createTable("db", missingPk, null)).getStatus());

        Map<String, Object> logWithPk = new LinkedHashMap<>();
        logWithPk.put(HttpApiConst.NAME, "tbl");
        logWithPk.put(HttpApiConst.TYPE, "log");
        logWithPk.put(HttpApiConst.PRIMARY_KEY, List.of("id"));
        logWithPk.put(HttpApiConst.SCHEMA, List.of(Map.of("name", "id", "type", "BIGINT")));
        assertEquals(400, assertThrows(ApiException.class, () -> service.createTable("db", logWithPk, null)).getStatus());
    }

    @Test
    void shouldRejectMissingPrimaryKeyOnRowOperations() throws Exception {
        TablePath path = TablePath.of("db", "pk");
        TableInfo info = mockTableInfo(path, true);
        Table table = mock(Table.class);
        Upsert upsert = mock(Upsert.class);
        UpsertWriter writer = mock(UpsertWriter.class);

        when(admin.tableExists(path)).thenReturn(CompletableFuture.completedFuture(true));
        when(admin.getTableInfo(path)).thenReturn(CompletableFuture.completedFuture(info));
        when(connection.getTable(path)).thenReturn(table);
        when(table.newUpsert()).thenReturn(upsert);
        when(upsert.createWriter()).thenReturn(writer);

        Map<String, Object> noPkRow = Map.of(HttpApiConst.ROWS_FIELD, List.of(Map.of("v", "x")));
        assertEquals(400, assertThrows(ApiException.class, () -> service.upsertRows("db", "pk", noPkRow, null)).getStatus());

        Map<String, String> missingKey = Map.of();
        assertEquals(400, assertThrows(ApiException.class, () -> service.getRowByPrimaryKey("db", "pk", missingKey)).getStatus());
        assertEquals(400, assertThrows(ApiException.class, () -> service.deleteRowByPrimaryKey("db", "pk", missingKey, true)).getStatus());
    }

    @Test
    void shouldRejectMissingSubscriptionAndDeleteSubscription() throws Exception {
        when(subscriptionManager.get("sub-1", "db", "log")).thenReturn(null);
        assertEquals(404, assertThrows(ApiException.class, () -> service.pollSubscription("db", "log", "sub-1", 10, 100L)).getStatus());

        when(subscriptionManager.remove("sub-1", "db", "log")).thenReturn(false);
        assertEquals(404, assertThrows(ApiException.class, () -> service.deleteSubscription("db", "log", "sub-1")).getStatus());
    }

    private TableInfo mockTableInfo(TablePath path, boolean primaryKey) {
        Schema.Builder schema = Schema.newBuilder().column("id", DataTypes.BIGINT());
        if (primaryKey) {
            schema.primaryKey("id");
            schema.column("v", DataTypes.STRING());
        } else {
            schema.column("v", DataTypes.STRING());
        }
        TableDescriptor descriptor = TableDescriptor.builder().schema(schema.build()).build().withBucketCount(1);
        return TableInfo.of(path, 1L, 1, descriptor, 1L, 1L);
    }
}
