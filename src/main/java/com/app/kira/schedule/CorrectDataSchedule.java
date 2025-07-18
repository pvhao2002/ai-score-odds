package com.app.kira.schedule;

import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@Log
@RequiredArgsConstructor
public class CorrectDataSchedule {
    private static final String CORRECT_HOME_AWAY_LINE = """
            UPDATE event_analyst ea
                JOIN (SELECT event_id,
                             GROUP_CONCAT(home_line_movement SEPARATOR ', ') AS home_line_movement,
                             GROUP_CONCAT(away_line_movement SEPARATOR ', ') AS away_line_movement
                      FROM (SELECT event_id,
                                   CONCAT(FORMAT(home_line, 2), ' ', FORMAT(home_odds, 2), ' ',
                                          CASE
                                              WHEN home_odds > LAG(home_odds) OVER (PARTITION BY event_id ORDER BY odd_date)
                                                  THEN '↑'
                                              WHEN home_odds < LAG(home_odds) OVER (PARTITION BY event_id ORDER BY odd_date)
                                                  THEN '↓'
                                              ELSE ''
                                              END) AS home_line_movement,
                                   CONCAT(FORMAT(away_line, 2), ' ', FORMAT(away_odds, 2), ' ',
                                          CASE
                                              WHEN away_odds > LAG(away_odds) OVER (PARTITION BY event_id ORDER BY odd_date)
                                                  THEN '↑'
                                              WHEN away_odds < LAG(away_odds) OVER (PARTITION BY event_id ORDER BY odd_date)
                                                  THEN '↓'
                                              ELSE ''
                                              END) AS away_line_movement
                            FROM odd_event
                            WHERE odd_type = 'hdc') ranked
                      GROUP BY event_id) t ON t.event_id = ea.event_id
            SET ea.home_line_movement = t.home_line_movement,
                ea.away_line_movement = t.away_line_movement
            """;
    private static final String CORRECT_OVER_UNDER_LINE = """
            UPDATE event_analyst ea
                JOIN (SELECT event_id,
                             GROUP_CONCAT(ou_trend SEPARATOR ', ')    AS ou_trend,
                             GROUP_CONCAT(under_trend SEPARATOR ', ') AS under_trend
                      FROM (SELECT event_id,
                                   CONCAT(line, ' ', FORMAT(over_odds, 2), ' ',
                                          CASE
                                              WHEN over_odds > LAG(over_odds) OVER (PARTITION BY event_id ORDER BY odd_date)
                                                  THEN '↑'
                                              WHEN over_odds < LAG(over_odds) OVER (PARTITION BY event_id ORDER BY odd_date)
                                                  THEN '↓'
                                              ELSE ''
                                              END) AS ou_trend,
                                   CONCAT(line, ' ', FORMAT(under_odds, 2), ' ',
                                          CASE
                                              WHEN under_odds > LAG(under_odds) OVER (PARTITION BY event_id ORDER BY odd_date)
                                                  THEN '↑'
                                              WHEN under_odds < LAG(under_odds) OVER (PARTITION BY event_id ORDER BY odd_date)
                                                  THEN '↓'
                                              ELSE ''
                                              END) AS under_trend
                            FROM odd_event
                            WHERE odd_type = 'ou') ranked
                      GROUP BY event_id) t ON t.event_id = ea.event_id
            SET ea.over_line_movement  = t.ou_trend,
                ea.under_line_movement = t.under_trend
            """;

    private static final String OPEN_PREMATCH_ODD = """
            update event_analyst ea
                join (select event_id,
                             max(case when odd_date = first_odd_date then home_odds end) as first_home_odds,
                             max(case when odd_date = last_odd_date then home_odds end)  as last_home_odds
                      from (select event_id,
                                   home_odds,
                                   odd_date,
                                   min(odd_date) over (partition by event_id) as first_odd_date,
                                   max(odd_date) over (partition by event_id) as last_odd_date
                            from odd_event
                            where odd_type = 'hdc') ranked
                      group by event_id) home_odd
                on home_odd.event_id = ea.event_id
                join (select event_id,
                             max(case when odd_date = first_odd_date then away_odds end) as first_away_odds,
                             max(case when odd_date = last_odd_date then away_odds end)  as last_away_odds
                      from (select event_id,
                                   away_odds,
                                   odd_date,
                                   min(odd_date) over (partition by event_id) as first_odd_date,
                                   max(odd_date) over (partition by event_id) as last_odd_date
                            from odd_event
                            where odd_type = 'hdc') ranked
                      group by event_id) away_odd
                on away_odd.event_id = ea.event_id
                join (select event_id,
                             max(case when odd_date = first_odd_date then over_odds end) as first_over_odds,
                             max(case when odd_date = last_odd_date then over_odds end)  as last_over_odds
                      from (select event_id,
                                   over_odds,
                                   odd_date,
                                   min(odd_date) over (partition by event_id) as first_odd_date,
                                   max(odd_date) over (partition by event_id) as last_odd_date
                            from odd_event
                            where odd_type = 'ou') ranked
                      group by event_id) over_odd on over_odd.event_id = ea.event_id
                join (select event_id,
                             max(case when odd_date = first_odd_date then under_odds end) as first_under_odds,
                             max(case when odd_date = last_odd_date then under_odds end)  as last_under_odds
                      from (select event_id,
                                   under_odds,
                                   odd_date,
                                   min(odd_date) over (partition by event_id) as first_odd_date,
                                   max(odd_date) over (partition by event_id) as last_odd_date
                            from odd_event
                            where odd_type = 'ou') ranked
                      group by event_id) under_odd on under_odd.event_id = ea.event_id
                join (select event_id,
                             max(case when odd_date = first_odd_date then line end) as first_ou,
                             max(case when odd_date = last_odd_date then line end)  as last_ou
                      from (select event_id,
                                   line,
                                   odd_date,
                                   min(odd_date) over (partition by event_id) as first_odd_date,
                                   max(odd_date) over (partition by event_id) as last_odd_date
                            from odd_event
                            where odd_type = 'ou') ranked
                      group by event_id) ou on ou.event_id = ea.event_id
                join (select event_id,
                             max(case when odd_date = first_odd_date then line end) as first_hdc,
                             max(case when odd_date = last_odd_date then line end)  as last_hdc
                      from (select event_id,
                                   line,
                                   odd_date,
                                   min(odd_date) over (partition by event_id) as first_odd_date,
                                   max(odd_date) over (partition by event_id) as last_odd_date
                            from odd_event
                            where odd_type = 'hdc') ranked
                      group by event_id) hdc on hdc.event_id = ea.event_id
            set ea.first_home_odds  = IFNULL(ea.first_home_odds, home_odd.first_home_odds),
                ea.last_home_odds   = IFNULL(ea.last_home_odds, home_odd.last_home_odds),
                ea.first_away_odds  = IFNULL(ea.first_away_odds, away_odd.first_away_odds),
                ea.last_away_odds   = IFNULL(ea.last_away_odds, away_odd.last_away_odds),
                ea.first_over_odds  = IFNULL(ea.first_over_odds, over_odd.first_over_odds),
                ea.last_over_odds   = IFNULL(ea.last_over_odds, over_odd.last_over_odds),
                ea.first_under_odds = IFNULL(ea.first_under_odds, under_odd.first_under_odds),
                ea.last_under_odds  = IFNULL(ea.last_under_odds, under_odd.last_under_odds),
                ea.first_ou         = IFNULL(ea.first_ou, ou.first_ou),
                ea.last_ou          = IFNULL(ea.last_ou, ou.last_ou),
                ea.first_hdc        = IFNULL(ea.first_hdc, hdc.first_hdc),
                ea.last_hdc         = IFNULL(ea.last_hdc, hdc.last_hdc)
            """;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Scheduled(cron = "0 0 5 * * *", zone = "Asia/Ho_Chi_Minh") // Every day at 5 AM
    @Retryable(
            retryFor = {Exception.class},
            backoff = @Backoff(delay = 20_000) // delay 20s giữa mỗi lần retry
    )
    @Transactional
    public void correctOddLine() {
        log.info("Correcting home/away line movements...");
        jdbcTemplate.update(CORRECT_HOME_AWAY_LINE, new java.util.HashMap<>());
        log.info("Correcting over/under line movements...");
        jdbcTemplate.update(CORRECT_OVER_UNDER_LINE, new java.util.HashMap<>());
        log.info("Opening prematch odds...");
        jdbcTemplate.update(OPEN_PREMATCH_ODD, new java.util.HashMap<>());
        log.info("Data correction completed.");
    }


    @Scheduled(cron = "0 0 7 * * *", zone = "Asia/Ho_Chi_Minh") // Every day at 7 AM
    @Transactional
    public void correctLeague() {
        jdbcTemplate.update("""
                insert ignore into kira_league(league_name)
                select distinct league_name
                from event_analyst
                order by league_name
                """, Map.of());
        jdbcTemplate.update("""
                update event_analyst ea
                    inner join kira_league kl on kl.league_name = ea.league_name
                set ea.league_id = kl.league_id
                where ea.league_id is null
                """, Map.of());
    }
}
