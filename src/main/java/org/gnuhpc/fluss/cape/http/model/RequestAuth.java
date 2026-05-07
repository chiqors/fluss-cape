package org.gnuhpc.fluss.cape.http.model;

public class RequestAuth {
    private final String username;
    private final String password;

    public RequestAuth(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}
