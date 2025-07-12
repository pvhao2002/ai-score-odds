package com.app.kira.dto;

import com.app.kira.util.JsonUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
public class EventDTO {
    Long eventId;
    String eventName;
    String homeTeam;
    String awayTeam;
    String leagueName;
    Timestamp eventDate;
    String htScore;
    String ftScore;
    String cornerScore;
    String link;
    String isClearHdc;
    String isClearOu;
    String isClearCorner;
    Boolean isMainLeague;
    OddInfoDTO parsedOddInfo;

    Integer ftHomeScore;
    Integer htHomeScore;
    Integer homeCorner;
    Integer ftAwayScore;
    Integer htAwayScore;
    Integer awayCorner;

    public EventDTO(ResultSet rs) throws SQLException {
        this.setEventId(rs.getLong("event_id"));
        this.setLeagueName(rs.getString("league_name"));
        this.setHomeTeam(rs.getString("home_team"));
        this.setAwayTeam(rs.getString("away_team"));
        this.setEventDate(rs.getTimestamp("event_date"));
        this.setHtScore(rs.getString("ht_score"));
        this.setFtScore(rs.getString("ft_score"));
        this.setCornerScore(rs.getString("corner_score"));
        this.setIsClearHdc(rs.getString("is_clear_hdc"));
        this.setIsClearOu(rs.getString("is_clear_ou"));
        this.setIsClearCorner(rs.getString("is_clear_corner"));
        this.setIsMainLeague(rs.getBoolean("is_main_league"));
        this.setLink(rs.getString("link"));
        this.setFtHomeScore(rs.getInt("ft_home_score"));
        this.setHtHomeScore(rs.getInt("ht_home_score"));
        this.setHomeCorner(rs.getInt("home_corner"));
        this.setFtAwayScore(rs.getInt("ft_away_score"));
        this.setHtAwayScore(rs.getInt("ht_away_score"));
        this.setAwayCorner(rs.getInt("away_corner"));

        String oddInfoJson = rs.getString("odd_info");
        if (oddInfoJson != null && !oddInfoJson.isEmpty()) {
            this.setParsedOddInfo(JsonUtil.fromJson(oddInfoJson, OddInfoDTO.class));
        } else {
            this.setParsedOddInfo(new OddInfoDTO());
        }
    }

}
