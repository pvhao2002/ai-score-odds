package com.app.kira.rest;

import com.app.kira.dto.DashboardDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    @GetMapping
    public Object dashboard() {
        var sql = """
                with total_leagues as (select count(1) as cnt from kira_league)
                select count(1)                                         as total_events
                     , COUNT(IF(DATE(event_date) = CURDATE(), 1, NULL)) as today_events
                     , COUNT(IF(event_date > NOW(), 1, NULL))           as upcoming_events -- number event upcoming
                     , (select cnt from total_leagues)                  as total_leagues
                from event_analyst
                """;
        return jdbcTemplate.query(sql, BeanPropertyRowMapper.newInstance(DashboardDTO.class))
                .stream()
                .findFirst()
                .orElse(new DashboardDTO());
    }
}
