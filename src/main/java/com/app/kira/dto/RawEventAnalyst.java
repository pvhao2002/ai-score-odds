package com.app.kira.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
public class RawEventAnalyst extends BaseEventAnalystDTO {
    String oddTypeHdc;
    String lineHdc;
    String homeLineHdc;
    String awayLineHdc;
    Double homeOddsHdc;
    Double awayOddsHdc;
    String homeLineMovement;
    String awayLineMovement;

    String oddTypeOu;
    String lineOu;
    Double overOu;
    Double underOu;
    String overLineMovement;
    String underLineMovement;

    String oddTypeCorner;
    String lineCorner;
    Double overCorner;
    Double underCorner;

    Double firstHomeOdds;
    Double lastHomeOdds;
    Double firstAwayOdds;
    Double lastAwayOdds;
    Double firstOverOdds;
    Double lastOverOdds;
    Double firstUnderOdds;
    Double lastUnderOdds;

    String firstHdc;
    String lastHdc;
    String firstOu;
    String lastOu;

    public List<OddEventFilterAnalystDTO> toListOdd() {
        return List.of(
                OddEventFilterAnalystDTO.builder()
                        .oddType(oddTypeHdc)
                        .line(lineHdc)
                        .homeLine(homeLineHdc)
                        .awayLine(awayLineHdc)
                        .odd1(homeOddsHdc)
                        .odd2(awayOddsHdc)
                        .build(),
                OddEventFilterAnalystDTO.builder()
                        .oddType(oddTypeOu)
                        .line(lineOu)
                        .odd1(overOu)
                        .odd2(underOu)
                        .build(),
                OddEventFilterAnalystDTO.builder()
                        .oddType(oddTypeCorner)
                        .line(lineCorner)
                        .odd1(overCorner)
                        .odd2(underCorner)
                        .build()
        );
    }
}
