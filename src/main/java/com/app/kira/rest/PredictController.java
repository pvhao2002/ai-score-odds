package com.app.kira.rest;

import com.app.kira.dto.predict.PredictDTO;
import com.app.kira.dto.predict.RawPredict;
import com.app.kira.server.ServerInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("predict")
@RequiredArgsConstructor
public class PredictController {
    private static final String PROMPT = """
            I want you to act as a professional odds analyst and tipster.
            I will provide you with the handicap (HDP), over/under (O/U), and corner odds and lines for an upcoming or ongoing football match.
            Based on that information, analyze the expected match dynamics using your knowledge of tactics, player quality, team strengths/weaknesses,
            and their current motivations (e.g., league position, recent form, or tournament context).
            Then, provide your professional prediction for the outcome of the handicap, over/under, and corner bets, as well as your predicted final score.
            Focus on intelligent and technical commentary, not just surface-level stats.
            My first request is:
            🏆 [LeagueName]
            🕘 Kick-off: [EventDateTime]
                        
            🔷 Match: [Home] vs [Away]
                        
            📊 Handicap (HDC):
               - [Home] [HomeHdc] [HomeOdd]
               - [Away] [AwayHdc] [AwayOdd]
                        
            📊 Over/Under (O/U):
               - Over [OuLine] [OverOdd]
               - Under [OuLine] [UnderOdd]
                        
            📊 Corners Line:
               - Over [CornerLine] [CornerOverOdd]
               - Under [CornerLine] [CornerUnderOdd]
                        
            📉 Head-to-Head (H2H):
               [H2H]
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ServerInfoService serverInfoService;

    @GetMapping("info")
    public Object info() {
        return Map.of(
                "hostName", serverInfoService.getHostName(),
                "ipAddress", serverInfoService.getIpAddress(),
                "url", serverInfoService.getUrl(),
                "active", serverInfoService.isActive()
        );
    }

    @PostMapping
    public Object predict(@RequestBody PredictDTO request) {
        var sql = """
                with goal_stats as (select ft_total_goal, count(1) as cnt
                                    from (select ea.event_id
                                          from event_analyst ea
                                                   inner join odd_event hdc on hdc.event_id = ea.event_id and hdc.odd_type = 'hdc'
                                                   inner join odd_event ou on ou.event_id = ea.event_id and ou.odd_type = 'ou'
                                          where hdc.home_line = :pHome
                                            and ou.line = :pOuLine
                                            and (
                                                (((:pFirstHome <= :pLastHome and ea.first_home_odds <= ea.last_home_odds) OR (:pFirstHome > :pLastHome and ea.first_home_odds > ea.last_home_odds))
                                                and ((:pFirstAway <= :pLastAway and ea.first_away_odds <= ea.last_away_odds) OR (:pFirstAway > :pLastAway and ea.first_away_odds > ea.last_away_odds)))
                                                OR
                                                (((:pFirstOver <= :pLastOver and ea.first_over_odds <= ea.last_over_odds) OR (:pFirstOver > :pLastOver and ea.first_over_odds > ea.last_over_odds))
                                                and ((:pFirstUnder <= :pLastUnder and ea.first_under_odds <= ea.last_under_odds) OR (:pFirstUnder > :pLastUnder and ea.first_under_odds > ea.last_under_odds)))
                                            )
                                            and (
                                                (((:pFirstHdc <= :pLastHdc and ea.first_hdc <= ea.last_hdc) OR (:pFirstHdc > :pLastHdc and ea.first_hdc > ea.last_hdc))
                                                and ((:pFirstOu <= :pLastOu and ea.first_ou <= ea.last_ou) OR (:pFirstOu > :pLastOu and ea.first_ou > ea.last_ou)))
                                            )
                                          group by ea.event_id) t
                                             inner join event_analyst t2 on t2.event_id = t.event_id
                                    group by ft_total_goal),
                     ranked_stats as (select ft_total_goal,
                                             cnt,
                                             row_number() over (order by cnt desc) as rn
                                      from goal_stats)
                select ft_total_goal,
                       cnt,
                       round(cnt / sum(cnt) over (), 4) as ratio
                from ranked_stats
                where rn <= 3
                order by cnt desc
                """;
        var params = Map.ofEntries(
                Map.entry("pHome", request.getHome()),
                Map.entry("pOuLine", request.getOuLine()),
                Map.entry("pFirstHome", request.getFirstHome()),
                Map.entry("pLastHome", request.getLastHome()),
                Map.entry("pFirstAway", request.getFirstAway()),
                Map.entry("pLastAway", request.getLastAway()),
                Map.entry("pFirstOver", request.getFirstOver()),
                Map.entry("pLastOver", request.getLastOver()),
                Map.entry("pFirstUnder", request.getFirstUnder()),
                Map.entry("pLastUnder", request.getLastUnder()),

                Map.entry("pFirstHdc", request.getFirstHdc()),
                Map.entry("pLastHdc", request.getLastHdc()),
                Map.entry("pFirstOu", request.getFirstOu()),
                Map.entry("pLastOu", request.getLastOu())
        );
        var sqlExactOdd = """
                with goal_stats as (select ft_total_goal, count(1) as cnt
                                    from (select event_id
                                     from event_analyst ea
                                     where true
                                       and ea.first_hdc = :pFirstHdc
                                       and ea.last_hdc = :pLastHdc
                                       and ea.first_ou = :pFirstOu
                                       and ea.last_ou = :pLastOu
                                       and ((:pLastHome > :pLastAway and ea.last_home_odds > ea.last_away_odds) OR (:pLastHome <= :pLastAway and ea.last_home_odds <= ea.last_away_odds))
                                       and ((:pLastOver > :pLastUnder and ea.last_home_odds > ea.last_away_odds) OR (:pLastOver <= :pLastUnder and ea.last_home_odds <= ea.last_away_odds))) t
                                             inner join event_analyst t2 on t2.event_id = t.event_id
                                    group by ft_total_goal),
                     ranked_stats as (select ft_total_goal, cnt, row_number() over (order by cnt desc) as rn from goal_stats)
                select ft_total_goal,
                       cnt,
                       round(cnt / sum(cnt) over (), 4) as ratio
                from ranked_stats
                where rn <= 3
                order by cnt desc
                """;
        var sameOdd = jdbcTemplate.query(sql, params, BeanPropertyRowMapper.newInstance(RawPredict.class));
        var exactOdd = jdbcTemplate.query(sqlExactOdd, params, BeanPropertyRowMapper.newInstance(RawPredict.class));
        return Map.of(
                "sameOdd", sameOdd,
                "exactOdd", exactOdd
        );
    }
}
