package com.app.kira.rest;

import com.app.kira.model.CorrectDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("correct")
@RequiredArgsConstructor
public class CorrectController {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    @GetMapping
    public Object correct() {
        var sql = """
                select event_id,
                       REPLACE(ft_score_str, ' ', '')   ft_score_str,
                       REPLACE(ht_score_str, 'HT ', '') ht_score_str,
                       REPLACE(corner_str, ' ', '')     corner_str
                from event_analyst
                """;
        var datas = jdbcTemplate.query(sql, BeanPropertyRowMapper.newInstance(CorrectDTO.class));
        var params = datas.stream()
                .peek(e -> {
                    // dau -
                    var minus = "-";
                    var ftScore = e.getFtScoreStr().split(minus);
                    var htScore = e.getHtScoreStr().split(minus);
                    var corner = e.getCornerStr().split(minus);

                    e.setFtHomeScore(Integer.parseInt(ftScore[0]));
                    e.setFtAwayScore(Integer.parseInt(ftScore[1]));

                    e.setHtHomeScore(Integer.parseInt(htScore[0]));
                    e.setHtAwayScore(Integer.parseInt(htScore[1]));

                    e.setCornerHome(Integer.parseInt(corner[0]));
                    e.setCornerAway(Integer.parseInt(corner[1]));

                    e.setFtTotalGoal(e.getFtHomeScore() + e.getFtAwayScore());
                    e.setHtTotalGoal(e.getHtHomeScore() + e.getHtAwayScore());
                    e.setTotalCorner(e.getCornerHome() + e.getCornerAway());
                })
                .map(CorrectDTO::toParam)
                .toArray(MapSqlParameterSource[]::new);
        jdbcTemplate.batchUpdate("""
                update event_analyst
                set ft_home_score = :ftHomeScore,
                    ft_away_score = :ftAwayScore,
                
                    ht_home_score = :htHomeScore,
                    ht_away_score = :htAwayScore,
                    
                    home_corner = :cornerHome,
                    away_corner = :cornerAway,
                    
                    ft_total_goal = :ftTotalGoal,
                    ht_total_goal = :htTotalGoal,
                    total_corner = :totalCorner
                where event_id = :eventId
                """, params);
        return "OK";
    }


}
