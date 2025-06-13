package com.app.kira.rest;

import com.app.kira.model.EventHtml;
import com.app.kira.model.analyst.CrawlDate;
import com.app.kira.model.task.CrawlTask;
import com.app.kira.util.DateUtil;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.jsoup.Jsoup;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;

@Log
@RequestMapping("/analyst")
@RestController
@RequiredArgsConstructor
public class AnalystController {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private static final int BROWSER_POOL_SIZE = 10;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36";
    private String osName;

    @PostConstruct
    public void onInit() {
        this.osName = System.getProperty("os.name").toLowerCase();
    }

    @GetMapping("os")
    public Object os() throws UnknownHostException {
        var address = InetAddress.getLocalHost();
        var hostName = address.getHostName();
        return System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + hostName + " " + address.getHostAddress();
    }

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
        var startDate = LocalDate.now().minusDays(150);
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

    private void analystEventByDate(String date, Page page) {
        var paramsDate = new MapSqlParameterSource()
                .addValue("date", date);
        var sqlCrawlDate = """
                insert into crawl_date (date, status)
                values (:date, 'in_progress')
                on duplicate key update status     = %s,
                                created_at = current_timestamp
                """;
        var result = new ArrayList<EventHtml>();
        try {
            jdbcTemplate.update(sqlCrawlDate.formatted("'in_progress'"), paramsDate);
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
                    """, params);
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
                try (var playwright = Playwright.create()) {
                    var browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
                    var context = browser.newContext(
                            new Browser.NewContextOptions()
                                    .setUserAgent(USER_AGENT)
                                    .setJavaScriptEnabled(true)
                                    .setIgnoreHTTPSErrors(true));

                    var page = context.newPage();
                    for (var it : subList) {
                        log.info("Crawl analystScheduleEventByDate for date: " + it.getDate());
                        analystEventByDate(it.getDate(), page);
                    }
                    page.close();
                    context.close();
                    browser.close();
                }
            }, executor);
            futures.add(future);
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();
        log.info("Crawl analystScheduleEventByDate end: " + new Date());
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 10_000)
    public void event() {
        jdbcTemplate.update("""
                 update event_crawl
                 set status = 'picked',
                     worker = :worker,
                     pick_time = now()
                 where status = 'pending' or status = 'failed'
                limit 200
                """, new MapSqlParameterSource().addValue("worker", osName));
        var sqlEvents = """
                select id,
                       event_name,
                       event_date as time,
                       detail_link
                from event_crawl
                where status = 'picked' and worker = :worker
                LIMIT 200
                """;
        var events = jdbcTemplate.query(sqlEvents, new MapSqlParameterSource().addValue("worker", osName), BeanPropertyRowMapper.newInstance(EventHtml.class));
        if (events.isEmpty()) {
            return;
        }

        log.info("Start crawl odd begin: " + new Date());
        int batchSize = (int) Math.ceil(events.size() / (double) BROWSER_POOL_SIZE);
        try (ExecutorService executor = Executors.newFixedThreadPool(BROWSER_POOL_SIZE);) {
            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < events.size(); i += batchSize) {
                int toIndex = Math.min(i + batchSize, events.size());
                List<EventHtml> subList = events.subList(i, toIndex);
                futures.add(executor.submit(new CrawlTask(subList, jdbcTemplate, osName)));
            }
            for (Future<?> future : futures) {
                future.get();
            }
            executor.shutdown();
        } catch (Exception e) {
            log.log(Level.WARNING, "Error during crawl odd", e);
            jdbcTemplate.update("""
                    update event_crawl
                    set status = 'failed',
                        worker = null,
                        pick_time = null
                    where worker = :worker
                    """, new MapSqlParameterSource().addValue("worker", osName));
        } finally {
            log.info("Crawl odd end: " + new Date());
        }
    }

    @Scheduled(fixedDelay = 1000 * 60 * 60 * 24, initialDelay = 10_000)
    public void revokeEventCrawl() {
        var sql = """
                update event_crawl
                set worker= null,
                    pick_time= null
                where pick_time < (now() - interval 10 MINUTE)
                """;
        jdbcTemplate.update(sql, Map.of());
    }
}
