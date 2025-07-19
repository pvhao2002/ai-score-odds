package com.app.kira.server;

import com.app.kira.spring.ApplicationContextProvider;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.java.Log;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

@Service
@Log
public class ServerInfoService implements ApplicationListener<WebServerInitializedEvent> {
    @Value("${server.servlet.context-path}")
    private String module;
    @Getter
    private String hostName;
    @Getter
    private String ipAddress;
    @Getter
    private String url;
    private final NamedParameterJdbcTemplate db;


    public ServerInfoService(JdbcTemplate db) {
        this.db = new NamedParameterJdbcTemplate(db);
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
        this.url = "http://" + ipAddress + ":" + event.getWebServer().getPort() + module;
        saveServerInfo();
        listScheduledMethods();
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

    public boolean isScheduledMethodActive(String methodName) {
        var sql = "select status from schedule_manager where schedule_name = :schedule_name and host_name = :host_name";
        var params = Map.of(
                "schedule_name", methodName,
                "host_name", hostName
        );
        return Optional.of(db.queryForObject(sql, params, String.class))
                .map("active"::equalsIgnoreCase)
                .orElse(false);
    }

    public void listScheduledMethods() {
        String[] beanNames = ApplicationContextProvider.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            Object bean = ApplicationContextProvider.getBean(beanName);
            Class<?> targetClass = AopUtils.getTargetClass(bean);

            Method[] methods = targetClass.getDeclaredMethods();
            for (Method method : methods) {
                if (method.isAnnotationPresent(Scheduled.class)) {
                    var name = simplifyMethod(targetClass, method);
                    var sql = "insert ignore into schedule_manager(schedule_name, host_name) VALUES (:schedule_name, :host_name)";
                    var params = Map.of(
                            "schedule_name", name,
                            "host_name", hostName
                    );
                    db.update(sql, params);
                }
            }
        }
    }


    private String simplifyMethod(Class<?> targetClass, Method method) {
        return String.format("%s.%s", targetClass.getPackageName(), method.getName());
    }

    public boolean isNotActive() {
        return !isActive();
    }
}
