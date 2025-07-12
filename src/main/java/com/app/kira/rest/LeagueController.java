package com.app.kira.rest;

import com.app.kira.model.League;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("leagues")
@RequiredArgsConstructor
public class LeagueController {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    @GetMapping
    public Object findAll(@RequestParam(defaultValue = "") String key, @RequestParam(defaultValue = "ALL") String isMain) {
        var sql = """
                select *
                from kira_league
                where 1 = 1
                  and (:isMain = '1' OR league_name like :key)
                  and (:isMain = 'ALL' or is_main = :isMain)
                order by is_main DESC, league_name
                """;
        var param = Map.of("key", "%" + key + "%", "isMain", isMain);
        return jdbcTemplate.query(sql, param, BeanPropertyRowMapper.newInstance(League.class));
    }

    @GetMapping("update-main/{leagueId}")
    public Object updateMain(@PathVariable Integer leagueId, @RequestParam Boolean isMain) {
        var sql = "update kira_league set is_main = :isMain where league_id = :leagueId";
        var param = Map.of("isMain", isMain, "leagueId", leagueId);
        jdbcTemplate.update(sql, param);
        return Map.of("status", "success");
    }
}
