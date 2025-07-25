package com.app.kira.model;

import com.app.kira.util.DateUtil;
import lombok.*;
import org.jsoup.nodes.Element;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EventHtml {
    private Integer id;
    private String eventName;
    private String homeName;
    private String awayName;
    private String time;
    private String leagueName;
    private String detailLink;

    private int ftHomeScore;
    private int ftAwayScore;
    private int htHomeScore;
    private int htAwayScore;

    private String ftScoreStr;
    private String htScoreStr;
    private String cornerStr;

    private int homeCorner;
    private int awayCorner;

    public EventHtml(Element ele, String leagueName, String date) {
        this.leagueName = leagueName;
        this.homeName = ele.select("[itemprop=homeTeam]").text();
        this.awayName = ele.select("[itemprop=awayTeam]").text();
        this.eventName = "%s v %s".formatted(this.homeName, this.awayName);
        this.time = ele.select(".time").text().concat(" %s".formatted(DateUtil.convertFormater1ToFormater2(date)));
        this.detailLink = ele.absUrl("href").replace("h2h", "odds");

        this.htScoreStr = ele.select(".half-over").text();
        this.ftScoreStr = ele.select(".scores.finished").text();
        this.cornerStr = ele.select(".corner.cornerBox").text();
        var minus = "-";

        var ftScoreTemp = ftScoreStr.split(minus);
        if (ftScoreTemp.length == 2) {
            this.ftHomeScore = parseScore(ftScoreTemp[0].trim());
            this.ftAwayScore = parseScore(ftScoreTemp[1].trim());
        } else {
            this.ftHomeScore = 0;
            this.ftAwayScore = 0;
        }

        var htScoreTemp = htScoreStr.replace("HT", "").split(minus);
        if (htScoreTemp.length == 2) {
            this.htHomeScore = parseScore(htScoreTemp[0].trim());
            this.htAwayScore = parseScore(htScoreTemp[1].trim());
        } else {
            this.htHomeScore = 0;
            this.htAwayScore = 0;
        }

        var cornerTemp = cornerStr.split(minus);
        if (cornerTemp.length == 2) {
            this.homeCorner = parseScore(cornerTemp[0].trim());
            this.awayCorner = parseScore(cornerTemp[1].trim());
        } else {
            this.homeCorner = 0;
            this.awayCorner = 0;
        }
    }

    public static SqlParameterSource toMap(EventHtml eventHtml) {
        return new MapSqlParameterSource()
                .addValue("event_name", eventHtml.getEventName())
                .addValue("home_team", eventHtml.getHomeName())
                .addValue("away_team", eventHtml.getAwayName())
                .addValue("league_name", eventHtml.getLeagueName())
                .addValue("ht_home_score", eventHtml.getHtHomeScore())
                .addValue("ht_away_score", eventHtml.getHtAwayScore())
                .addValue("ft_home_score", eventHtml.getFtHomeScore())
                .addValue("ft_away_score", eventHtml.getFtAwayScore())
                .addValue("ht_score_str", eventHtml.getHtScoreStr())
                .addValue("ft_score_str", eventHtml.getFtScoreStr())
                .addValue("corner_str", eventHtml.getCornerStr())
                .addValue("home_corner", eventHtml.getHomeCorner())
                .addValue("away_corner", eventHtml.getAwayCorner())
                .addValue("event_date", DateUtil.parseDate(eventHtml.getTime()))
                .addValue("detail_link", eventHtml.getDetailLink());
    }

    public int parseScore(String score) {
        try {
            return Integer.parseInt(score.trim());
        } catch (NumberFormatException e) {
            return 0; // Return 0 if parsing fails
        }
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
