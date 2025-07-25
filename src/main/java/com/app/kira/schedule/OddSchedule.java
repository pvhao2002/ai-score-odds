package com.app.kira.schedule;

import com.app.kira.model.EventDTO;
import com.app.kira.model.EventResult;
import com.app.kira.model.analyst.OddAnalyst;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;

@Log
@Service
@RequiredArgsConstructor
public class OddSchedule {
    private static final String STATUS_KEY = "status";
    private static final String FAIL_STATUS = "fail";
    private static final String DONE_STATUS = "done";
    private static final String IN_PROGRESS_STATUS = "in_progress";
    private static final String EVENT_ID_KEY = "event_id";
    private static final String SQL_GET_EVENT_AND_ODD = """
            with oa as (SELECT oa.event_id
                            FROM odd_analyst oa
                            WHERE TRUE
                              AND odd_value <> '[]'
                            GROUP BY oa.event_id
                            HAVING COUNT(DISTINCT oa.odd_type) >= 3)
                select oa2.event_id
                     , oa2.odd_value
                     , oa2.odd_type
                     , ea.event_name
                     , ea.event_date
                     , ea.league_name
                from event_analyst ea
                         inner join oa on oa.event_id = ea.event_id
                         inner join odd_analyst oa2 on oa2.event_id = oa.event_id
                where true
                  and (oa2.status = 'pending' or oa2.status = 'fail')
                limit 120
                for update skip locked
            """;
    private static final String SQL_DELETE_OLD_ODD_EVENT = """
            delete
            from odd_event
            where event_id = :event_id
            """;
    private static final String SQL_UPDATE_ODD_ANALYST_STATUS = """
            update odd_analyst
                set status = :status
            where event_id = :event_id
            """;
    private static final String SQL_INSERT_ODD_EVENT = """
            insert into odd_event(event_id, odd_type, odd_date, line, home_odds, draw_odds, away_odds, over_odds, under_odds)
            VALUES (:event_id, :odd_type, :odd_date, :line, :home_odds, :draw_odds, :away_odds, :over_odds, :under_odds)
            ON DUPLICATE KEY UPDATE odd_date   = values(odd_date),
                                    line       = values(line),
                                    home_odds  = values(home_odds),
                                    draw_odds  = values(draw_odds),
                                    away_odds  = values(away_odds),
                                    over_odds  = values(over_odds),
                                    under_odds = values(under_odds)
            """;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Transactional
    @Scheduled(fixedDelay = 3, initialDelay = 10, timeUnit = TimeUnit.SECONDS)
    public void calculateOdds() {
        var result = jdbcTemplate.query(SQL_GET_EVENT_AND_ODD, (rs, i) -> new EventDTO(rs));
        if (result.isEmpty()) {
            return;
        }
        result.stream()
                .collect(Collectors.groupingBy(EventDTO::getEventId))
                .forEach((key, value) -> {
                    var param = new MapSqlParameterSource(EVENT_ID_KEY, key);
                    try {
                        jdbcTemplate.update(SQL_DELETE_OLD_ODD_EVENT, param);
                        jdbcTemplate.update(SQL_UPDATE_ODD_ANALYST_STATUS, param.addValue(STATUS_KEY, IN_PROGRESS_STATUS));
                        var eventResult = new EventResult(value);
                        var odds = eventResult.parseOdd();
                        if (odds.isEmpty()) {
                            log.warning("No odds found for event: " + key);
                            jdbcTemplate.update(SQL_UPDATE_ODD_ANALYST_STATUS, param.addValue(STATUS_KEY, FAIL_STATUS));
                            return;
                        }
                        var paramOdds = odds
                                .stream()
                                .map(OddAnalyst::toParam)
                                .toArray(SqlParameterSource[]::new);
                        jdbcTemplate.batchUpdate(SQL_INSERT_ODD_EVENT, paramOdds);
                        jdbcTemplate.update(SQL_UPDATE_ODD_ANALYST_STATUS, param.addValue(STATUS_KEY, DONE_STATUS));
                    } catch (Exception ex) {
                        log.log(Level.WARNING, "OddSchedule >> calculateOdds >> exception with event %s:".formatted(key), ex);
                        jdbcTemplate.update(SQL_UPDATE_ODD_ANALYST_STATUS, param.addValue(STATUS_KEY, FAIL_STATUS));
                    }
                });
    }
}
