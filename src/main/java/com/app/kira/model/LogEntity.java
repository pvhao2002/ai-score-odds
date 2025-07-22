package com.app.kira.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

@Data
@Table("app_logs")
@AllArgsConstructor
@NoArgsConstructor
public class LogEntity {
    @Id
    private Long logId;
    private String hostName;
    private String level;
    private String logger;
    private String thread;
    private String message;
    private LocalDateTime createdAt;

    public MapSqlParameterSource toParam() {
        return new MapSqlParameterSource()
                .addValue("hostName", hostName)
                .addValue("level", level)
                .addValue("logger", logger)
                .addValue("thread", thread)
                .addValue("message", message);
    }

    public String formatMessage() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

        String timestamp = createdAt != null
                ? createdAt.format(formatter)
                : formatter.format(LocalDateTime.now());

        return String.format("[%s] [%5s] [%s] %s - %s",
                timestamp,
                level != null ? level.toUpperCase() : "INFO",
                thread != null ? thread : "main",
                logger != null ? logger : "unknown.Logger",
                message != null ? message : ""
        );
    }
}
