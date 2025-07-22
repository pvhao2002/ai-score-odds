package com.app.kira.server;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.app.kira.model.LogEntity;
import lombok.Setter;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class DatabaseLogAppender extends AppenderBase<ILoggingEvent> {
    private static final String SQL_INSERT_LOG = "insert into app_logs(host_name, level, logger, thread, message) VALUES (:hostName, :level, :logger, :thread, :message)";
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    @Setter
    private String hostName;

    public void setLogRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.namedParameterJdbcTemplate = jdbcTemplate;
    }

    @Override
    protected void append(ILoggingEvent eventObject) {
        try {
            LogEntity log = new LogEntity();
            log.setLevel(eventObject.getLevel().toString());
            log.setMessage(eventObject.getFormattedMessage());
            log.setLogger(eventObject.getLoggerName());
            log.setThread(eventObject.getThreadName());
            log.setHostName(hostName);
            namedParameterJdbcTemplate.update(SQL_INSERT_LOG, log.toParam());
        } catch (Exception e) {
            System.err.println("Failed to log to database: " + e.getMessage());
        }
    }
}
