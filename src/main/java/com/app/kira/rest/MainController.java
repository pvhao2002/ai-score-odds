package com.app.kira.rest;

import com.app.kira.model.*;
import com.app.kira.util.DateUtil;
import com.google.gson.Gson;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.PdfWriter;
import com.microsoft.playwright.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Log
@RestController
@RequiredArgsConstructor
public class MainController {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final Gson gson = new Gson();
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36";

    @GetMapping(value = "current", produces = MediaType.TEXT_PLAIN_VALUE)
    public Object current(
            @RequestParam(value = "league_name", defaultValue = "") String leagueName,
            @RequestParam(value = "range_type", defaultValue = "full") String rangeType,
            @RequestParam(value = "showOdd", required = false) Boolean showOdd
    ) {
        var sql = """
                SELECT e.event_id,
                       e.event_name,
                       e.event_date,
                       e.league_name,
                       o.odd_type,
                       o.odd_value
                FROM events e
                         LEFT JOIN odds o ON e.event_id = o.event_id
                WHERE e.event_date BETWEEN
                    CASE
                        WHEN :range_type = 'full'
                            THEN CONVERT_TZ(CURDATE(), '+00:00', '+07:00')
                        ELSE UTC_TIMESTAMP() + INTERVAL 7 HOUR
                        END
                    AND
                    CASE
                        WHEN :range_type = 'full'
                            THEN CONVERT_TZ(CURDATE() + INTERVAL 1 DAY, '+00:00', '+07:00')
                        ELSE UTC_TIMESTAMP() + INTERVAL (7 + 4) HOUR
                        END
                   AND e.league_name LIKE :league_name
                ORDER BY e.event_date
                """;
        var param = new MapSqlParameterSource()
                .addValue("range_type", rangeType)
                .addValue("league_name", "%" + leagueName + "%");
        return jdbcTemplate.query(sql, param, (rs, i) -> new EventDTO(rs))
                           .stream()
                           .collect(Collectors.groupingBy(EventDTO::getEventId))
                           .entrySet()
                           .stream()
                           .map(EventResult::new)
                           .sorted(Comparator.comparing(EventResult::getEventDate))
                           .map(it -> it.toResult(showOdd))
                           .collect(Collectors.joining("\n"));
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
            int maxTries = 200;
            int scrollStep = 800;
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
                page.waitForTimeout(3000); // Đợi nội dung mới load (1s)
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

    @GetMapping("crawl-odd")
    public void crawalOdd() {
        crawlOdd();
    }

    //    @Scheduled(fixedRate = 2 * 60 * 1000)
    public void crawlOdd() {
        // process 20 events every 2 minutes
        var sql = """
                SELECT event_id, event_name, event_date, league_name, detail_link
                FROM events
                WHERE 1 = 1
                  AND event_date BETWEEN UTC_TIMESTAMP() + INTERVAL 8 HOUR AND UTC_TIMESTAMP() + INTERVAL 11 HOUR
                  AND is_crawl_odds = 'N'
                LIMIT 20
                """;
        var events = jdbcTemplate.query(sql, (rs, i) -> new Event(rs));
        if (!CollectionUtils.isEmpty(events)) {
            log.info("Start crawl odd begin: " + new Date());
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
            page.waitForTimeout(2_500);
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
                    } else{
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

    public  byte[] compressJpeg(BufferedImage image, float quality) throws IOException {
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
    public static void main(String[] args) throws MalformedURLException {

        var url = new URL(URLDecoder.decode("https://drive.google.com/viewerng/img?id=ACFrOgDjR3pV7OLyklHDCWz560WnLRZsZUnqUGVvcb_stGolBvQGiNITE1l3kgtBZqNpjXJUqQ7w6gj4oCbSKSaf4p_5Rs1D9t8KFU4jYIsFvOvAym9n2wxH4eyY8YMoX-XeeNK60Krm1_E_egkzCDltj4Maz5-nzpGGWaKQlkM6dVYioahV70euqOPjBH2neaXGrVfBgzDXch4Ad-LefOoUGbSiDdsf20WStN0ozarkOGAbD32BPR35OGKE9PE%3D&page=10&skiphighlight=true&w=1600&webp=true", StandardCharsets.UTF_8));
        try (var in = url.openStream()) {
            BufferedImage img = ImageIO.read(in);
            if (img != null) {
                System.out.println("Image loaded successfully: " + img.getWidth() + "x" + img.getHeight());
            } else{
                System.err.println("Image is null");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
