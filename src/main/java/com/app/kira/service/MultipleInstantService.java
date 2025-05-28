package com.app.kira.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
public class MultipleInstantService {
    private final NamedParameterJdbcTemplate db;
    private final ExecutorService executor = Executors.newFixedThreadPool(30);

    public void scheduleBatchTasks() {
        for (int i = 0; i < 30; i++) {
            executor.submit(this::executeTask);
        }
    }

    private void executeTask() {
        var taskName = "task_" + Thread.currentThread().getName() + "_" + System.currentTimeMillis();
        var insert = "insert into task(task_name) values (:name)";
        var params = new MapSqlParameterSource("name", taskName);
        db.update(insert, params);
        System.out.println("Task executed: " + taskName);
    }
}
