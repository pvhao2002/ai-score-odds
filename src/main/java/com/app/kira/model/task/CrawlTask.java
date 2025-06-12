package com.app.kira.model.task;

import com.app.kira.model.*;
import com.app.kira.util.DateUtil;
import com.google.gson.Gson;
import com.microsoft.playwright.*;
import lombok.Data;
import lombok.extern.java.Log;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

@Log
@Data
public class CrawlTask implements Runnable {
    private final List<EventHtml> events;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final Gson gson = new Gson();
    private final String os;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36";

    public CrawlTask(List<EventHtml> events, NamedParameterJdbcTemplate jdbcTemplate, String os) {
        this.events = events;
        this.jdbcTemplate = jdbcTemplate;
        this.os = os;
    }

    @Override
    public void run() {
        if (events.isEmpty()) {
            log.info("No events to process.");
            return;
        }
        log.info("Starting crawl task for " + events.size() + " events on OS: " + os);
        try (var playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false));
             BrowserContext context = browser.newContext(
                     new Browser.NewContextOptions()
                             .setUserAgent(USER_AGENT))) {
            Page page = context.newPage();

            for (EventHtml event : events) {
                List<MapSqlParameterSource> result = new ArrayList<>();
                var bet = Bet.builder();
                try {
                    jdbcTemplate.update(
                            "update event_crawl set status = 'in_progress' where id = :id",
                            new MapSqlParameterSource().addValue("id", event.getId())
                    );
                    page.navigate(event.getDetailLink() + "/odds");
                    page.waitForSelector(".lookBox", new Page.WaitForSelectorOptions().setTimeout(30_000));
                    var lookBoxes = page.querySelectorAll(".lookBox");
                    if (!lookBoxes.isEmpty()) {
                        lookBoxes.getFirst().click();
                        var oddButton = page.querySelectorAll(".changeItem");
                        if (oddButton.size() >= 4) {
                            Map<Integer, OddsConfig<BaseOdd>> oddsConfigMap = new HashMap<>();

                            oddsConfigMap.put(1, new OddsConfig<>("1x2", tds -> new Odd1x2(
                                    tds.getFirst().text(),
                                    tds.get(1).text(),
                                    tds.get(2).text(),
                                    tds.getLast().text()
                            )));
                            oddsConfigMap.put(2, new OddsConfig<>("Handicap", tds -> new OddHandicap(
                                    tds.getFirst().text(),
                                    tds.get(1).text(),
                                    tds.getLast().text()
                            )));
                            oddsConfigMap.put(3, new OddsConfig<>("Over/Under", tds -> new OddGoal(
                                    tds.getFirst().text(),
                                    tds.get(1).text(),
                                    tds.get(2).text(),
                                    tds.getLast().text()
                            )));
                            oddsConfigMap.put(4, new OddsConfig<>("Corner", tds -> new OddCorner(
                                    tds.getFirst().text(),
                                    tds.get(1).text(),
                                    tds.get(2).text(),
                                    tds.getLast().text()
                            )));
                            Map<Integer, BiConsumer<Bet.BetBuilder, List<?>>> betSetterMap = new HashMap<>();
                            betSetterMap.put(1, (builder, odds) -> builder.odds1x2((List<Odd1x2>) odds));
                            betSetterMap.put(2, (builder, odds) -> builder.oddsHandicap((List<OddHandicap>) odds));
                            betSetterMap.put(3, (builder, odds) -> builder.oddsGoal((List<OddGoal>) odds));
                            betSetterMap.put(4, (builder, odds) -> builder.oddsCorner((List<OddCorner>) odds));

                            for (int idx = 1; idx <= 4; idx++) {
                                OddsConfig<BaseOdd> config = oddsConfigMap.get(idx);
                                if (config == null) continue;
                                List<?> odds = clickAndParseOdds(page, oddButton, idx, config);
                                BiConsumer<Bet.BetBuilder, List<?>> setter = betSetterMap.get(idx);
                                if (setter != null) setter.accept(bet, odds);
                            }
                        }
                        var resultBet = bet.build();
                        result.add(new MapSqlParameterSource()
                                .addValue("event_name", event.getEventName())
                                .addValue("event_date", event.getTime())
                                .addValue("odd_value", gson.toJson(resultBet.getOdds1x2()))
                                .addValue("odd_type", "1x2"));

                        result.add(new MapSqlParameterSource()
                                .addValue("event_name", event.getEventName())
                                .addValue("event_date", event.getTime())
                                .addValue("odd_value", gson.toJson(resultBet.getOddsHandicap()))
                                .addValue("odd_type", "hdc"));

                        result.add(new MapSqlParameterSource()
                                .addValue("event_name", event.getEventName())
                                .addValue("event_date", event.getTime())
                                .addValue("odd_value", gson.toJson(resultBet.getOddsGoal()))
                                .addValue("odd_type", "ou"));

                        result.add(new MapSqlParameterSource()
                                .addValue("event_name", event.getEventName())
                                .addValue("event_date", event.getTime())
                                .addValue("odd_value", gson.toJson(resultBet.getOddsCorner()))
                                .addValue("odd_type", "corner"));
                        var sqlInsert = """
                                insert into odd_analyst(event_id, odd_type, odd_value)
                                                          SELECT e.id, :odd_type, :odd_value
                                                          FROM event_analyst e
                                                          WHERE TRUE
                                                            AND e.event_name = :event_name
                                                            AND e.event_date = :event_date
                                                          ON DUPLICATE KEY UPDATE odd_value = :odd_value
                                """;
                        jdbcTemplate.batchUpdate(sqlInsert, result.toArray(new MapSqlParameterSource[0]));
                        System.out.println("Insert odd for event: " + event.getEventName() + " - " + event.getTime() + " success");
                        jdbcTemplate.update("""
                                  insert into pc(pc_name, event_id, status) VALUES (:os, :event_id, 'ok')                  
                                """, new MapSqlParameterSource().addValue("event_id", event.getId()).addValue("os", os));
                    }
                } catch (Exception ex) {
                    jdbcTemplate.update(
                            "update event_crawl set status = 'failed' where id = :id",
                            new MapSqlParameterSource().addValue("id", event.getId())
                    );
                    jdbcTemplate.update("""
                            insert into pc(pc_name, event_id, status) VALUES (:os, :event_id, 'fail')                  
                              """, new MapSqlParameterSource().addValue("event_id", event.getId()).addValue("os", os));
                    System.err.println("Error processing event: " + event.getEventName() + " - " + event.getTime() + " get ex: " + ex.getMessage());
                } finally {
                    var sqlDel = "DELETE FROM event_crawl  WHERE id=:id AND status = 'in_progress'";
                    jdbcTemplate.update(sqlDel, new MapSqlParameterSource().addValue("id", event.getId()));
                }
            }
        }
    }

    private <T extends BaseOdd> List<T> parseOdds(Document doc, Function<List<Element>, T> rowMapper) {
        return doc.select("table.el-table__body")
                .select("tr.el-table__row")
                .stream()
                .map(r -> rowMapper.apply(r.select("td")))
                .filter(Objects::nonNull)
                .filter(it -> !it.getOddDate().contains("'"))
                .filter(it -> DateUtil.parseOddDate(it.getOddDate(), null) != null)
                .sorted(Comparator.comparing(
                        (T o) -> DateUtil.parseOddDate(o.getOddDate())
                ).reversed())
                .limit(15)
                .toList();
    }

    private <T extends BaseOdd> List<T> clickAndParseOdds(Page page, List<ElementHandle> oddButtons, int btnIndex, OddsConfig<T> config) {
        if (oddButtons.size() < btnIndex) return Collections.emptyList();
        if (btnIndex > 0)
            oddButtons.get(btnIndex - 1).click();
        page.waitForTimeout(1_500);
        Document doc = Jsoup.parse(page.content());
        return parseOdds(doc, config.rowMapper());
    }
}

