package com.app.kira.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PROTECTED)
public class BaseEventAnalystDTO {
    Long   eventId;
    String homeTeam;
    String awayTeam;
    String leagueName;
    String eventDate;
    String htScoreStr;
    String ftScoreStr;
    String cornerStr;
    String link;

    public BaseEventAnalystDTO(RawEventAnalyst value) {
        this.eventId = value.getEventId();
        this.homeTeam = value.getHomeTeam();
        this.awayTeam = value.getAwayTeam();
        this.leagueName = value.getLeagueName();
        this.eventDate = value.getEventDate();
        this.htScoreStr = value.getHtScoreStr();
        this.ftScoreStr = value.getFtScoreStr();
        this.cornerStr = value.getCornerStr();
        this.link = value.getLink();
    }
}
