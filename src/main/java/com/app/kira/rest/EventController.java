package com.app.kira.rest;

import com.app.kira.dto.*;
import com.app.kira.model.FilterOdd;
import com.app.kira.util.OddConverter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class EventController {
    private double relativeError = 1.0 / 100; // 0.01% relative error for odds comparison
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @GetMapping("relative-error")
    public Object getRelativeError() {
        return Map.of("relativeError", relativeError);
    }

    @GetMapping("update-relative-error")
    public Object updateRelativeError(@RequestParam(name = "relativeError", defaultValue = "0.01") double re) {
        relativeError = re;
        return Map.of("relativeError", relativeError);
    }

    @PostMapping("filter-odd")
    public Object filterOdd(@RequestBody List<FilterOdd> request) {
        var sql = """
                select ea.event_id
                     , home_team
                     , away_team
                     , ea.league_name
                     , event_date
                     , ht_score_str
                     , ft_score_str
                     , corner_str
                     , link
                                
                     , odd_hdc.odd_type      as odd_type_hdc
                     , odd_hdc.line          as line_hdc
                     , odd_hdc.home_line     as home_line_hdc
                     , odd_hdc.away_line     as away_line_hdc
                     , odd_hdc.home_odds     as home_odds_hdc
                     , odd_hdc.away_odds     as away_odds_hdc
                                
                     , ea.home_line_movement
                     , ea.away_line_movement
                                
                     , odd_ou.odd_type       as odd_type_ou
                     , odd_ou.line           as line_ou
                     , odd_ou.over_odds      as over_ou
                     , odd_ou.under_odds     as under_ou
                                
                     , over_line_movement
                     , under_line_movement
                                
                     , odd_corner.odd_type   as odd_type_corner
                     , odd_corner.line       as line_corner
                     , odd_corner.over_odds  as over_corner
                     , odd_corner.under_odds as under_corner
                     
                     , first_home_odds
                     , last_home_odds
                     , first_away_odds
                     , last_away_odds
                     , first_over_odds
                     , last_over_odds
                     , first_under_odds
                     , last_under_odds
                     
                     , first_hdc
                     , last_hdc
                     , first_ou
                     , last_ou
                                
                from event_analyst ea
                         inner join odd_event odd_hdc
                                    on odd_hdc.event_id = ea.event_id and odd_hdc.odd_type = 'hdc' and odd_hdc.open_odd = 1
                         inner join odd_event odd_ou
                                    on odd_ou.event_id = ea.event_id and odd_ou.odd_type = 'ou' and odd_ou.open_odd = 1
                         left join odd_event odd_corner
                                   on odd_corner.event_id = ea.event_id and odd_corner.odd_type = 'corner' and odd_corner.open_odd = 1
                         left join kira_league kl on kl.league_id = ea.league_id
                where true
                  and (odd_hdc.line = :hdc_line or odd_hdc.home_line = :hdc_line or odd_hdc.away_line = :hdc_line)
                  and odd_ou.line = :ou_line
                                
                order by kl.is_main desc
                       , ea.event_date desc
                       , ea.event_name
                """;
        var param = new MapSqlParameterSource("re", relativeError);
        for (var r : request) {
            if ("ou".equalsIgnoreCase(r.getType())) {
                param.addValue("ou_line", r.getLine());
            } else {
                param.addValue("hdc_line", r.getLine());
            }
        }
        var data = namedParameterJdbcTemplate.query(sql, param, BeanPropertyRowMapper.newInstance(RawEventAnalyst.class))
                .stream()
                .map(EventFilterAnalystDTO::new)
                .toList();

        return Map.of(
                "data", data
        );
    }

    @GetMapping
    public Object findAll(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "key", defaultValue = "") String key,
            @RequestParam(name = "isTeam", defaultValue = "false") Boolean isTeam,
            @RequestParam(name = "exact", defaultValue = "false") Boolean exact
    ) {
        var offset = (page - 1) * size;
        var param = new MapSqlParameterSource()
                .addValue("key", exact ? key : "%" + key + "%")
                .addValue("exact", exact)
                .addValue("isTeam", isTeam)
                .addValue("offset", offset)
                .addValue("size", size);

        var sql = """
                WITH hdc_odds AS (SELECT * FROM odd_event WHERE open_odd = 1 AND odd_type = 'hdc'),
                     ou_odds AS (SELECT * FROM odd_event WHERE open_odd = 1 AND odd_type = 'ou'),
                     corner_odds AS (SELECT * FROM odd_event WHERE open_odd = 1 AND odd_type = 'corner')
                select ea.event_id
                     , kl.league_name
                     , home_team
                     , away_team
                     , event_date
                     , ht_score_str as ht_score
                     , ft_score_str as ft_score
                     , corner_str   as corner_score
                     , is_clear_hdc
                     , is_clear_ou
                     , is_clear_corner
                     , is_main      AS is_main_league
                     , link
                      , ft_home_score
                      , ht_home_score
                      , home_corner
                      , ft_away_score
                      , ht_away_score
                      , away_corner
                     , JSON_OBJECT(
                        'hdc', JSON_OBJECT(
                                'line', IFNULL(hdc.line, ''),
                                'homeOdds', IFNULL(hdc.home_odds, ''),
                                'awayOdds', IFNULL(hdc.away_odds, ''),
                                'isClear', IFNULL(ea.is_clear_hdc, '')
                               ),
                        'ou', JSON_OBJECT(
                                'line', IFNULL(ou.line, ''),
                                'overOdds', IFNULL(ou.over_odds, ''),
                                'underOdds', IFNULL(ou.under_odds, ''),
                                'isClear', IFNULL(ea.is_clear_ou, '')
                              ),
                        'corner', JSON_OBJECT(
                                'line', IFNULL(corner.line, ''),
                                'homeOdds', IFNULL(corner.over_odds, ''),
                                'awayOdds', IFNULL(corner.under_odds, ''),
                                'isClear', IFNULL(ea.is_clear_corner, '')
                                  )
                       )            AS odd_info
                from event_analyst ea
                         left join kira_league kl on kl.league_id = ea.league_id
                         LEFT JOIN hdc_odds hdc ON hdc.event_id = ea.event_id AND hdc.open_odd = 1
                         LEFT JOIN ou_odds ou ON ou.event_id = ea.event_id AND ou.open_odd = 1
                         LEFT JOIN corner_odds corner ON corner.event_id = ea.event_id AND corner.open_odd = 1
                where TRUE
                  AND (
                    :key = ''
                    OR (
                        (:exact = TRUE AND (
                            (:isTeam = TRUE AND (ea.home_team = :key OR ea.away_team = :key))
                            OR
                            (:isTeam = FALSE AND (ea.league_name = :key OR ea.home_team = :key OR ea.away_team = :key))
                        ))
                        OR
                        (:exact = FALSE AND (
                            (:isTeam = TRUE AND (ea.home_team LIKE :key OR ea.away_team LIKE :key))
                            OR
                            (:isTeam = FALSE AND (ea.league_name LIKE :key OR ea.home_team LIKE :key OR ea.away_team LIKE :key))
                        ))
                    )
                )
                order by kl.is_main desc, event_date desc
                limit :size offset :offset
                """;
        var result = namedParameterJdbcTemplate.query(sql, param, (rs, i) -> new EventDTO(rs));
        var countSql = """
                select count(1)
                from event_analyst ea
                         left join kira_league kl on kl.league_id = ea.league_id
                where TRUE
                  AND (
                    :key = ''
                    OR (
                        (:exact = TRUE AND (
                            (:isTeam = TRUE AND (ea.home_team = :key OR ea.away_team = :key))
                            OR
                            (:isTeam = FALSE AND (ea.league_name = :key OR ea.home_team = :key OR ea.away_team = :key))
                        ))
                        OR
                        (:exact = FALSE AND (
                            (:isTeam = TRUE AND (ea.home_team LIKE :key OR ea.away_team LIKE :key))
                            OR
                            (:isTeam = FALSE AND (ea.league_name LIKE :key OR ea.home_team LIKE :key OR ea.away_team LIKE :key))
                        ))
                    )
                )
                """;
        var count = namedParameterJdbcTemplate.queryForObject(countSql, param, Integer.class);
        TeamAnalystDetailDTO detail = new TeamAnalystDetailDTO();
        if (isTeam && exact) {
            var ftScore = result.stream()
                    .map(e -> StringUtils.compare(e.getHomeTeam(), key) == 0
                            ? e.getFtHomeScore()
                            : e.getFtAwayScore())
                    .toList();
            var htScore = result.stream()
                    .map(e -> StringUtils.compare(e.getHomeTeam(), key) == 0
                            ? e.getHtHomeScore()
                            : e.getHtAwayScore())
                    .toList();
            var corner = result.stream()
                    .map(e -> StringUtils.compare(e.getHomeTeam(), key) == 0
                            ? e.getHomeCorner()
                            : e.getAwayCorner())
                    .toList();
            var minFtScore = ftScore.stream().filter(e -> e != 0).min(Integer::compareTo).orElse(0);
            var maxFtScore = ftScore.stream().max(Integer::compareTo).orElse(0);
            var avgFtScore = ftScore.stream().mapToInt(Integer::intValue).average().orElse(0.0);
            detail.setMinFtScore(minFtScore);
            detail.setMaxFtScore(maxFtScore);
            detail.setAvgFtScore(avgFtScore);

            var minHtScore = htScore.stream().filter(e -> e != 0).min(Integer::compareTo).orElse(0);
            var maxHtScore = htScore.stream().max(Integer::compareTo).orElse(0);
            var avgHtScore = htScore.stream().mapToInt(Integer::intValue).average().orElse(0.0);
            detail.setMinHtScore(minHtScore);
            detail.setMaxHtScore(maxHtScore);
            detail.setAvgHtScore(avgHtScore);

            var minCorner = corner.stream().filter(e -> e != 0).min(Integer::compareTo).orElse(0);
            var maxCorner = corner.stream().max(Integer::compareTo).orElse(0);
            var avgCorner = corner.stream().mapToInt(Integer::intValue).average().orElse(0.0);
            detail.setMinCorner(minCorner);
            detail.setMaxCorner(maxCorner);
            detail.setAvgCorner(avgCorner);
        }
        return Map.of(
                "data", result,
                "page", page,
                "size", size,
                "total", count,
                "isTeam", isTeam,
                "exact", exact,
                "detail", detail
        );
    }

    @GetMapping("correct-clear")
    public Object correct() {
        var sql = """
                select ea.event_id
                     , ht_home_score
                     , ht_away_score
                     , ft_home_score
                     , ft_away_score
                     , home_corner
                     , away_corner
                     , oe.odd_type
                     , oe.line
                     , oe.home_line
                     , oe.away_line
                     , oe.odd_date
                     , IF(oe.odd_type in ('ou', 'corner'), oe.over_odds, oe.home_odds)  AS odd1
                     , IF(oe.odd_type in ('ou', 'corner'), oe.under_odds, oe.away_odds) AS odd2
                from event_analyst ea
                         INNER JOIN odd_event oe ON oe.event_id = ea.event_id and oe.open_odd = 1
                """;
        var result = namedParameterJdbcTemplate.query(sql, BeanPropertyRowMapper.newInstance(CorrectClearDTO.class));
        result.stream()
                .collect(Collectors.groupingBy(CorrectClearDTO::getEventId))
                .forEach((key, value) -> {
                    var param = new MapSqlParameterSource("eventId", key);
                    value.forEach(e -> {
                        switch (e.getOddType()) {
                            case "ou" -> {
                                var line = OddConverter.convertOverUnderOdds(e.getLine());
                                var ftScore = e.getFtHomeScore() + e.getFtAwayScore();
                                var isClear = ftScore > line
                                        ? "yes"
                                        : (ftScore == line ? "draw" : "no");
                                param.addValue("clearOU", isClear);
                                var sqlUpdate = """
                                        update event_analyst
                                        set is_clear_ou     = :clearOU
                                        where event_id = :eventId
                                        """;
                                namedParameterJdbcTemplate.update(sqlUpdate, param);
                            }
                            case "corner" -> {
                                var line = OddConverter.convertOverUnderOdds(e.getLine());
                                var ftCorner = e.getHomeCorner() + e.getAwayCorner();
                                var isClear = ftCorner > line
                                        ? "yes"
                                        : (ftCorner == line ? "draw" : "no");
                                param.addValue("clearCorner", isClear);
                                var sqlUpdate = """
                                        update event_analyst
                                        set is_clear_corner = :clearCorner
                                        where event_id = :eventId
                                        """;
                                namedParameterJdbcTemplate.update(sqlUpdate, param);
                            }
                            case "hdc" -> {
                                var homeLine = e.getHomeLine();
                                var awayLine = e.getAwayLine();
                                String isClear;
                                if ("0#0".equalsIgnoreCase(e.getLine())) {
                                    isClear = e.getFtHomeScore() > e.getFtAwayScore() ? "yes" :
                                            (e.getFtHomeScore().equals(e.getFtAwayScore()) ? "draw" : "no");
                                } else if (homeLine > awayLine) {
                                    isClear = e.getFtHomeScore() + homeLine > e.getFtAwayScore() ? "yes" :
                                            (e.getFtHomeScore() + homeLine == e.getFtAwayScore() ? "draw" : "no");
                                } else {
                                    isClear = e.getFtHomeScore() < e.getFtAwayScore() + awayLine ? "yes" :
                                            (e.getFtHomeScore() == e.getFtAwayScore() + awayLine ? "draw" : "no");
                                }
                                param.addValue("clearHDC", isClear);
                                var sqlUpdate = """
                                        update event_analyst
                                        set is_clear_hdc    = :clearHDC
                                        where event_id = :eventId
                                        """;
                                namedParameterJdbcTemplate.update(sqlUpdate, param);
                            }
                        }
                    });
                });
        return Map.of("result", "OK");
    }
}
