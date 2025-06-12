package com.app.kira.rest;

import com.app.kira.model.*;
import com.app.kira.model.task.BrowserPool;
import com.app.kira.util.DateUtil;
import com.google.gson.Gson;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.PdfWriter;
import com.microsoft.playwright.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Log
@RestController
@RequiredArgsConstructor
public class MainController {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final Gson gson = new Gson();
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36";
    private static final int BROWSER_POOL_SIZE = 5;

    private final String PROMPT = """
            I will provide a list of upcoming football matches below.
            For each match, please predict the result and provide betting recommendations for the following markets:
            – Asian Handicap
            – 1X2 (European odds)
            – Over/Under Goals
            – Over/Under Corners
            – Over/Under Cards
                        
            Base your analysis on current form, head-to-head record, team news, and tactical trends. If detailed data is not available, use probability and general patterns.
                        
            Matches:
                        
            %s
            …
                        
            For each match, please present your answer in this format:
                        
            Score prediction:
                        
            Asian Handicap pick:
                        
            1X2 pick:
                        
            Over/Under Goals pick:
                        
            Over/Under Corners pick:
                        
            Over/Under Cards pick:
                        
            Reasoning:
            """;

    @GetMapping(value = "under", produces = MediaType.TEXT_PLAIN_VALUE)
    public Object under(@RequestParam(required = false, defaultValue = "1") String mode) {
        var events = getEvents("");
        return events.stream()
                .filter(it -> List.of("2.25", "2/2.5").contains(Optional.ofNullable(it.getOddsGoal())
                        .filter(odds -> !odds.isEmpty())
                        .map(List::getFirst)
                        .map(OddGoal::getGoals)
                        .orElse("null")))
                .collect(Collectors.collectingAndThen(
                        Collectors.toList(),
                        list -> "Tổng số event: " + list.size() + "\n" +
                                list.stream()
                                        .map(it -> "1".equalsIgnoreCase(mode) ? it.toResultUnder() : it.toResult(true))
                                        .collect(Collectors.joining("\n"))
                ));
    }

    @GetMapping(value = "current", produces = MediaType.TEXT_PLAIN_VALUE)
    public Object current(
            @RequestParam(value = "league_name", defaultValue = "") String leagueName,
            @RequestParam(value = "showOdd", required = false) Boolean showOdd
    ) {
        return getEvents(leagueName)
                .stream()
                .collect(Collectors.collectingAndThen(
                        Collectors.toList(),
                        list -> "Tổng số event: " + list.size() + "\n" +
                                list.stream()
                                        .map(it -> it.toResult(true))
                                        .collect(Collectors.joining("\n"))
                ));
    }

    @GetMapping(value = "new-predict", produces = MediaType.TEXT_PLAIN_VALUE)
    public Object newPredict(
            @RequestParam(value = "league_name", defaultValue = "") String leagueName
    ) {
        return getEvents(leagueName)
                .stream()
                .collect(Collectors.collectingAndThen(
                        Collectors.toList(),
                        list -> "Tổng số event: " + list.size() + "\n" +
                                PROMPT.formatted(
                                        IntStream.range(0, list.size())
                                                .mapToObj(i -> list.get(i).toResult(i + 1))
                                                .collect(Collectors.joining("\n"))
                                ) + "\n\n"
                ));
    }

    private List<EventResult> getEvents(String leagueName) {
        var sql = """
                SELECT e.event_id,
                       e.event_name,
                       e.event_date,
                       e.league_name,
                       o.odd_type,
                       o.odd_value
                FROM events e
                         LEFT JOIN odds o ON e.event_id = o.event_id
                WHERE e.event_date BETWEEN :start_date AND :end_date
                   AND e.league_name LIKE :league_name
                ORDER BY e.event_date
                """;
        var startDate = DateUtil.currentDateNow();
        var endDate = DateUtil.next7Days();
        var param = new MapSqlParameterSource()
                .addValue("start_date", startDate)
                .addValue("end_date", endDate)
                .addValue("league_name", "%" + leagueName + "%");
        return jdbcTemplate.query(sql, param, (rs, i) -> new EventDTO(rs))
                .stream()
                .collect(Collectors.groupingBy(EventDTO::getEventId))
                .entrySet()
                .stream()
                .map(EventResult::new)
                .sorted(Comparator.comparing(EventResult::getEventDate))
                .toList();
    }

    @GetMapping(value = "test", produces = MediaType.TEXT_PLAIN_VALUE)
    public Object test(@RequestParam("date") String date) throws IOException {
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
            page.navigate("https://www.aiscore.com/%s".formatted(date));
            page.click("span.changeItem:has-text(\"Scheduled\")");
            page.click("span.sortByText:has-text(\"Sort by time\")");

            int previousHeight = 0;
            int currentHeight;
            int maxTries = 2000;
            int scrollStep = 500;
            int tries = 0;

            while (tries < maxTries) {
                System.out.println("Crawl time: " + tries + ", number of events: " + result.size());

                // Phân tích nội dung mới
                var pageSource = page.content();
                var doc = Jsoup.parse(pageSource, "https://www.aiscore.com/");

                getEvent(doc, date).forEach(e -> result.stream()
                        .filter(it -> it.getEventName().equals(e.getEventName()))
                        .findFirst()
                        .ifPresentOrElse(
                                it -> {
                                },
                                () -> result.add(e)
                        ));

                currentHeight = ((Number) page.evaluate("() => document.body.scrollHeight")).intValue();

                if (currentHeight <= previousHeight) {
                    break; // Không còn phần tử mới load
                }
                page.evaluate("window.scrollBy(0, %d)".formatted(scrollStep));
                page.waitForTimeout(300);
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
            var sql = """
                    insert into events(detail_link, event_name, event_date, league_name) 
                    values (:event_link, :event_name, :event_date, :league_name)
                    ON DUPLICATE KEY UPDATE
                        league_name = :league_name
                    """;
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

    private List<EventHtml> getEvent(Document doc, String date) {
        return doc.select(".vue-recycle-scroller__item-view")
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
                .toList();
    }

    //    @Scheduled(fixedDelay = 60 * 60 * 1000, initialDelay = 15 * 60 * 1000)
    @GetMapping("odd-1")
    public void crawalOdd() throws InterruptedException {
        crawlOdd();
    }

    @GetMapping("league")
    public Object getLeagues(@RequestParam(value = "day", defaultValue = "today") String day) {
        var sql = """
                select league_name, MIN(event_date) AS event_date
                from events
                WHERE event_date BETWEEN
                          CASE
                              WHEN :day = 'today' THEN CONCAT('2025-06-05', ' 00:00:00')
                              WHEN :day = 'tomorrow' THEN CONCAT(DATE_ADD('2025-06-05', INTERVAL 1 DAY), ' 00:00:00')
                              END
                          AND
                          CASE
                              WHEN :day = 'today' THEN CONCAT('2025-06-05', ' 23:59:59')
                              WHEN :day = 'tomorrow' THEN CONCAT(DATE_ADD('2025-06-05', INTERVAL 1 DAY), ' 23:59:59')
                              END
                GROUP BY league_name
                HAVING event_date > DATE_ADD(NOW(), INTERVAL 7 HOUR)
                ORDER BY event_date
                """;
        var param = new MapSqlParameterSource()
                .addValue("day", day);
        return jdbcTemplate.query(
                sql,
                param,
                (rs, i) -> new String[]{rs.getString("league_name"), rs.getString("event_date")}
        );
    }

    //    @Scheduled(fixedDelay = 3 * 30 * 1000)
    public void crawlOdd() {
        var sql = """
                select event_id, event_name, event_date, league_name, detail_link
                from events
                where true 
                and event_date between '2025-06-10 00:00:00' AND '2025-06-14 23:59:00' --  :start_date AND :end_date
                order by last_update, event_date
                """;
        var startDate = DateUtil.currentDateNow();
        var endDate = DateUtil.next7Days();
        var param = new MapSqlParameterSource()
                .addValue("start_date", startDate)
                .addValue("end_date", endDate);
        var events = jdbcTemplate.query(sql, param, (rs, i) -> new Event(rs));
        if (CollectionUtils.isEmpty(events)) {
            return;
        }
        log.info("Start crawl odd begin: " + new Date());
        int batchSize = (int) Math.ceil((double) events.size() / BROWSER_POOL_SIZE);
        ExecutorService executor = Executors.newFixedThreadPool(BROWSER_POOL_SIZE);

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < events.size(); i += batchSize) {
            int toIndex = Math.min(i + batchSize, events.size());
            List<Event> subList = events.subList(i, toIndex);

            int finalI = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try (var playwright = Playwright.create()) {
                    var browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false));
                    var context = browser.newContext(
                            new Browser.NewContextOptions()
                                    .setUserAgent(USER_AGENT)
                                    .setJavaScriptEnabled(true)
                                    .setIgnoreHTTPSErrors(true));

                    var page = context.newPage();
                    int times = 1;
                    for (Event it : subList) {
                        List<MapSqlParameterSource> result = new ArrayList<>();
                        log.info("Crawl odd for event (%d/%d) of index(%d-%d): ".formatted(
                                times,
                                subList.size(),
                                finalI,
                                toIndex
                        ) + it.getEventName() + " - " + it.getEventDate());
                        var bet = getBet(it.getDetailLink(), page);
                        if (bet == null) {
                            log.warning("Bet is null for event: " + it.getEventName());
                            continue;
                        }

                        result.add(new MapSqlParameterSource()
                                .addValue("event_id", it.getEventId())
                                .addValue("odd_value", gson.toJson(bet.getOdds1x2()))
                                .addValue("odd_type", "1x2"));

                        result.add(new MapSqlParameterSource()
                                .addValue("event_id", it.getEventId())
                                .addValue("odd_value", gson.toJson(bet.getOddsHandicap()))
                                .addValue("odd_type", "handicap"));

                        result.add(new MapSqlParameterSource()
                                .addValue("event_id", it.getEventId())
                                .addValue("odd_value", gson.toJson(bet.getOddsGoal()))
                                .addValue("odd_type", "goals"));

                        result.add(new MapSqlParameterSource()
                                .addValue("event_id", it.getEventId())
                                .addValue("odd_value", gson.toJson(bet.getOddsCorner()))
                                .addValue("odd_type", "corners"));
                        times++;
                        var sqlInsert = """
                                insert into odds(odd_type, odd_value, event_id) 
                                values (:odd_type, :odd_value, :event_id)
                                ON DUPLICATE KEY UPDATE
                                    odd_value = :odd_value
                                """;
                        jdbcTemplate.batchUpdate(sqlInsert, result.toArray(new MapSqlParameterSource[0]));
                        var sqlUpdate = "update events set number_updated = number_updated + 1 where event_id = :event_id";
                        jdbcTemplate.update(sqlUpdate, new MapSqlParameterSource()
                                .addValue("event_id", it.getEventId()));
                        log.log(Level.INFO, "Crawl odd for event (%d/%d) of index(%d-%d): %s - %s".formatted(
                                times,
                                subList.size(),
                                finalI,
                                toIndex,
                                it.getEventName(),
                                it.getEventDate()
                        ));
                    }
                    log.info("Crawl odd end of index" + finalI + "-" + toIndex + ": " + new Date());
                    browser.close();
                }
            }, executor);
            futures.add(future);
        }


        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();
    }

    private Bet getBet(String url, Page page) {
        var bet = Bet.builder();
        page.navigate(url);
        page.waitForSelector(".lookBox", new Page.WaitForSelectorOptions().setTimeout(20_000));
        var lookBoxes = page.querySelectorAll(".lookBox");
        if (lookBoxes.size() >= 2) {
            lookBoxes.get(1).click();
            page.waitForTimeout(1000);
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
                page.waitForTimeout(1000);
                doc = Jsoup.parse(page.content());
                var oddHandicap = parseOdds(doc, tds -> new OddHandicap(
                        tds.getFirst().text(),
                        tds.get(1).text(),
                        tds.getLast().text()
                ));

                bet = bet.oddsHandicap(oddHandicap);

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
        }
        return bet.build();
    }

    @GetMapping("/generate-pdf")
    public Object generatePdf(@RequestParam String url) throws Exception {
        List<String> imageLinks = new CopyOnWriteArrayList<>();
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            BrowserContext context = browser.newContext();
            Page page = context.newPage();

            // Lắng nghe network request
            page.onRequestFinished(request -> {
                String u = request.url();
                if (u.startsWith("https://drive.google.com/viewerng/img")) {
                    System.out.println("Found image link: " + u);
                    imageLinks.add(u);
                }
            });

            // Mở trang
            page.navigate(url);
            page.waitForTimeout(3000);

            for (int i = 0; i < 430; i++) {
                if (imageLinks.size() == 192) {
                    break; // Dừng nếu đã đủ 192 ảnh
                }
                System.out.println("Crawl time: " + i + ", number of images: " + imageLinks.size());
                page.evaluate("""
                        () => {
                            const scroller = document.querySelector('.ndfHFb-c4YZDc-cYSp0e-s2gQvd, .ndfHFb-c4YZDc-s2gQvd, .ndfHFb-c4YZDc-s2gQvd-sn54Q') || document.scrollingElement;
                            if (scroller) {
                                scroller.scrollBy(0, 800);
                            }
                        }
                        """);
                page.waitForTimeout(300);
            }
            System.out.println("Số ảnh tìm thấy: " + imageLinks.size());
            // Đóng trình duyệt
            browser.close();
        }
        // Sắp xếp theo param page
        List<String> sortedLinks = imageLinks.stream()
                .sorted(Comparator.comparingInt(link -> {
                    Matcher matcher = Pattern.compile("[?&]page=(\\d+)").matcher(link);
                    return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
                }))
                .toList();
        System.out.println("Số ảnh sau khi sắp xếp: " + sortedLinks.size());
        ImageIO.scanForPlugins();
        // Tạo PDF
        String outputPath = "images_output.pdf";
        try (FileOutputStream fos = new FileOutputStream(outputPath)) {
            com.lowagie.text.Document document = new com.lowagie.text.Document();
            PdfWriter.getInstance(document, fos);
            document.open();

            for (String imgUrl : sortedLinks) {
                try (InputStream in = new URL(imgUrl).openStream()) {
                    BufferedImage img = ImageIO.read(in);
                    if (img != null) {
                        byte[] compressedBytes = compressJpeg(img, 0.5f); // 50% quality
                        Image pdfImg = Image.getInstance(compressedBytes);
                        pdfImg.scaleToFit(PageSize.A4.getWidth(), PageSize.A4.getHeight());
                        pdfImg.setAlignment(Image.ALIGN_CENTER);
                        document.add(pdfImg);
                        document.newPage();
                    } else {
                        System.err.println("Image is null: " + imgUrl);
                    }
                } catch (Exception e) {
                    System.err.println("Failed to load image: " + imgUrl);
                }
            }

            document.close();
        }

        // Trả về file
        File file = new File(outputPath);
        InputStreamResource resource = new InputStreamResource(new FileInputStream(file));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=" + file.getName())
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(file.length())
                .body(resource);
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

    public byte[] compressJpeg(BufferedImage image, float quality) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        ImageWriter jpgWriter = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam jpgWriteParam = jpgWriter.getDefaultWriteParam();
        jpgWriteParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        jpgWriteParam.setCompressionQuality(quality); // từ 0.0 (thấp nhất) đến 1.0 (cao nhất)

        ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
        jpgWriter.setOutput(ios);
        jpgWriter.write(null, new IIOImage(image, null, null), jpgWriteParam);

        jpgWriter.dispose();
        return baos.toByteArray();
    }
}
