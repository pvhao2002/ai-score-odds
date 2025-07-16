package com.app.kira.dto.predict;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PredictDTO {
    private Double home;
    private String ouLine;
    private Double firstHome;
    private Double lastHome;
    private Double firstAway;
    private Double lastAway;
    private Double firstOver;
    private Double lastOver;
    private Double firstUnder;
    private Double lastUnder;
    private String firstHdc;
    private String lastHdc;
    private String firstOu;
    private String lastOu;
}
