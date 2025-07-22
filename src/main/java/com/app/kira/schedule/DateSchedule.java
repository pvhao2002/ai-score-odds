package com.app.kira.schedule;

import com.app.kira.model.EventHtml;
import com.app.kira.model.analyst.CrawlDate;
import com.app.kira.util.Constants;
import com.app.kira.util.DateUtil;
import com.app.kira.util.PlaywrightUtil;
import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.jsoup.Jsoup;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.logging.Level;

@Log
@Service
@RequiredArgsConstructor
public class DateSchedule {
    private static final String STATUS = "status";
    private static final String MATCH_BOX_SELECTOR = ".match-box";
    private static final String INSERT_SQL_EVENT_ANALYST = """
            insert into event_analyst(event_name
            , home_team
            , away_team
            , league_name
            , event_date
            , ht_home_score
            , ht_away_score
            , ft_home_score
            , ft_away_score
            , ht_score_str
            , ft_score_str
            , home_corner
            , away_corner
            , corner_str
            , link)
            values (:event_name
            , :home_team
            , :away_team
            , :league_name
            , :event_date
            , :ht_home_score
            , :ht_away_score
            , :ft_home_score
            , :ft_away_score
            , :ht_score_str
            , :ft_score_str
            , :home_corner
            , :away_corner
            , :corner_str
            , :detail_link)
            on duplicate key update
                home_team = values(home_team),
                away_team = values(away_team),
                league_name = values(league_name),
                ht_home_score = values(ht_home_score),
                ht_away_score = values(ht_away_score),
                ft_home_score = values(ft_home_score),
                ft_away_score = values(ft_away_score),
                ht_score_str = values(ht_score_str),
                ft_score_str = values(ft_score_str),
                home_corner = values(home_corner),
                away_corner = values(away_corner),
                corner_str = values(corner_str),
                link = values(link)
            """;
    private static final String SQL_CRAWL_DATE = """
            insert into crawl_date (date, status)
                        values (:date, :status)
                        on duplicate key update status     = values(status),
                                        created_at = current_timestamp
            """;
    private static final String SQL_INSERT_EVENT_CRAWL = """
                INSERT INTO event_crawl(event_name, event_date, detail_link)
                            VALUES (:event_name, :event_date, :detail_link)
                            ON DUPLICATE KEY UPDATE
                                detail_link = VALUES(detail_link),
                                status      = 'pending'
            """;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Scheduled(cron = "0 0 19 * * ?", zone = "Asia/Ho_Chi_Minh")
    @Retryable(retryFor = Exception.class, backoff = @Backoff(delay = 60_000, multiplier = 2))
    public void crawlTomorrowEvent() {
        var date = DateUtil.getTomorrowDate();
        PlaywrightUtil.withPlaywright(Collections.emptyList(), (page, list) -> {
            try {
                var result = new ArrayList<EventHtml>();
                page.navigate(Constants.AI_SCORE_URL + "%s".formatted(date));
                page.waitForSelector(MATCH_BOX_SELECTOR,
                        new Page.WaitForSelectorOptions().setTimeout(20_000)
                );
                page.click("span.changeItem:has-text(\"Scheduled\")");
                page.click("span.sortByText:has-text(\"Sort by time\")");
                crawlEvent(result, page, date);
                var params = result.stream()
                        .map(it -> new MapSqlParameterSource()
                                .addValue("event_link", it.getDetailLink())
                                .addValue("event_name", it.getEventName())
                                .addValue("league_name", it.getLeagueName())
                                .addValue("event_date", DateUtil.parseDate(it.getTime())))
                        .toArray(MapSqlParameterSource[]::new);
                var sql = """
                        insert into events(detail_link, event_name, event_date, league_name)
                        values (:event_link, :event_name, :event_date, :league_name)
                        ON DUPLICATE KEY UPDATE
                            league_name = values(league_name)
                        """;
                jdbcTemplate.batchUpdate(sql, params);
            } catch (Exception ex) {
                log.log(Level.WARNING, "Error during crawlTomorrowEvent", ex);
            } finally {
                jdbcTemplate.update("""
                        insert ignore into kira_league(league_name)
                        select distinct league_name
                        from events
                        order by league_name
                        """, Map.of());
                jdbcTemplate.update("""
                        update events ea
                            inner join kira_league kl on kl.league_name = ea.league_name
                        set ea.league_id = kl.league_id
                        where ea.league_id is null
                        """, Map.of());
            }
        });
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Ho_Chi_Minh") // Every day at midnight
    @Transactional
    public void crawlByDate() {
        var sql = """
                select *
                from crawl_date
                where status = 'PENDING' OR status = 'FAILED'
                LIMIT 10
                for update
                skip locked
                """;
        var list = jdbcTemplate.query(sql, BeanPropertyRowMapper.newInstance(CrawlDate.class));
        if (list.isEmpty()) {
            return;
        }
        PlaywrightUtil.withPlaywright(list, (page, dates) -> {
            for (var it : dates) {
                var date = it.getDate();
                log.info(" crawlByDate for date: " + date);
                var paramsDate = new MapSqlParameterSource("date", date);
                var result = new ArrayList<EventHtml>();
                try {
                    jdbcTemplate.update(SQL_CRAWL_DATE, paramsDate.addValue(STATUS, "in_progress"));
                    page.navigate(Constants.AI_SCORE_URL + "%s".formatted(date));
                    page.waitForSelector(
                            MATCH_BOX_SELECTOR,
                            new Page.WaitForSelectorOptions().setTimeout(20_000)
                    );
                    page.click("span.changeItem:has-text(\"Finished\")");
                    page.click("span.sortByText:has-text(\"Sort by time\")");
                    crawlEvent(result, page, date);
                    var params = result.stream()
                            .map(EventHtml::toMap)
                            .toArray(SqlParameterSource[]::new);
                    jdbcTemplate.batchUpdate(SQL_INSERT_EVENT_CRAWL, params);
                    jdbcTemplate.batchUpdate(INSERT_SQL_EVENT_ANALYST, params);
                } catch (Exception ex) {
                    log.log(Level.WARNING, "Error during analystDate", ex);
                    jdbcTemplate.update(SQL_CRAWL_DATE, paramsDate.addValue(STATUS, "failed"));
                } finally {
                    log.info("Crawl analystDate for date: " + date + " done at " + new Date());
                    jdbcTemplate.update("""
                            delete
                            from crawl_date
                            where date = :date and status = 'in_progress'
                            """, paramsDate);
                }
            }
        });
    }

    private void crawlEvent(List<EventHtml> result, Page page, String date) {
        int previousHeight = 0;
        int currentHeight;
        int maxTries = 2000;
        int scrollStep = 500;
        int tries = 0;

        while (tries < maxTries) {
            var pageSource = page.content();
            var doc = Jsoup.parse(pageSource, Constants.AI_SCORE_URL);
            var events = doc.select(".vue-recycle-scroller__item-view")
                    .stream()
                    .map(l -> {
                        var leagueName = "%s %s".formatted(
                                l.select(".country-name").text(),
                                l.select(".compe-name").text()
                        );
                        return l.select("a.match-container")
                                .stream()
                                .map(e -> new EventHtml(e, leagueName, date))
                                .toList();
                    })
                    .flatMap(Collection::stream)
                    .filter(e -> result.stream()
                            .filter(item -> item.getEventName().equalsIgnoreCase(e.getEventName())
                                    &&
                                    item.getLeagueName().equalsIgnoreCase(e.getLeagueName())
                                    &&
                                    item.getTime().equalsIgnoreCase(e.getTime())
                            )
                            .findFirst()
                            .isEmpty())
                    .toList();
            result.addAll(events);
            currentHeight = ((Number) page.evaluate("() => document.body.scrollHeight")).intValue();

            if (currentHeight <= previousHeight) {
                break;
            }
            page.evaluate("window.scrollBy(0, %d)".formatted(scrollStep));
            page.waitForTimeout(1_000);
            previousHeight += scrollStep;
            tries++;
        }
    }
}
