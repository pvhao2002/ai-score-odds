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
                        Hãy phân tích và nhận định kèo trận đấu bóng đá giữa %s, diễn ra vào %s tại giải %s.
                                                
                        Dựa trên các yếu tố sau, vui lòng cung cấp một phân tích chuyên sâu và lựa chọn kèo cược an toàn nhất:
                                                
                        Mục tiêu thi đấu của hai đội trong bối cảnh giải đấu hiện tại (ví dụ: tranh suất trụ hạng, cạnh tranh vô địch, đã hết động lực, v.v.).
                                                
                        Phong độ 5 trận gần nhất của mỗi đội (trong mọi đấu trường hoặc riêng tại giải đấu).
                                                
                        Lịch sử đối đầu gần đây (H2H – Head-to-head).
                                                
                        Đội hình ra sân dự kiến, bao gồm các chấn thương, treo giò hoặc thiếu vắng đáng chú ý.
                                                
                        Phân tích chiến thuật và lối chơi của cả hai đội.
                                                
                        Sau đó, hãy đưa ra nhận định cụ thể cho từng loại kèo sau:
                                                
                        Kèo châu Á (Handicap)
                                                
                        Kèo tài xỉu (Over/Under)
                                                
                        Kèo phạt góc (Corner)
                                                
                        Kèo thẻ phạt (Cards)
                                                
                        Cuối cùng, dự đoán tỷ số chính xác và nêu rõ lựa chọn kèo có xác suất thắng cao nhất, kèm lý do ngắn gọn.
                        \n
                                """.formatted(getEventName(), getEventDate().toString(), getLeagueName()));
        Optional.ofNullable(showOdd)
                .filter(show -> show)
                .ifPresent(show -> {
                    // Helper line
                    String line1x2 = "+" + "-".repeat(42) + "+" + "-".repeat(17) + "+" + "-".repeat(17) + "+" + "-".repeat(
                            17) + "+\n";
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
                        result.append(line1x2);
                    }
                });
        return result.toString();
    }
}
