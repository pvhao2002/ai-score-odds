package com.app.kira.model;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.*;
import org.springframework.util.CollectionUtils;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        this.oddsGoal = getOddsByType(entry.getValue(), "goal", OddGoal.class);
        this.oddsHandicap = getOddsByType(entry.getValue(), "handicap", OddHandicap.class);
        this.oddsCorner = getOddsByType(entry.getValue(), "corner", OddCorner.class);
    }

    public static <T> List<T> getOddsByType(List<EventDTO> odds, String type, Class<T> clazz) {
        return odds.stream()
                   .filter(odd -> odd.getOddType().equalsIgnoreCase(type))
                   .flatMap(odd -> {
                       var listType = TypeToken.getParameterized(List.class, clazz).getType();
                       List<T> parsedList = gson.fromJson(odd.getOddValue(), listType);
                       return parsedList.stream();
                   })
                   .toList();
    }

    public String toResult() {
        var result = new StringBuilder();
        result.append(
                "Dự đoán tỷ số và lựa chọn kèo cược an toàn nhất dựa trên phân tích dữ liệu dựa trên mục tiêu thi đấu, phong độ 5 trận gần nhất của mỗi đội, lịch sử đối đầu gần đây, đội hình ra sân dự kiến, các ca chấn thương hoặc thẻ phạt ảnh hưởng\n");
        result.append("Event Name: ").append(eventName).append("\n");
        result.append("League Name: ").append(leagueName).append("\n");
        result.append("Event Date: ").append(eventDate).append("\n\n");

        // Helper line
        String line1x2 = "+" + "-".repeat(42) + "+" + "-".repeat(17) + "+" + "-".repeat(17) + "+" + "-".repeat(17) + "+\n";
        String lineHandicap = "+" + "-".repeat(42) + "+" + "-".repeat(17) + "+" + "-".repeat(17) + "+\n";

        if (!CollectionUtils.isEmpty(odds1x2)) {
            // Odds 1x2
            result.append("Odds 1x2:\n");
            result.append(line1x2);
            result.append(String.format("| %-40s | %-15s | %-15s | %-15s |\n", "Date", "1", "X", "2"));
            result.append(line1x2);
            for (Odd1x2 odd : odds1x2) {
                result.append(String.format("| %-40s | %-15s | %-15s | %-15s |\n",
                                            odd.getOddDate(), odd.get_1(), odd.getX(), odd.get_2()
                ));
            }
            result.append(line1x2).append("\n");
        }

        if (!CollectionUtils.isEmpty(oddsGoal)) {
            // Odds Goal
            result.append("Odds Goal:\n");
            result.append(line1x2);
            result.append(String.format("| %-40s | %-15s | %-15s | %-15s |\n", "Date", "Goals", "Over", "Under"));
            result.append(line1x2);
            for (OddGoal odd : oddsGoal) {
                result.append(String.format("| %-40s | %-15s | %-15s | %-15s |\n",
                                            odd.getOddDate(), odd.getGoals(), odd.getOver(), odd.getUnder()
                ));
            }
            result.append(line1x2).append("\n");
        }

        if (!CollectionUtils.isEmpty(oddsHandicap)) {
            // Odds Handicap
            result.append("Odds Handicap:\n");
            result.append(lineHandicap);
            result.append(String.format("| %-40s | %-15s | %-15s |\n", "Date", "Home", "Away"));
            result.append(lineHandicap);
            for (OddHandicap odd : oddsHandicap) {
                result.append(String.format("| %-40s | %-15s | %-15s |\n",
                                            odd.getOddDate(), odd.getHome(), odd.getAway()
                ));
            }
            result.append(lineHandicap).append("\n");
        }

        if (!CollectionUtils.isEmpty(oddsCorner)) {
            // Odds Corner
            result.append("Odds Corner:\n");
            result.append(line1x2);
            result.append(String.format("| %-40s | %-15s | %-15s | %-15s |\n", "Date", "Corner", "Over", "Under"));
            result.append(line1x2);
            for (OddCorner odd : oddsCorner) {
                result.append(String.format("| %-40s | %-15s | %-15s | %-15s |\n",
                                            odd.getOddDate(), odd.getCorner(), odd.getOver(), odd.getUnder()
                ));
            }
            result.append(line1x2);
        }
        return result.toString();
    }
}
