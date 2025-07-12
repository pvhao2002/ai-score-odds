package com.app.kira.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CorrectDTO {
    private Long eventId;
    private String ftScoreStr;
    private String htScoreStr;
    private String cornerStr;

    private int ftHomeScore;
    private int ftAwayScore;

    private int htHomeScore;
    private int htAwayScore;

    private int cornerHome;
    private int cornerAway;

    private int ftTotalGoal;
    private int htTotalGoal;
    private int totalCorner;

    public static MapSqlParameterSource toParam(CorrectDTO dto) {
        return new MapSqlParameterSource()
                .addValue("eventId", dto.getEventId())
                .addValue("ftHomeScore", dto.getFtHomeScore())
                .addValue("ftAwayScore", dto.getFtAwayScore())

                .addValue("htHomeScore", dto.getHtHomeScore())
                .addValue("htAwayScore", dto.getHtAwayScore())

                .addValue("cornerHome", dto.getCornerHome())
                .addValue("cornerAway", dto.getCornerAway())

                .addValue("ftTotalGoal", dto.getFtTotalGoal())
                .addValue("htTotalGoal", dto.getHtTotalGoal())
                .addValue("totalCorner", dto.getTotalCorner());
    }
}
