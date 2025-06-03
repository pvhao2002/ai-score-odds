package com.app.kira.model;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.*;
import org.springframework.util.CollectionUtils;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EventResult {
    private static final Gson gson = new Gson();
    private Long eventId;
    private String eventName;
    private String leagueName;
    private Timestamp eventDate;
    @Builder.Default
    private List<Odd1x2> odds1x2 = new ArrayList<>();

    @Builder.Default
    private List<OddGoal> oddsGoal = new ArrayList<>();

    @Builder.Default
    private List<OddHandicap> oddsHandicap = new ArrayList<>();

    @Builder.Default
    private List<OddCorner> oddsCorner = new ArrayList<>();

    public EventResult(Map.Entry<Long, List<EventDTO>> entry) {
        this.eventId = entry.getKey();
        this.eventName = entry.getValue().getFirst().getEventName();
        this.leagueName = entry.getValue().getFirst().getLeagueName();
        this.eventDate = entry.getValue().getFirst().getEventDate();

        this.odds1x2 = getOddsByType(entry.getValue(), "1x2", Odd1x2.class);
        this.oddsGoal = getOddsByType(entry.getValue(), "goals", OddGoal.class);
        this.oddsHandicap = getOddsByType(entry.getValue(), "handicap", OddHandicap.class);
        this.oddsCorner = getOddsByType(entry.getValue(), "corners", OddCorner.class);
    }

    public static <T> List<T> getOddsByType(List<EventDTO> odds, String type, Class<T> clazz) {
        return odds.stream()
                   .filter(odd -> type.equalsIgnoreCase(odd.getOddType()))
                   .flatMap(odd -> {
                       var listType = TypeToken.getParameterized(List.class, clazz).getType();
                       List<T> parsedList = gson.fromJson(odd.getOddValue(), listType);
                       return parsedList.stream();
                   })
                   .toList();
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
