package com.app.kira.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CorrectClearDTO {
    private Long eventId;

    private Integer htHomeScore;
    private Integer htAwayScore;

    private Integer ftHomeScore;
    private Integer ftAwayScore;

    private Integer homeCorner;
    private Integer awayCorner;

    private String oddType;
    private String line;
    private Double homeLine;
    private Double awayLine;
    private Timestamp oddDate;

    private Double odd1; // over or home odds
    private Double odd2; // under or away odds
}
