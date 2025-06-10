package com.app.kira.model.analyst;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OddAnalyst {
    private Integer oddId;
    private Integer eventId;
    private String oddType;
    private String isLive;
    private LocalDateTime oddDate;
    private String line;
    private Double homeOdd;
    private Double awayOdd;
    private Double drawOdd;
    private Double overOdd;
    private Double underOdd;

    public static SqlParameterSource toParam(OddAnalyst dto) {
        return new MapSqlParameterSource()
                .addValue("event_id", dto.getEventId())
                .addValue("odd_type", dto.getOddType())
                .addValue("odd_date", dto.getOddDate())
                .addValue("line", dto.getLine())
                .addValue("home_odds", dto.getHomeOdd())
                .addValue("draw_odds", dto.getDrawOdd())
                .addValue("away_odds", dto.getAwayOdd())
                .addValue("over_odds", dto.getOverOdd())
                .addValue("under_odds", dto.getUnderOdd());
    }
}
