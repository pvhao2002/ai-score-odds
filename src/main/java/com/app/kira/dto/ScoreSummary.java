package com.app.kira.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ScoreSummary {
    private static final String FORMAT_SCORE_SUMMARY = "H: %.2f/ A: %.2f / O: %.2f/ U: %.2f";
    private String score;
    private int cnt;
    private String minOdd;
    private String maxOdd;

    private String regularOdd;

    public ScoreSummary(Map.Entry<String, List<EventFilterAnalystDTO>> items) {
        this.score = items.getKey();
        this.cnt = items.getValue().size();
        var minOddH = items.getValue().stream()
                .map(EventFilterAnalystDTO::getLastHomeOdds)
                .filter(Objects::nonNull)
                .min(Double::compareTo)
                .orElse(null);
        var maxOddH = items.getValue().stream()
                .map(EventFilterAnalystDTO::getLastHomeOdds)
                .filter(Objects::nonNull)
                .max(Double::compareTo)
                .orElse(null);
        var minOddA = items.getValue().stream()
                .map(EventFilterAnalystDTO::getLastAwayOdds)
                .filter(Objects::nonNull)
                .min(Double::compareTo)
                .orElse(null);
        var maxOddA = items.getValue().stream()
                .map(EventFilterAnalystDTO::getLastAwayOdds)
                .filter(Objects::nonNull)
                .max(Double::compareTo)
                .orElse(null);
        var minOddO = items.getValue().stream()
                .map(EventFilterAnalystDTO::getLastOverOdds)
                .filter(Objects::nonNull)
                .min(Double::compareTo)
                .orElse(null);
        var maxOddO = items.getValue().stream()
                .map(EventFilterAnalystDTO::getLastOverOdds)
                .filter(Objects::nonNull)
                .max(Double::compareTo)
                .orElse(null);
        var minOddU = items.getValue().stream()
                .map(EventFilterAnalystDTO::getLastUnderOdds)
                .filter(Objects::nonNull)
                .min(Double::compareTo)
                .orElse(null);
        var maxOddU = items.getValue().stream()
                .map(EventFilterAnalystDTO::getLastUnderOdds)
                .filter(Objects::nonNull)
                .max(Double::compareTo)
                .orElse(null);
        this.minOdd = minOddH != null && minOddA != null ? String.format(FORMAT_SCORE_SUMMARY, minOddH, minOddA, minOddO, minOddU) : null;
        this.maxOdd = maxOddH != null && maxOddA != null ? String.format(FORMAT_SCORE_SUMMARY, maxOddH, maxOddA, maxOddO, maxOddU) : null;
        this.regularOdd = items.getValue().stream()
                .map(it -> FORMAT_SCORE_SUMMARY.formatted(it.getLastHomeOdds(), it.getLastAwayOdds(), it.getLastOverOdds(), it.getLastUnderOdds()))
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(String::toString, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(it -> it.getKey() + " (" + it.getValue() + ")")
                .orElse(null);
    }
}
