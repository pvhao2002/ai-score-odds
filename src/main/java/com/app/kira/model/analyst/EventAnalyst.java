package com.app.kira.model.analyst;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventAnalyst {
    private Integer id;
    private Integer crawlDateId;
    private String eventName;
    private String homeTeam;
    private String awayTeam;
    private String leagueName;
    private String eventDateStr;
    private Timestamp eventDate;
    private Integer homeScore;
    private Integer awayScore;
    private Integer homeCorner;
    private Integer awayCorner;
    private Boolean isClearHdc;
    private Boolean isClearOu;
    private Boolean isClearCorner;
}
