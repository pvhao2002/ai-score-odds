package com.app.kira.server;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LogServer {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ServerInfoService serverInfoService;

    @PostConstruct
    public void setup() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        DatabaseLogAppender dbAppender = new DatabaseLogAppender();
        dbAppender.setContext(loggerContext);
        dbAppender.setHostName(serverInfoService.getHostName());
        dbAppender.setLogRepository(jdbcTemplate);
        dbAppender.start();

        Logger rootLogger = loggerContext.getLogger("ROOT");
        rootLogger.addAppender(dbAppender);
    }
}
