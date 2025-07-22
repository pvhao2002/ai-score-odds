package com.app.kira.repo;

import com.app.kira.model.LogEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface LogRepository extends ReactiveCrudRepository<LogEntity, Long> {

    @Query("""
                SELECT * FROM app_logs
                WHERE host_name = :hostName AND log_id > :lastId AND created_at >= DATE_SUB(CURDATE(), INTERVAL 1 DAY)
                ORDER BY log_id
            """)
    Flux<LogEntity> findNewLogs(String hostName, Long lastId);
}
