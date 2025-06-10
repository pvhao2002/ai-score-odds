package com.app.kira.service;

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

import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

@Log
@Service
@RequiredArgsConstructor
public class OddService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Transactional
//    @Scheduled(fixedDelay = 500)
    public void calculateOdds() {
        try {
            var sql = """
                    with oa as (SELECT oa.event_id
                                FROM odd_analyst oa
                                WHERE TRUE
                                  AND odd_type IN ('hdc', '1x2', 'ou')
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
                             inner join oa on oa.event_id = ea.id
                             inner join odd_analyst oa2 on oa2.event_id = oa.event_id
                    where true
                      and (oa2.status = 'pending' or oa2.status = 'fail')
                    limit 120
                    for update skip locked 
                                   """;
            var result = jdbcTemplate.query(sql, (rs, i) -> new EventDTO(rs));
            if (result.isEmpty()) {
                log.info("No odds to calculate");
                return;
            }
            var paramIds = result.stream()
                    .map(it -> new MapSqlParameterSource()
                            .addValue("event_id", it.getEventId())
                            .addValue("odd_type", it.getOddType()))
                    .toArray(SqlParameterSource[]::new);
            var sql2 = """
                           update odd_analyst
                            set status = '%s'
                            where event_id = :event_id
                              and odd_type = :odd_type
                    """;
            jdbcTemplate.batchUpdate(sql2.formatted("in_progress"), paramIds);

            var param = result
                    .stream()
                    .collect(Collectors.groupingBy(EventDTO::getEventId))
                    .entrySet()
                    .stream()
                    .map(EventResult::new)
                    .map(EventResult::parseOdd)
                    .flatMap(List::stream)
                    .map(OddAnalyst::toParam)
                    .toArray(SqlParameterSource[]::new);
            var sql1 = """
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
            jdbcTemplate.batchUpdate(sql1, param);
            jdbcTemplate.batchUpdate(sql2.formatted("done"), paramIds);
            log.info("Odds calculated successfully: " + result.size());
        } catch (Exception ex) {
            log.log(Level.WARNING, "Error calculating odds", ex);
        }
    }
}
