package com.app.kira.dto.predict;

import com.microsoft.playwright.options.Proxy;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.ResultSet;
import java.sql.SQLException;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProxyDTO {
    private String server;
    private int port;
    private String secret;
    private String username;
    private String password;

    public ProxyDTO(ResultSet rs) throws SQLException {
        this.server = rs.getString("address");
        this.port = rs.getInt("port");
        this.username = rs.getString("username");
        this.password = rs.getString("password");
    }

    public static Proxy toProxyPlayWright(ProxyDTO dto) {
        return new Proxy(dto.getServer() + ":" + dto.getPort())
                .setUsername(dto.getUsername())
                .setPassword(dto.getPassword());
    }
}
