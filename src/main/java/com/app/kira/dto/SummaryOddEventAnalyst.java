package com.app.kira.dto;

import com.app.kira.model.FilterOdd;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.function.Predicate;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SummaryOddEventAnalyst {
    private Integer homeGreaterAway;
    private Integer awayGreaterHome;
    private Integer overGreaterUnder;
    private Integer underGreaterOver;
    private Integer homeAndOverGreater;
    private Integer homeAndUnderGreater;
    private Integer awayAndOverGreater;
    private Integer awayAndUnderGreater;
    private Integer lineAndOddSame;

    public SummaryOddEventAnalyst(List<EventFilterAnalystDTO> items, List<FilterOdd> filterOdds) {
        this.homeGreaterAway = countWhere(items,
                i -> i.getLastHomeOdds() != null
                        && i.getLastAwayOdds() != null
                        && i.getLastHomeOdds() > i.getLastAwayOdds());

        this.awayGreaterHome = countWhere(items,
                i -> i.getLastHomeOdds() != null
                        && i.getLastAwayOdds() != null
                        && i.getLastAwayOdds() > i.getLastHomeOdds());

        this.overGreaterUnder = countWhere(items,
                i -> i.getLastOverOdds() != null
                        && i.getLastUnderOdds() != null
                        && i.getLastOverOdds() > i.getLastUnderOdds());

        this.underGreaterOver = countWhere(items,
                i -> i.getLastOverOdds() != null
                        && i.getLastUnderOdds() != null
                        && i.getLastUnderOdds() > i.getLastOverOdds());

        this.homeAndOverGreater = countWhere(items,
                i -> i.getLastHomeOdds() != null
                        && i.getLastAwayOdds() != null
                        && i.getLastOverOdds() != null
                        && i.getLastUnderOdds() != null
                        && i.getLastHomeOdds() > i.getLastAwayOdds()
                        && i.getLastOverOdds() > i.getLastUnderOdds());

        this.homeAndUnderGreater = countWhere(items,
                i -> i.getLastHomeOdds() != null
                        && i.getLastAwayOdds() != null
                        && i.getLastOverOdds() != null
                        && i.getLastUnderOdds() != null
                        && i.getLastHomeOdds() > i.getLastAwayOdds()
                        && i.getLastUnderOdds() > i.getLastOverOdds());

        this.awayAndOverGreater = countWhere(items,
                i -> i.getLastHomeOdds() != null
                        && i.getLastAwayOdds() != null
                        && i.getLastOverOdds() != null
                        && i.getLastUnderOdds() != null
                        && i.getLastAwayOdds() > i.getLastHomeOdds()
                        && i.getLastOverOdds() > i.getLastUnderOdds());

        this.awayAndUnderGreater = countWhere(items,
                i -> i.getLastHomeOdds() != null
                        && i.getLastAwayOdds() != null
                        && i.getLastOverOdds() != null
                        && i.getLastUnderOdds() != null
                        && i.getLastAwayOdds() > i.getLastHomeOdds()
                        && i.getLastUnderOdds() > i.getLastOverOdds());
        Double homeOddRequest = null;
        Double awayOddRequest = null;
        Double overOddRequest = null;
        Double underOddRequest = null;
        String lineHdc = null;
        String lineOu = null;

        for (var i : filterOdds) {
            if ("hdc".equalsIgnoreCase(i.getType())) {
                homeOddRequest = i.getOdd1();
                awayOddRequest = i.getOdd2();
                lineHdc = i.getLine();
            } else if ("ou".equalsIgnoreCase(i.getType())) {
                overOddRequest = i.getOdd1();
                underOddRequest = i.getOdd2();
                lineOu = i.getLine();
            }
        }

        final Double finalHomeOddRequest = homeOddRequest;
        final Double finalAwayOddRequest = awayOddRequest;
        final Double finalOverOddRequest = overOddRequest;
        final Double finalUnderOddRequest = underOddRequest;
        final String finalLineHdc = lineHdc;
        final String finalLineOu = lineOu;
        this.lineAndOddSame = countWhere(items,
                i -> equalsFirstDecimalPlace(i.getLastHomeOdds(), finalHomeOddRequest)
                        && equalsFirstDecimalPlace(i.getLastAwayOdds(), finalAwayOddRequest)
                        && equalsFirstDecimalPlace(i.getLastOverOdds(), finalOverOddRequest)
                        && equalsFirstDecimalPlace(i.getLastUnderOdds(), finalUnderOddRequest)
                        && (
                        finalLineHdc != null
                                && (finalLineHdc.equalsIgnoreCase(i.getLastHdc())
                                || finalLineHdc.equalsIgnoreCase(Math.abs(Long.parseLong(i.getHomeLineHdc())) + "")
                                || finalLineHdc.equalsIgnoreCase(Math.abs(Long.parseLong(i.getAwayLineHdc())) + "")))
                        && (finalLineOu != null && finalLineOu.equalsIgnoreCase(i.getLastOu()))
        );
    }

    private static <T> int countWhere(List<T> list, Predicate<T> condition) {
        return (int) list.stream().filter(condition).count();
    }

    private static boolean equalsFirstDecimalPlace(Double a, Double b) {
        if (a == null || b == null) return false;
        return Math.round(a * 10) == Math.round(b * 10);
    }
}
