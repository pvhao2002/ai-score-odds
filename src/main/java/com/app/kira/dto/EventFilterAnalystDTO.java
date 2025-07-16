package com.app.kira.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
public class EventFilterAnalystDTO extends BaseEventAnalystDTO {
    @JsonIgnore
    private final String ICON_UP = """
            <i class="fa-solid fa-up-long"></i>
            """;
    @JsonIgnore
    private final String ICON_DOWN = """
            <i class="fa-solid fa-down-long"></i>
            """;
    String homeLineHdcMovement;
    String awayLineHdcMovement;
    String overLineMovement;
    String underLineMovement;
    String homeLineHdc;
    String awayLineHdc;
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
    List<OddEventFilterAnalystDTO> odds = new ArrayList<>();

    public EventFilterAnalystDTO(RawEventAnalyst item) {
        super(item);
        this.homeLineHdcMovement = item.getHomeLineMovement();
        this.awayLineHdcMovement = item.getAwayLineMovement();
        this.overLineMovement = item.getOverLineMovement();
        this.underLineMovement = item.getUnderLineMovement();
        this.firstHomeOdds = item.getFirstHomeOdds();
        this.lastHomeOdds = item.getLastHomeOdds();
        this.firstAwayOdds = item.getFirstAwayOdds();
        this.lastAwayOdds = item.getLastAwayOdds();
        this.firstOverOdds = item.getFirstOverOdds();
        this.lastOverOdds = item.getLastOverOdds();
        this.firstUnderOdds = item.getFirstUnderOdds();
        this.lastUnderOdds = item.getLastUnderOdds();
        this.homeLineHdc = item.getHomeLineHdc();
        this.awayLineHdc = item.getAwayLineHdc();
        this.firstHdc = item.getFirstHdc();
        this.lastHdc = item.getLastHdc();
        this.firstOu = item.getFirstOu();
        this.lastOu = item.getLastOu();
        this.odds = item.toListOdd();
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class InternalLineOddEventFilterAnalyst {
        String line;
        Double odd;
    }
}
