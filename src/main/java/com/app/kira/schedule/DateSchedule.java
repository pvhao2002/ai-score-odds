package com.app.kira.schedule;

import com.app.kira.model.EventHtml;
import com.app.kira.model.analyst.CrawlDate;
import com.app.kira.server.ServerInfoService;
import com.app.kira.util.Constants;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.logging.Level;

@Log
@Service
@RequiredArgsConstructor
public class DateSchedule {
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
                        values (:date, 'in_progress')
                        on duplicate key update status     = %s,
                                        created_at = current_timestamp
            """;
    private static final String SQL_INSERT_EVENT_CRAWL = """
                INSERT INTO event_crawl(event_name, event_date, detail_link)
                            VALUES (:event_name, :event_date, :detail_link)
                            ON DUPLICATE KEY UPDATE
                                detail_link = VALUES(detail_link),
                                status      = 'pending'
            """;
    private final ServerInfoService serverInfoService;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Ho_Chi_Minh") // Every day at midnight
    @Retryable(
            retryFor = {Exception.class},
            backoff = @Backoff(delay = 3_600_000) // delay 1 hour (3,600,000 milliseconds)
    )
    @Transactional
    public void crawlByDate() {
        if (serverInfoService.isNotActive()) {
            return;
        }
        var sql = """
                select *
                from crawl_date
                where status = 'PENDING' OR status = 'FAILED'
                for update
                skip locked
                """;
        var list = jdbcTemplate.query(sql, BeanPropertyRowMapper.newInstance(CrawlDate.class));
        if (list.isEmpty()) {
            log.info("No crawl date found");
            return;
        }
        PlaywrightUtil.withPlaywright(list, (page, dates) -> {
            for (var it : dates) {
                var date = it.getDate();
                log.info(" crawlByDate for date: " + date);
                var paramsDate = new MapSqlParameterSource("date", date);
                var result = new ArrayList<EventHtml>();
                try {
                    jdbcTemplate.update(SQL_CRAWL_DATE.formatted("'in_progress'"), paramsDate);
                    page.navigate(Constants.AI_SCORE_URL + "%s".formatted(date));
                    page.waitForSelector(
                            ".match-box",
                            new Page.WaitForSelectorOptions().setTimeout(20_000)
                    );
                    page.click("span.changeItem:has-text(\"Finished\")");
                    page.click("span.sortByText:has-text(\"Sort by time\")");

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
                        page.waitForTimeout(5_000);
                        previousHeight += scrollStep;
                        tries++;
                    }
                    var params = result.stream()
                            .map(EventHtml::toMap)
                            .toArray(SqlParameterSource[]::new);
                    jdbcTemplate.batchUpdate(SQL_INSERT_EVENT_CRAWL, params);
                    jdbcTemplate.batchUpdate(INSERT_SQL_EVENT_ANALYST, params);
                } catch (Exception ex) {
                    log.log(Level.WARNING, "Error during analystDate", ex);
                    jdbcTemplate.update(SQL_CRAWL_DATE.formatted("'failed'"), paramsDate);
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
}
