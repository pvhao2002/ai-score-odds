package com.app.kira.server;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.*;
import lombok.extern.java.Log;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@Log
public class ServerInfoService implements ApplicationListener<WebServerInitializedEvent> {
    private static final String MODULE_KEY = "module";
    @Value("${server.servlet.context-path}")
    private String module;
    @Getter
    private String hostName;
    @Getter
    private String ipAddress;
    @Getter
    private String url;
    private final boolean localProfile;
    private final NamedParameterJdbcTemplate db;


    public ServerInfoService(JdbcTemplate db, Environment environment) {
        this.db = new NamedParameterJdbcTemplate(db);
        this.localProfile = Arrays.stream(environment.getActiveProfiles()).anyMatch(it -> List.of("dev", "local", "forceActive").contains(it));
    }

    @PostConstruct
    void init() {
        this.hostName = ServerUtils.getServerHostName();
        this.ipAddress = ServerUtils.getInstanceName();
    }

    @PreDestroy
    void stopInstance() {

    }

    @Override
    public void onApplicationEvent(@NonNull WebServerInitializedEvent event) {
        if (this.localProfile) {
            return;
        }
        this.url = "http://" + ipAddress + ":" + event.getWebServer().getPort() + module;
        saveServerInfo();
    }

    @Scheduled(fixedDelay = 3 * 60 * 1000, initialDelay = 60 * 1000)
    void removeInactiveNode() {

    }

    void saveServerInfo() {
        var params = Map.of("node", hostName, "url", url);
        db.update("""
                INSERT INTO router_setting(node, url)
                VALUES (:node, :url)
                ON DUPLICATE KEY UPDATE last_update = NOW()
                , url = values(url)
                """, params);
    }

    public boolean isActive() {
        return db.queryForObject(
                "select is_active from router_setting where node = :node",
                Map.of("node", hostName),
                Boolean.class
        );
    }

    public boolean isNotActive() {
        return !isActive();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServerActive {
        private int lastActiveMin;
        private String node;
        private String module;

        public ServerActive(ResultSet rs) throws SQLException {
            this.lastActiveMin = rs.getInt("last_update_time");
            this.node = rs.getString("node");
            this.module = rs.getString(MODULE_KEY);
        }
    }
}
