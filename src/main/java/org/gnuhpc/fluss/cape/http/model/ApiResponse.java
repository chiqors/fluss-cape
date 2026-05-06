package org.gnuhpc.fluss.cape.http.model;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ApiResponse {
    private ApiResponse() {}

    public static Map<String, Object> ok(Map<String, Object> data) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("ok", true);
        root.put("data", data == null ? new LinkedHashMap<>() : data);
        return root;
    }

    public static Map<String, Object> error(String code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("ok", false);
        root.put("error", error);
        return root;
    }
}
