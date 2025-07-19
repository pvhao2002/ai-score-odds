package com.app.kira.util;

import lombok.experimental.UtilityClass;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

@Log
@UtilityClass
public class DateUtil {
    private static final String DATE_FORMAT_CRAWL = "yyyyMMdd";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("h:mm a dd-MM-yyyy");
    private static final DateTimeFormatter FORMATTER_WITH_TIME_MINUTES = DateTimeFormatter.ofPattern("h:mm a yyyy-MM-dd");
    private static final DateTimeFormatter FORMATTER_WITH_TIME_MINUTES_V2 = DateTimeFormatter.ofPattern("hh:mm a yyyy-MM-dd");
    private static final DateTimeFormatter FORMATTER3 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final DateTimeFormatter FORMATTER1 = DateTimeFormatter.ofPattern(DATE_FORMAT_CRAWL);

    private static final DateTimeFormatter FORMATTER_WITHOUT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public String currentDateNow() {
        return LocalDateTime.now().format(FORMATTER3);
    }

    public String next12Hours() {
        return LocalDateTime.now().plusHours(12).format(FORMATTER3);
    }

    public String next7Days() {
        return LocalDateTime.now().plusDays(7).format(FORMATTER3);
    }

    public static String convertFormater1ToFormater2(String date) {
        LocalDate localDate = LocalDate.parse(date, FORMATTER1);
        return localDate.format(FORMATTER_WITHOUT_TIME);
    }

    public static String getTodayDate() {
        return getTodayDate(DATE_FORMAT_CRAWL);
    }

    public static String getTodayDate(String pattern) {
        LocalDate today = LocalDate.now();
        return today.format(DateTimeFormatter.ofPattern(pattern));
    }

    public static String getTomorrowDate() {
        return getTomorrowDate(DATE_FORMAT_CRAWL);
    }

    public static String getDate(String pattern, LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern(pattern));
    }

    public static String getDateForDB(String date) {
        if (StringUtils.isBlank(date)) return "";
        List<DateTimeFormatter> formatters = List.of(
                FORMATTER,
                FORMATTER_WITH_TIME_MINUTES
        );
        LocalDateTime dateTime = null;
        for (DateTimeFormatter fmt : formatters) {
            try {
                dateTime = LocalDateTime.parse(date, fmt);
            } catch (Exception exp) {
                log.log(Level.WARNING, "Failed to parse date: " + date + " with formatter: " + fmt, exp);
            }
        }
        return dateTime == null
                ? ""
                : dateTime.format(FORMATTER3);
    }

    public static String getTomorrowDate(String pattern) {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        return tomorrow.format(DateTimeFormatter.ofPattern(pattern));
    }

    public LocalDateTime parseDate(String date) {
        try {
            return LocalDateTime.parse(date, FORMATTER);
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(date, FORMATTER_WITH_TIME_MINUTES);
            } catch (Exception ex) {
                return LocalDateTime.parse(date, FORMATTER_WITH_TIME_MINUTES_V2);
            }
        }
    }

    private static final List<DateTimeFormatter> FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("h:mm a EEEE, MMMM d, yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("h:mm a EEE, MMM d, yyyy", Locale.ENGLISH)
    );

    public LocalDateTime parseOddDate(String oddDate) {
        return parseOddDate(oddDate, LocalDateTime.now());
    }

    public LocalDateTime parseOddDate(String oddDate, LocalDateTime defaultDate) {
        for (DateTimeFormatter fmt : FORMATTERS) {
            try {
                return LocalDateTime.parse(oddDate, fmt);
            } catch (Exception exp) {
                log.log(Level.WARNING, "Failed to parse odd date: " + oddDate + " with formatter: " + fmt, exp);
            }
        }
        return defaultDate;
    }

    public static String convertToGMT7(String isoDateTime) {
        try {
            OffsetDateTime offsetDateTime = OffsetDateTime.parse(isoDateTime);
            ZonedDateTime gmt7Time = offsetDateTime.atZoneSameInstant(ZoneId.of("Asia/Ho_Chi_Minh"));
            return gmt7Time.toLocalDateTime().format(FORMATTER3); // formatter3 = "yyyy-MM-dd HH:mm:ss"
        } catch (Exception e) {
            return null; // hoặc ném lỗi tùy vào use-case
        }
    }
}

