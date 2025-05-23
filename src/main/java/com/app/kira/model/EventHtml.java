package com.app.kira.model;

import com.app.kira.util.DateUtil;
import lombok.*;
import org.jsoup.nodes.Element;

import java.sql.ResultSet;
import java.sql.SQLException;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EventHtml {
    private String eventName;
    private String homeName;
    private String awayName;
    private String time;
    private String leagueName;
    private String detailLink;

    public EventHtml(Element ele, String leagueName) {
        this.leagueName = leagueName;
        this.homeName = ele.select("[itemprop=homeTeam]").text();
        this.awayName = ele.select("[itemprop=awayTeam]").text();
        this.eventName = "%s v %s".formatted(this.homeName, this.awayName);
        this.time = ele.select(".time").text().concat(" %s".formatted(DateUtil.getTomorrowDate("dd-MM-yyyy")));
        this.detailLink = ele.absUrl("href").replace("h2h", "odds");
    }

    public String toResult() {
        return """
                +----------------------+
                Event Name: %s
                League Name: %s
                Event Time: %s
                +----------------------+
                \n
                """.formatted(
                this.eventName,
                this.leagueName,
                this.time
        );
    }
}
