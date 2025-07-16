package com.app.kira.schedule;

import com.app.kira.model.BaseOdd;
import com.app.kira.model.EventHtml;
import com.app.kira.model.task.OddsConfig;
import com.app.kira.server.ServerInfoService;
import com.app.kira.util.DateUtil;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Level;

@Service
@Log
@RequiredArgsConstructor
public class EventSchedule {
    private final ServerInfoService serverInfoService;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Transactional
    public void event() {
        var sqlEvents = """
                select id,
                       event_name,
                       event_date as time,
                       detail_link
                from event_crawl
                where status = 'pending' and status = 'failed'
                LIMIT 50
                """;
        var events = jdbcTemplate.query(sqlEvents, BeanPropertyRowMapper.newInstance(EventHtml.class));
        if (events.isEmpty()) {
            return;
        }
        PlaywrightUtil.withPlaywright(events, (page, list) -> {
            list.forEach(evt -> {
                try {

                } catch (Exception ex) {
                    log.log(Level.WARNING, "Error during crawl odd >> {0}", evt);
                    jdbcTemplate.update("""
                              update event_crawl
                              set status  = 'failed'
                              where id = :id
                            """, new MapSqlParameterSource("id", evt.getId()));
                } finally {
                    log.log(Level.WARNING, "Crawl Event {0}-{1} Done", new Object[]{evt.getId(), evt.getEventName()});
                }
            });
        });
    }

    private <T extends BaseOdd> List<T> parseOdds(Document doc, Function<List<Element>, T> rowMapper) {
        return doc.select("table.el-table__body")
                .select("tr.el-table__row")
                .stream()
                .map(r -> rowMapper.apply(r.select("td")))
                .filter(Objects::nonNull)
                .filter(it -> !it.getOddDate().contains("'"))
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
}
