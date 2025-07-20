package com.app.kira.schedule;

import com.app.kira.model.*;
import com.app.kira.model.task.OddsConfig;
import com.app.kira.server.ServerInfoService;
import com.app.kira.util.DateUtil;
import com.app.kira.util.JsonUtil;
import com.app.kira.util.PlaywrightUtil;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.logging.Level;

@Service
@Log
@RequiredArgsConstructor
public class EventSchedule {
    private static final String MONEY_LINE_1X2 = "1x2";
    private static final String HDC = "hdc";
    private static final String OU = "ou";
    private static final String CORNER = "corner";
    private static final String EVENT_NAME = "event_name";
    private static final String EVENT_DATE = "event_date";
    private static final String ODD_VALUE = "odd_value";
    private static final String ODD_TYPE = "odd_type";
    private static final String SQL_INSERT_ODD_ANALYST = """
                        INSERT INTO odd_analyst(event_id, odd_type, odd_value)
                             SELECT e.event_id, :odd_type, :odd_value
                             FROM event_analyst e
                             WHERE TRUE
                               AND e.event_name = :event_name
                               AND e.event_date = :event_date
                             ON DUPLICATE KEY UPDATE odd_value = :odd_value
            """;

    private final ServerInfoService serverInfoService;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Scheduled(fixedDelay = 60, timeUnit = TimeUnit.SECONDS, initialDelay = 10)
    @Transactional
    public void event() {
        var sqlEvents = """
                select id,
                       event_name,
                       event_date as time,
                       detail_link
                from event_crawl
                where status = 'pending' or status = 'failed'
                LIMIT 50
                for update
                skip locked
                """;
        var events = jdbcTemplate.query(sqlEvents, BeanPropertyRowMapper.newInstance(EventHtml.class));
        if (events.isEmpty()) {
            return;
        }
        PlaywrightUtil.withPlaywright(events, (page, list) -> list.forEach(event -> {
            List<MapSqlParameterSource> result = new ArrayList<>();
            var bet = Bet.builder();
            try {
                log.log(Level.INFO, "Crawl Event {0}-{1}-{2} Start", new Object[]{event.getId(), event.getEventName(), event.getDetailLink()});
                jdbcTemplate.update(
                        "update event_crawl set status = 'in_progress' where id = :id",
                        new MapSqlParameterSource().addValue("id", event.getId())
                );
                page.navigate(event.getDetailLink() + "/odds");
                page.waitForSelector(".lookBox", new Page.WaitForSelectorOptions().setTimeout(30_000));
                var lookBoxes = page.querySelectorAll(".lookBox.brb");
                if (!lookBoxes.isEmpty()) {
                    lookBoxes.getFirst().click();
                    var oddButton = page.querySelectorAll(".changeItem");
                    if (oddButton.size() >= 4) {
                        var oddsConfigMap = getOddsConfigMap();
                        var betSetterMap = getBetSetterMap();

                        for (int idx = 1; idx <= 4; idx++) {
                            OddsConfig<BaseOdd> config = oddsConfigMap.get(idx);
                            if (config == null) continue;
                            List<?> odds = clickAndParseOdds(page, oddButton, idx, config);
                            BiConsumer<Bet.BetBuilder, List<?>> setter = betSetterMap.get(idx);
                            if (setter != null) setter.accept(bet, odds);
                        }
                    }
                    var resultBet = bet.build();
                    resultBet.cleanOdd();
                    result.add(new MapSqlParameterSource()
                            .addValue(EVENT_NAME, event.getEventName())
                            .addValue(EVENT_DATE, event.getTime())
                            .addValue(ODD_VALUE, JsonUtil.toJson(resultBet.getOdds1x2()))
                            .addValue(ODD_TYPE, MONEY_LINE_1X2));

                    result.add(new MapSqlParameterSource()
                            .addValue(EVENT_NAME, event.getEventName())
                            .addValue(EVENT_DATE, event.getTime())
                            .addValue(ODD_VALUE, JsonUtil.toJson(resultBet.getOddsHandicap()))
                            .addValue(ODD_TYPE, HDC));

                    result.add(new MapSqlParameterSource()
                            .addValue(EVENT_NAME, event.getEventName())
                            .addValue(EVENT_DATE, event.getTime())
                            .addValue(ODD_VALUE, JsonUtil.toJson(resultBet.getOddsGoal()))
                            .addValue(ODD_TYPE, OU));

                    result.add(new MapSqlParameterSource()
                            .addValue(EVENT_NAME, event.getEventName())
                            .addValue(EVENT_DATE, event.getTime())
                            .addValue(ODD_VALUE, JsonUtil.toJson(resultBet.getOddsCorner()))
                            .addValue(ODD_TYPE, CORNER));
                    jdbcTemplate.batchUpdate(SQL_INSERT_ODD_ANALYST, result.toArray(new MapSqlParameterSource[0]));
                    jdbcTemplate.update("""
                              insert into pc(pc_name, event_id, status) VALUES (:os, :event_id, 'ok')
                            """, new MapSqlParameterSource().addValue("event_id", event.getId()).addValue("os", serverInfoService.getHostName()));
                }
            } catch (Exception ex) {
                log.log(Level.SEVERE, "Crawl Event %s-%s-%s Failed".formatted(event.getId(), event.getEventName(), event.getDetailLink()), ex);
                jdbcTemplate.update(
                        "update event_crawl set status = 'failed' where id = :id",
                        new MapSqlParameterSource().addValue("id", event.getId())
                );
                jdbcTemplate.update("""
                        insert into pc(pc_name, event_id, status, message) VALUES (:os, :event_id, 'fail', :message)
                          """, new MapSqlParameterSource()
                        .addValue("message", ex.getMessage())
                        .addValue("event_id", event.getId())
                        .addValue("os", serverInfoService.getHostName()));
            } finally {
                var sqlDel = "DELETE FROM event_crawl  WHERE id=:id AND status = 'in_progress'";
                jdbcTemplate.update(sqlDel, new MapSqlParameterSource().addValue("id", event.getId()));
                log.log(Level.INFO, "Crawl Event {0}-{1}-{2} End", new Object[]{event.getId(), event.getEventName(), event.getDetailLink()});
            }
        }));
    }

    private <T extends BaseOdd> List<T> parseOdds(Document doc, Function<List<Element>, T> rowMapper) {
        return doc.select("table.el-table__body")
                .select("tr.el-table__row")
                .stream()
                .map(r -> rowMapper.apply(r.select("td")))
                .filter(Objects::nonNull)
                .filter(it -> !it.getOddDate().contains("'") || !it.getOddDate().contains("HT"))
                .filter(it -> DateUtil.parseOddDate(it.getOddDate(), null) != null)
                .sorted(Comparator.comparing((T o) -> DateUtil.parseOddDate(o.getOddDate())).reversed())
                .toList();
    }

    private <T extends BaseOdd> List<T> clickAndParseOdds(Page page, List<ElementHandle> oddButtons, int btnIndex, OddsConfig<T> config) {
        if (oddButtons.size() < btnIndex) return Collections.emptyList();
        if (btnIndex > 0) {
            oddButtons.get(btnIndex - 1).click();
        }
        page.waitForTimeout(3_500);
        Document doc = Jsoup.parse(page.content());
        return parseOdds(doc, config.rowMapper());
    }

    private Map<Integer, OddsConfig<BaseOdd>> getOddsConfigMap() {
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
        return oddsConfigMap;
    }

    private Map<Integer, BiConsumer<Bet.BetBuilder, List<?>>> getBetSetterMap() {
        Map<Integer, BiConsumer<Bet.BetBuilder, List<?>>> betSetterMap = new HashMap<>();
        betSetterMap.put(1, (builder, odds) -> builder.odds1x2((castList(odds))));
        betSetterMap.put(2, (builder, odds) -> builder.oddsHandicap((castList(odds))));
        betSetterMap.put(3, (builder, odds) -> builder.oddsGoal((castList(odds))));
        betSetterMap.put(4, (builder, odds) -> builder.oddsCorner((castList(odds))));
        return betSetterMap;
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> castList(List<?> list) {
        return (List<T>) list;
    }
}
