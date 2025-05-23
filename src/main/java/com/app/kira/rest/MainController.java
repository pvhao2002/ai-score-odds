package com.app.kira.rest;

import com.app.kira.model.*;
import com.app.kira.util.DateUtil;
import com.google.gson.Gson;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Log
@RestController
@RequiredArgsConstructor
public class MainController {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final Gson gson = new Gson();
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36";

    @GetMapping(value = "current", produces = MediaType.TEXT_PLAIN_VALUE)
    public Object current(@RequestParam(value = "league_name", defaultValue = "") String leagueName) {
        var sql = """
                SELECT e.event_id,
                       e.event_name,
                       e.event_date,
                       e.league_name,
                       o.odd_type,
                       o.odd_value
                FROM events e
                         INNER JOIN odds o ON e.event_id = o.event_id
                WHERE 1 = 1
                  AND event_date
                    BETWEEN UTC_TIMESTAMP() + INTERVAL 6 HOUR
                    AND UTC_TIMESTAMP() + INTERVAL 10 HOUR
                AND e.league_name LIKE :league_name
                ORDER BY event_date
                """;
        var param = new MapSqlParameterSource()
                .addValue("league_name", "%" + leagueName + "%");
        return jdbcTemplate.query(sql, param, (rs, i) -> new EventDTO(rs))
                           .stream()
                           .collect(Collectors.groupingBy(EventDTO::getEventId))
                           .entrySet()
                           .stream()
                           .map(EventResult::new)
                           .map(EventResult::toResult)
                           .collect(Collectors.joining("\n"));
    }

    @GetMapping(value = "test", produces = MediaType.TEXT_PLAIN_VALUE)
    public Object test() throws IOException {
        var result = new ArrayList<EventHtml>();
        try (var playwright = Playwright.create()) {
            var browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            var context = browser.newContext(
                    new Browser
                            .NewContextOptions()
                            .setUserAgent(USER_AGENT)
                            .setJavaScriptEnabled(true)
                            .setIgnoreHTTPSErrors(true));

            var page = context.newPage();
            page.navigate("https://www.aiscore.com/%s".formatted(DateUtil.getTomorrowDate()));
            page.click("span.changeItem:has-text(\"Scheduled\")");
            page.click("span.sortByText:has-text(\"Sort by time\")");

            int previousHeight = 0;
            int currentHeight;
            int maxTries = 100;
            int scrollStep = 800;
            int tries = 0;

            while (tries < maxTries) {
                // Phân tích nội dung mới
                var pageSource = page.content();
                var doc = Jsoup.parse(pageSource, "https://www.aiscore.com/");

                getEvent(doc).forEach(e -> result.stream()
                                                 .filter(it -> it.getEventName().equals(e.getEventName()))
                                                 .findFirst()
                                                 .ifPresentOrElse(
                                                         it -> System.out.println("Số lượng: " + result.size()),
                                                         () -> result.add(e)
                                                 ));

                currentHeight = ((Number) page.evaluate("() => document.body.scrollHeight")).intValue();

                if (currentHeight <= previousHeight) {
                    break; // Không còn phần tử mới load
                }
                page.evaluate("window.scrollBy(0, %d)".formatted(scrollStep));
                page.waitForTimeout(1000); // Đợi nội dung mới load (1s)
                previousHeight += scrollStep;
                tries++;
            }

            browser.close();

            var params = result.stream()
                               .map(it -> new MapSqlParameterSource()
                                       .addValue("event_link", it.getDetailLink())
                                       .addValue("event_name", it.getEventName())
                                       .addValue("league_name", it.getLeagueName())
                                       .addValue("event_date", DateUtil.parseDate(it.getTime())))
                               .toList();
            var sql = "insert into events(detail_link, event_name, event_date, league_name) values (:event_link, :event_name, :event_date, :league_name)";
            jdbcTemplate.batchUpdate(sql, params.toArray(new MapSqlParameterSource[0]));


            return """
                    %s
                    \n
                    \n
                    %s
                    """.formatted(
                    !result.isEmpty() ? "Có %s sự kiện".formatted(result.size()) : "Không có sự kiện nào"
                    , result.stream().map(EventHtml::toResult).collect(Collectors.joining("\n")));
        }
    }

    private List<EventHtml> getEvent(Document doc) {
        return doc.select(".vue-recycle-scroller__item-view")
                  .stream()
                  .map(l -> {
                      var leagueName = "%s %s".formatted(
                              l.select(".country-name").text(),
                              l.select(".compe-name").text()
                      );
                      return l.select("a.match-container").stream().map(e -> new EventHtml(e, leagueName)).toList();
                  })
                  .flatMap(Collection::stream)
                  .toList();
    }

    @GetMapping("crawl-odd")
    public void crawalOdd() {
        crawlOdd();
    }

    @Scheduled(fixedRate = 2 * 60 * 1000)
    public void crawlOdd() {
        // process 20 events every 2 minutes
        log.info("Start crawl odd begin: " + new Date());
        var sql = """
                SELECT event_id, event_name, event_date, league_name, detail_link
                FROM events
                WHERE 1 = 1
                  AND event_date BETWEEN UTC_TIMESTAMP() + INTERVAL 7 HOUR AND UTC_TIMESTAMP() + INTERVAL 11 HOUR
                  AND is_crawl_odds = 'N'
                LIMIT 20
                """;
        var events = jdbcTemplate.query(sql, (rs, i) -> new Event(rs));
        if (!CollectionUtils.isEmpty(events)) {
            var odds = events.stream()
                             .map(it -> {
                                 var bet = getBet(it.getDetailLink());
                                 var param1x2 = new MapSqlParameterSource()
                                         .addValue("event_id", it.getEventId())
                                         .addValue("odd_value", gson.toJson(bet.getOdds1x2()))
                                         .addValue("odd_type", "1x2");
                                 var paramHandicap = new MapSqlParameterSource()
                                         .addValue("event_id", it.getEventId())
                                         .addValue("odd_value", gson.toJson(bet.getOddsHandicap()))
                                         .addValue("odd_type", "handicap");
                                 var paramGoal = new MapSqlParameterSource()
                                         .addValue("event_id", it.getEventId())
                                         .addValue("odd_value", gson.toJson(bet.getOddsGoal()))
                                         .addValue("odd_type", "goals");
                                 var paramCorner = new MapSqlParameterSource()
                                         .addValue("event_id", it.getEventId())
                                         .addValue("odd_value", gson.toJson(bet.getOddsCorner()))
                                         .addValue("odd_type", "corners");
                                 return List.of(param1x2, paramHandicap, paramGoal, paramCorner);
                             })
                             .flatMap(Collection::stream)
                             .toList()
                             .toArray(new MapSqlParameterSource[0]);
            var sqlInsert = "insert into odds(odd_type, odd_value, event_id) values (:odd_type, :odd_value, :event_id)";
            jdbcTemplate.batchUpdate(sqlInsert, odds);
            var sqlUpdate = "update events set is_crawl_odds = 'Y' where event_id = :event_id";
            var params = events.stream()
                               .map(it -> new MapSqlParameterSource()
                                       .addValue("event_id", it.getEventId()))
                               .toList();
            jdbcTemplate.batchUpdate(sqlUpdate, params.toArray(new MapSqlParameterSource[0]));
        }
    }

    @GetMapping(value = "k", produces = MediaType.TEXT_PLAIN_VALUE)
    public Object k(@RequestParam("url") String url) {
        return getBet(url).toResult();
    }

    private Bet getBet(String url) {
        var bet = Bet.builder();
        try (var playwright = Playwright.create()) {
            var browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            var context = browser.newContext(
                    new Browser.NewContextOptions()
                            .setUserAgent(USER_AGENT)
                            .setJavaScriptEnabled(true)
                            .setIgnoreHTTPSErrors(true));

            var page = context.newPage();
            page.navigate(url);
            page.waitForTimeout(1_500);
            var lookBoxes = page.querySelectorAll(".lookBox");

            if (lookBoxes.size() >= 2) {
                lookBoxes.get(1).click();
                page.waitForTimeout(2_000);
                var doc = Jsoup.parse(page.content());
                var homeTeam = doc.select("[itemprop=homeTeam]").text();
                var awayTeam = doc.select("[itemprop=awayTeam]").text();
                var leagueName = doc.select(".comp-name a").text();
                var eventDate = doc.select("[itemprop=startDate]").text();

                var odd1x2 = parseOdds(doc, tds -> new Odd1x2(
                        tds.getFirst().text(),
                        tds.get(1).text(),
                        tds.get(2).text(),
                        tds.getLast().text()
                ));

                bet = bet.eventDate(eventDate)
                         .eventName("%s v %s".formatted(homeTeam, awayTeam))
                         .leagueName(leagueName)
                         .odds1x2(odd1x2);

                var oddButton = page.querySelectorAll(".changeItem");

                if (oddButton.size() >= 4) {
                    oddButton.get(1).click();
                    page.waitForTimeout(2_000);
                    doc = Jsoup.parse(page.content());
                    var oddHandicap = parseOdds(doc, tds -> new OddHandicap(
                            tds.getFirst().text(),
                            tds.get(1).text(),
                            tds.getLast().text()
                    ));

                    bet = bet.oddsHandicap(oddHandicap);

                    oddButton.get(2).click();
                    page.waitForTimeout(2_000);
                    doc = Jsoup.parse(page.content());
                    var oddGoal = parseOdds(doc, tds -> new OddGoal(
                            tds.getFirst().text(),
                            tds.get(1).text(),
                            tds.get(2).text(),
                            tds.getLast().text()
                    ));
                    bet = bet.oddsGoal(oddGoal);

                    oddButton.get(3).click();
                    page.waitForTimeout(2_000);
                    doc = Jsoup.parse(page.content());
                    var oddCorner = parseOdds(doc, tds -> new OddCorner(
                            tds.getFirst().text(),
                            tds.get(1).text(),
                            tds.get(2).text(),
                            tds.getLast().text()
                    ));
                    bet = bet.oddsCorner(oddCorner);
                }
            }


            browser.close();
        }

        return bet.build();
    }

    private <T extends BaseOdd> List<T> parseOdds(Document doc, Function<List<Element>, T> rowMapper) {
        return doc.select("table.el-table__body")
                  .select("tr.el-table__row")
                  .stream()
                  .map(r -> rowMapper.apply(r.select("td")))
                  .filter(Objects::nonNull)
                  .sorted(Comparator.comparing(
                          (T o) -> DateUtil.parseOddDate(o.getOddDate())
                  ).reversed())
                  .limit(15)
                  .toList();
    }


}
