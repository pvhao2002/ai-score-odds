package com.app.kira.model;

import com.app.kira.model.analyst.OddAnalyst;
import com.app.kira.util.DateUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EventResult {
    private static final Gson gson = new Gson();
    private Long eventId;
    private String eventName;
    @JsonIgnore
    private String leagueName;
    private String eventDate;
    private String link;
    @Builder.Default
    private List<Odd1x2> odds1x2 = new ArrayList<>();

    @Builder.Default
    private List<OddGoal> oddsGoal = new ArrayList<>();

    @Builder.Default
    private List<OddHandicap> oddsHandicap = new ArrayList<>();

    @Builder.Default
    private List<OddCorner> oddsCorner = new ArrayList<>();

    public List<OddAnalyst> parseOdd() {
        List<OddAnalyst> odds = new ArrayList<>();

        getOdds1x2().stream()
                .filter(odd -> DateUtil.parseOddDate(odd.getOddDate(), null) != null)
                .filter(odd -> StringUtils.isNotBlank(odd.get_1()) && StringUtils.isNotBlank(odd.getX()) && StringUtils.isNotBlank(odd.get_2()))
                .forEach(odd -> odds.add(OddAnalyst.builder()
                        .eventId(getEventId().intValue())
                        .oddType("1x2")
                        .oddDate(DateUtil.parseOddDate(odd.getOddDate(), null))
                        .homeOdd(parse(odd.get_1()))
                        .awayOdd(parse(odd.get_2()))
                        .drawOdd(parse(odd.getX()))
                        .build()));

        getOddsGoal().stream()
                .filter(odd -> DateUtil.parseOddDate(odd.getOddDate(), null) != null)
                .filter(odd -> StringUtils.isNotBlank(odd.getGoals()) && StringUtils.isNotBlank(odd.getOver()) && StringUtils.isNotBlank(odd.getUnder()))
                .forEach(odd -> odds.add(OddAnalyst.builder()
                        .eventId(getEventId().intValue())
                        .oddType("ou")
                        .oddDate(DateUtil.parseOddDate(odd.getOddDate(), null))
                        .line(odd.getGoals())
                        .overOdd(parse(odd.getOver()))
                        .underOdd(parse(odd.getUnder()))
                        .build()));

        getOddsHandicap().stream()
                .filter(odd -> DateUtil.parseOddDate(odd.getOddDate(), null) != null)
                .filter(odd -> StringUtils.isNotBlank(odd.getHome()) && StringUtils.isNotBlank(odd.getAway()))
                .forEach(odd -> odds.add(OddAnalyst.builder()
                        .eventId(getEventId().intValue())
                        .oddType("hdc")
                        .oddDate(DateUtil.parseOddDate(odd.getOddDate(), null))
                        .line(odd.getHome().split(" ")[0] + "#" + odd.getAway().split(" ")[0])
                        .homeOdd(parse(odd.getHome().split(" ")[1]))
                        .awayOdd(parse(odd.getAway().split(" ")[1]))
                        .build()));

        getOddsCorner().stream()
                .filter(odd -> DateUtil.parseOddDate(odd.getOddDate(), null) != null)
                .filter(odd -> StringUtils.isNotBlank(odd.getCorner()) && StringUtils.isNotBlank(odd.getOver()) && StringUtils.isNotBlank(odd.getUnder()))
                .forEach(odd -> odds.add(OddAnalyst.builder()
                        .eventId(getEventId().intValue())
                        .oddType("corner")
                        .oddDate(DateUtil.parseOddDate(odd.getOddDate(), null))
                        .line(odd.getCorner())
                        .overOdd(parse(odd.getOver()))
                        .underOdd(parse(odd.getUnder()))
                        .build()));
        return odds;
    }

    public static Double parse(String value) {
        try {
            return Double.parseDouble(value);
        } catch (Exception ex) {
            return 0.0;
        }
    }

    public EventResult(Map.Entry<Long, List<EventDTO>> entry) {
        this(entry.getValue());
        this.odds1x2 = getLatestOdd(this.getOdds1x2());
        this.oddsGoal = getLatestOdd(this.getOddsGoal());
        this.oddsHandicap = getLatestOdd(this.getOddsHandicap());
        this.oddsCorner = getLatestOdd(this.getOddsCorner());
    }

    private static <T extends BaseOdd> List<T> getLatestOdd(List<T> odds) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("h:mm a EEEE, MMMM d, yyyy", Locale.ENGLISH);
        return odds.stream()
                .filter(o -> StringUtils.isNotBlank(o.getOddDate()))
                .max(Comparator.comparing(o -> LocalDateTime.parse(o.getOddDate(), formatter)))
                .map(List::of)
                .orElse(List.of());
    }


    public EventResult(List<EventDTO> entry) {
        this.eventId = entry.getFirst().getEventId();
        this.eventName = entry.getFirst().getEventName();
        this.leagueName = entry.getFirst().getLeagueName();
        this.eventDate = entry.getFirst().getEventDate();
        this.link = entry.getFirst().getDetailLink();

        this.odds1x2 = getOddsByType(entry, "1x2", Odd1x2.class);
        this.oddsGoal = getOddsByType(entry, "goals, ou", OddGoal.class);
        this.oddsHandicap = getOddsByType(entry, "handicap, hdc", OddHandicap.class);
        this.oddsCorner = getOddsByType(entry, "corners", OddCorner.class);
    }

    public static <T> List<T> getOddsByType(List<EventDTO> odds, String type, Class<T> clazz) {
        return odds.stream()
                .filter(odd -> StringUtils.isNotBlank(odd.getOddType()))
                .filter(odd -> type.contains(odd.getOddType()))
                .flatMap(odd -> {
                    var listType = TypeToken.getParameterized(List.class, clazz).getType();
                    List<T> parsedList = gson.fromJson(odd.getOddValue(), listType);
                    return parsedList.stream();
                })
                .toList();
    }

    public String toResultUnder() {
        return """
                %s
                %s
                %s
                """.formatted(getLeagueName(), getEventName(), getEventDate());
    }

    public String toResult(int idx) {
        return """
                Match: %d
                League: %s
                Event: %s
                Date (GMT+7): %s
                """.formatted(idx, getLeagueName(), getEventName(), getEventDate());
    }

    public String toResult(Boolean showOdd) {
        var result = new StringBuilder();
        result.append(
                """
                        Please provide a comprehensive and intelligent betting analysis for the football match between [%s] in league [%s], scheduled on [%s].
                                                
                        I will provide you with the following betting odds data:
                                                
                        - 1X2 (Match result odds)
                                                
                        - Asian Handicap odds
                                                
                        - Over/Under Goals odds
                                                
                        - Corner betting odds (if available)
                                                
                        In addition to the standard match analysis, please use artificial intelligence to interpret and factor in odds movement and potential sharp money patterns to refine predictions.
                                                
                        Specifically:
                                                
                        - Consider pre-match motivation and goals of each team based on tournament/league context.
                                                
                        - Analyze the last 5-match form, including home/away performance and goal trends.
                                                
                        - Include recent head-to-head record.
                                                
                        - Account for injuries, suspensions, and expected line-ups.
                                                
                        - Examine tactical styles of both teams and their compatibility against each other.
                                                
                        - Use the provided odds to detect any unusual shifts, suspicious movements, or value betting angles.
                                                
                        Provide a breakdown of recommendations for:
                                                
                        - Asian Handicap
                                                
                        - Over/Under Goals
                                                
                        - 1X2 (Match Winner)
                                                
                        - Corner Bet
                                                
                        - Card Bet (if applicable)
                                                
                        Finish with:
                                                
                        - Exact score prediction
                                                
                        - Most reliable/safe betting option
                                                
                        - Any value or contrarian betting insights based on odds movement analysis
                        \n
                        """.formatted(getEventName(), getLeagueName(), getEventDate().toString()));
        Optional.ofNullable(showOdd)
                .filter(show -> show)
                .ifPresent(show -> {
                    // Helper line
                    String line1x2 = "|" + "-".repeat(42) + "|" + "-".repeat(17) + "|" + "-".repeat(17) + "|" + "-".repeat(
                            17) + "|\n";
                    String lineHandicap = "|" + "-".repeat(42) + "|" + "-".repeat(17) + "|" + "-".repeat(17) + "|\n";

                    if (!CollectionUtils.isEmpty(odds1x2)) {
                        // Odds 1x2
                        result.append("Odds 1x2:\n");
                        result.append(String.format("| %-40s | %-15s | %-15s | %-15s |\n", "Date", "1", "X", "2"));
                        result.append(line1x2);
                        for (Odd1x2 odd : odds1x2) {
                            result.append(String.format("| %-40s | %-15s | %-15s | %-15s |\n",
                                    odd.getOddDate(), odd.get_1(), odd.getX(), odd.get_2()
                            ));
                        }
                        result.append("\n");
                    }

                    if (!CollectionUtils.isEmpty(oddsGoal)) {
                        // Odds Goal
                        result.append("Odds Goal:\n");
                        result.append(String.format(
                                "| %-40s | %-15s | %-15s | %-15s |\n",
                                "Date",
                                "Goals",
                                "Over",
                                "Under"
                        ));
                        result.append(line1x2);
                        for (OddGoal odd : oddsGoal) {
                            result.append(String.format("| %-40s | %-15s | %-15s | %-15s |\n",
                                    odd.getOddDate(), odd.getGoals(), odd.getOver(), odd.getUnder()
                            ));
                        }
                        result.append("\n");
                    }

                    if (!CollectionUtils.isEmpty(oddsHandicap)) {
                        // Odds Handicap
                        result.append("Odds Handicap:\n");
                        result.append(String.format("| %-40s | %-15s | %-15s |\n", "Date", "Home", "Away"));
                        result.append(lineHandicap);
                        for (OddHandicap odd : oddsHandicap) {
                            result.append(String.format("| %-40s | %-15s | %-15s |\n",
                                    odd.getOddDate(), odd.getHome(), odd.getAway()
                            ));
                        }
                        result.append("\n");
                    }

                    if (!CollectionUtils.isEmpty(oddsCorner)) {
                        // Odds Corner
                        result.append("Odds Corner:\n");
                        result.append(String.format(
                                "| %-40s | %-15s | %-15s | %-15s |\n",
                                "Date",
                                "Corner",
                                "Over",
                                "Under"
                        ));
                        result.append(line1x2);
                        for (OddCorner odd : oddsCorner) {
                            result.append(String.format("| %-40s | %-15s | %-15s | %-15s |\n",
                                    odd.getOddDate(), odd.getCorner(), odd.getOver(), odd.getUnder()
                            ));
                        }
                    }
                });
        return result.toString();
    }
}
