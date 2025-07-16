package com.app.kira.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

@Log
@Service
@RequiredArgsConstructor
public class MultipleInstantService {
    private final NamedParameterJdbcTemplate db;
    private final ExecutorService executor = Executors.newFixedThreadPool(30);


//    @Scheduled(fixedDelay = 500)
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void scheduleBatchTasks() {
        var result = db.query("select task_id from task  limit 5 for update skip locked", (rs, i) -> rs.getLong("task_id"));
        if (result.isEmpty()) {
            System.out.println("No tasks to execute");
            return;
        }
        result.forEach(task -> {
            try {
                log.log(Level.INFO, "Executing task: " + task);
                db.update("update task set task_name = CONCAT(task_name,'_processing') where task_id = :id", new MapSqlParameterSource("id", task));
                Thread.sleep(15_000);
                db.update("update task set task_name = REPLACE(task_name, '_processing', '_done') where task_id = :id", new MapSqlParameterSource("id", task));
            } catch (InterruptedException e) {
                log.log(Level.WARNING, "Task interrupted", e);
            }
        });
    }

    private void executeTask() {
        var taskName = "task_" + Thread.currentThread().getName() + "_" + System.currentTimeMillis();
        var insert = "insert into task(task_name) values (:name)";
        var params = new MapSqlParameterSource("name", taskName);
        db.update(insert, params);
        System.out.println("Task executed: " + taskName);
    }
}
