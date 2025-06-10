package com.app.kira.rest;

import com.app.kira.model.*;
import com.app.kira.model.analyst.CrawlDate;
import com.app.kira.service.OddService;
import com.app.kira.util.DateUtil;
import com.google.gson.Gson;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.logging.Level;

@Log
@RequestMapping("/analyst")
@RestController
@RequiredArgsConstructor
public class AnalystController {
    private final OddService oddService;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final Gson gson = new Gson();
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36";

    @GetMapping("update-score")
    public Object updateScore() {
        var sql = """
                select id, ht_score_str, ft_score_str from event_analyst
                """;
        var events = jdbcTemplate.query(sql, BeanPropertyRowMapper.newInstance(EventHtml.class));
        if (events.isEmpty()) {
            return "No events found";
        }
        var params = events.stream()
                .map(it -> {
                    var p = new MapSqlParameterSource();
                    p.addValue("id", it.getId());
                    return p;
                })
                .toArray(MapSqlParameterSource[]::new);
        return "OK";
    }

    @GetMapping("init")
    public Object init() {
        // loop over the last 100 days to get format yyyyMMdd
        var startDate = LocalDate.now().minusDays(120);
        var endDate = LocalDate.now();
        var sqlInsertCrawlDate = """
                insert into crawl_date (date, status)
                values (:date, 'pending')
                on duplicate key update status = 'pending'
                """;
        var params = new ArrayList<MapSqlParameterSource>();
        for (LocalDate date = startDate; date.isBefore(endDate); date = date.plusDays(1)) {
            var formattedDate = DateUtil.getDate("yyyyMMdd", date);
            params.add(new MapSqlParameterSource().addValue("date", formattedDate));
        }
        jdbcTemplate.batchUpdate(sqlInsertCrawlDate, params.toArray(new MapSqlParameterSource[0]));
        return "OK";
    }

    private void analystEventByDate(String date) {
        var paramsDate = new MapSqlParameterSource()
                .addValue("date", date);
        var sqlCrawlDate = """
                insert into crawl_date (date, status)
                values (:date, 'in_progress')
                on duplicate key update status     = %s,
                                created_at = current_timestamp
                """;
        var result = new ArrayList<EventHtml>();
        try (var playwright = Playwright.create()) {
            jdbcTemplate.update(sqlCrawlDate.formatted("'in_progress'"), paramsDate);
            var browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            var context = browser.newContext(
                    new Browser
                            .NewContextOptions()
                            .setUserAgent(USER_AGENT)
                            .setJavaScriptEnabled(true)
                            .setIgnoreHTTPSErrors(true));

            var page = context.newPage();
            page.navigate("https://www.aiscore.com/%s".formatted(date));
            page.waitForSelector(
                    "span.changeItem:has-text(\"Finished\")",
                    new Page.WaitForSelectorOptions().setTimeout(10_000)
            );
            page.click("span.changeItem:has-text(\"Finished\")");
            page.click("span.sortByText:has-text(\"Sort by time\")");

            int previousHeight = 0;
            int currentHeight;
            int maxTries = 2000;
            int scrollStep = 500;
            int tries = 0;

            while (tries < maxTries) {
                System.out.println("Crawl time: " + tries + ", number of events: " + result.size());
                var pageSource = page.content();
                var doc = Jsoup.parse(pageSource, "https://www.aiscore.com/");
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
                                .filter(it -> it.getEventName().equalsIgnoreCase(e.getEventName())
                                        &&
                                        it.getLeagueName().equalsIgnoreCase(e.getLeagueName())
                                        &&
                                        it.getTime().equalsIgnoreCase(e.getTime())
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
            var params = result.stream()
                    .map(EventHtml::toMap)
                    .toArray(SqlParameterSource[]::new);
            jdbcTemplate.batchUpdate(
                    """
                            INSERT INTO event_crawl(event_name, event_date, detail_link)
                            VALUES (:event_name, :event_date, :detail_link)
                            ON DUPLICATE KEY UPDATE 
                                detail_link = VALUES(detail_link),
                                status      = 'pending'
                            """,
                    params
            );
            jdbcTemplate.batchUpdate("""
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
                    , corner_str)
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
                    , :corner_str)
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
                        corner_str = values(corner_str)
                                                    """, params);
            browser.close();
        } catch (Exception ex) {
            log.log(Level.WARNING, "Error during analystDate", ex);
            jdbcTemplate.update(sqlCrawlDate.formatted("'failed'"), paramsDate);
        } finally {
            log.info("Crawl analystDate for date: " + date + " done at " + new Date());
            jdbcTemplate.update("""
                    delete
                    from crawl_date
                    where date = :date and status = 'in_progress'
                    """, paramsDate);
        }
    }

    @GetMapping("schedule")
    public void analystScheduleEventByDate() {
        var sql = """
                select *
                from crawl_date
                FOR UPDATE SKIP LOCKED
                """;
        var list = jdbcTemplate.query(sql, BeanPropertyRowMapper.newInstance(CrawlDate.class));
        if (list.isEmpty()) {
            log.info("No crawl date found");
            return;
        }
        int batchSize = 10;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            int toIndex = Math.min(i + batchSize, list.size());
            var subList = list.subList(i, toIndex);

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                for (var it : subList) {
                    log.info("Crawl analystScheduleEventByDate for date: " + it.getDate());
                    analystEventByDate(it.getDate());
                }
            }, executor);
            futures.add(future);
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();
        log.info("Crawl analystScheduleEventByDate end: " + new Date());
    }

    @Transactional
    @GetMapping("{date}")
    public Object analystDate(@PathVariable String date) {
        analystEventByDate(date);
        return "OK";
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 10_000)
    public void event() {
        var sqlEvents = """
                select id,
                       event_name,
                       event_date as time,
                       detail_link
                from event_crawl
                where status = 'pending' or status = 'failed'
                LIMIT 5
                FOR UPDATE SKIP LOCKED
                """;
        var events = jdbcTemplate.query(sqlEvents, BeanPropertyRowMapper.newInstance(EventHtml.class));
        if (events.isEmpty()) {
            return;
        }

        log.info("Start crawl odd begin: " + new Date());
        int batchSize = 1;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < events.size(); i += batchSize) {
            int toIndex = Math.min(i + batchSize, events.size());
            List<EventHtml> subList = events.subList(i, toIndex);

            int finalI = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                int times = 1;
                for (var it : subList) {
                    jdbcTemplate.update(
                            "update event_crawl set status = 'in_progress' where id = :id",
                            new MapSqlParameterSource().addValue("id", it.getId())
                    );
                    List<MapSqlParameterSource> result = new ArrayList<>();
                    var bet = Bet.builder();
                    log.info("Crawl odd for event (%d/%d) of index(%d-%d): ".formatted(
                            times,
                            subList.size(),
                            finalI,
                            toIndex
                    ) + it.getEventName() + " - " + it.getTime());

                    try (var playwright = Playwright.create()) {
                        var browser = playwright.chromium()
                                .launch(new BrowserType.LaunchOptions().setHeadless(true));
                        var context = browser.newContext(
                                new Browser
                                        .NewContextOptions()
                                        .setUserAgent(USER_AGENT)
                                        .setJavaScriptEnabled(true)
                                        .setIgnoreHTTPSErrors(true));

                        var page = context.newPage();
                        page.navigate(it.getDetailLink() + "/odds");
                        page.waitForSelector(".lookBox", new Page.WaitForSelectorOptions().setTimeout(10_000));
                        var lookBoxes = page.querySelectorAll(".lookBox");
                        if (lookBoxes.size() >= 2) {
                            lookBoxes.getFirst().click();
                            var doc = Jsoup.parse(page.content());
                            var odd1x2 = parseOdds(doc, tds -> new Odd1x2(
                                    tds.getFirst().text(),
                                    tds.get(1).text(),
                                    tds.get(2).text(),
                                    tds.getLast().text()
                            ));
                            var oddButton = page.querySelectorAll(".changeItem");

                            if (oddButton.size() >= 4) {
                                oddButton.get(1).click();
                                page.waitForTimeout(1000);
                                doc = Jsoup.parse(page.content());
                                var oddHandicap = parseOdds(doc, tds -> new OddHandicap(
                                        tds.getFirst().text(),
                                        tds.get(1).text(),
                                        tds.getLast().text()
                                ));

                                bet = bet.odds1x2(odd1x2)
                                        .oddsHandicap(oddHandicap);

                                oddButton.get(2).click();
                                page.waitForTimeout(1000);
                                doc = Jsoup.parse(page.content());
                                var oddGoal = parseOdds(doc, tds -> new OddGoal(
                                        tds.getFirst().text(),
                                        tds.get(1).text(),
                                        tds.get(2).text(),
                                        tds.getLast().text()
                                ));
                                bet = bet.oddsGoal(oddGoal);

                                oddButton.get(3).click();
                                page.waitForTimeout(1000);
                                doc = Jsoup.parse(page.content());
                                var oddCorner = parseOdds(doc, tds -> new OddCorner(
                                        tds.getFirst().text(),
                                        tds.get(1).text(),
                                        tds.get(2).text(),
                                        tds.getLast().text()
                                ));
                                bet = bet.oddsCorner(oddCorner);
                            }
                            var resultBet = bet.build();
                            result.add(new MapSqlParameterSource()
                                    .addValue("event_name", it.getEventName())
                                    .addValue("event_date", it.getTime())
                                    .addValue("odd_value", gson.toJson(resultBet.getOdds1x2()))
                                    .addValue("odd_type", "1x2"));

                            result.add(new MapSqlParameterSource()
                                    .addValue("event_name", it.getEventName())
                                    .addValue("event_date", it.getTime())
                                    .addValue("odd_value", gson.toJson(resultBet.getOddsHandicap()))
                                    .addValue("odd_type", "hdc"));

                            result.add(new MapSqlParameterSource()
                                    .addValue("event_name", it.getEventName())
                                    .addValue("event_date", it.getTime())
                                    .addValue("odd_value", gson.toJson(resultBet.getOddsGoal()))
                                    .addValue("odd_type", "ou"));

                            result.add(new MapSqlParameterSource()
                                    .addValue("event_name", it.getEventName())
                                    .addValue("event_date", it.getTime())
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
                            times++;
                            log.log(Level.INFO, "Crawl odd for event (%d/%d) of index(%d-%d): %s - %s done".formatted(
                                    times,
                                    subList.size(),
                                    finalI,
                                    toIndex,
                                    it.getEventName(),
                                    it.getTime()
                            ));
                            jdbcTemplate.update("""
                                    insert into pc(pc_name, event_id, status) VALUES ('thinkpad', :event_id, 'ok')                  
                                      """, new MapSqlParameterSource().addValue("event_id", it.getId()));
                        }
                        browser.close();
                    } catch (Exception ex) {
                        log.log(Level.WARNING, "Error processing event: " + it.getEventName(), ex);
                        jdbcTemplate.update(
                                "update event_crawl set status = 'failed' where id = :id",
                                new MapSqlParameterSource().addValue("id", it.getId())
                        );
                        jdbcTemplate.update("""
                                insert into pc(pc_name, event_id, status) VALUES ('thinkpad', :event_id, 'fail')                  
                                  """, new MapSqlParameterSource().addValue("event_id", it.getId()));
                    } finally {
                        var sqlDel = "DELETE FROM event_crawl  WHERE id=:id AND status = 'in_progress'";
                        jdbcTemplate.update(sqlDel, new MapSqlParameterSource().addValue("id", it.getId()));
                    }
                }
                log.info("Crawl odd end of index" + finalI + "-" + toIndex + ": " + new Date());
            }, executor);
            futures.add(future);
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();
        log.info("Crawl odd end: " + new Date());
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
}
