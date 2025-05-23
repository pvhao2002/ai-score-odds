package com.app.kira.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Bet {
    private String eventName;
    private String leagueName;
    private String eventDate;

    @Builder.Default
    private List<Odd1x2> odds1x2 = new ArrayList<>();

    @Builder.Default
    private List<OddGoal> oddsGoal = new ArrayList<>();

    @Builder.Default
    private List<OddHandicap> oddsHandicap = new ArrayList<>();

    @Builder.Default
    private List<OddCorner> oddsCorner = new ArrayList<>();

    public String toResult() {
        var result = new StringBuilder();

        result.append("Event Name: ").append(eventName).append("\n");
        result.append("League Name: ").append(leagueName).append("\n");
        result.append("Event Date: ").append(eventDate).append("\n\n");

        // Helper line
        String line1x2 = "+" + "-".repeat(42) + "+" + "-".repeat(17) + "+" + "-".repeat(17) + "+" + "-".repeat(17) + "+\n";
        String lineHandicap = "+" + "-".repeat(42) + "+" + "-".repeat(17) + "+" + "-".repeat(17) + "+\n";

        // Odds 1x2
        result.append("Odds 1x2:\n");
        result.append(line1x2);
        result.append(String.format("| %-40s | %-15s | %-15s | %-15s |\n", "Date", "1", "X", "2"));
        result.append(line1x2);
        for (Odd1x2 odd : odds1x2) {
            result.append(String.format("| %-40s | %-15s | %-15s | %-15s |\n",
                                        odd.getOddDate(), odd.get_1(), odd.getX(), odd.get_2()));
        }
        result.append(line1x2).append("\n");

        // Odds Goal
        result.append("Odds Goal:\n");
        result.append(line1x2);
        result.append(String.format("| %-40s | %-15s | %-15s | %-15s |\n", "Date", "Goals", "Over", "Under"));
        result.append(line1x2);
        for (OddGoal odd : oddsGoal) {
            result.append(String.format("| %-40s | %-15s | %-15s | %-15s |\n",
                                        odd.getOddDate(), odd.getGoals(), odd.getOver(), odd.getUnder()));
        }
        result.append(line1x2).append("\n");

        // Odds Handicap
        result.append("Odds Handicap:\n");
        result.append(lineHandicap);
        result.append(String.format("| %-40s | %-15s | %-15s |\n", "Date", "Home", "Away"));
        result.append(lineHandicap);
        for (OddHandicap odd : oddsHandicap) {
            result.append(String.format("| %-40s | %-15s | %-15s |\n",
                                        odd.getOddDate(), odd.getHome(), odd.getAway()));
        }
        result.append(lineHandicap).append("\n");

        // Odds Corner
        result.append("Odds Corner:\n");
        result.append(line1x2);
        result.append(String.format("| %-40s | %-15s | %-15s | %-15s |\n", "Date", "Corner", "Over", "Under"));
        result.append(line1x2);
        for (OddCorner odd : oddsCorner) {
            result.append(String.format("| %-40s | %-15s | %-15s | %-15s |\n",
                                        odd.getOddDate(), odd.getCorner(), odd.getOver(), odd.getUnder()));
        }
        result.append(line1x2);

        return result.toString();
    }

}
