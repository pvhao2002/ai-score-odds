package com.app.kira.util;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@UtilityClass
public class DateUtil {
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("h:mm a dd-MM-yyyy");
    private static final DateTimeFormatter formatter2 = DateTimeFormatter.ofPattern("h:mm a yyyy-MM-dd");
    private static final DateTimeFormatter formatter3 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final DateTimeFormatter FORMATTER1 = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final DateTimeFormatter FORMATTER2 = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public String currentDateNow() {
        return LocalDateTime.now().format(formatter3);
    }

    public String next12Hours() {
        return LocalDateTime.now().plusHours(12).format(formatter3);
    }

    public String next7Days() {
        return LocalDateTime.now().plusDays(7).format(formatter3);
    }

    public static String convertFormater1ToFormater2(String date) {
        LocalDate localDate = LocalDate.parse(date, FORMATTER1);
        return localDate.format(FORMATTER2);
    }

    public static String getTodayDate() {
        return getTodayDate("yyyyMMdd");
    }

    public static String getTodayDate(String pattern) {
        LocalDate today = LocalDate.now();
        return today.format(DateTimeFormatter.ofPattern(pattern));
    }

    public static String getTomorrowDate() {
        return getTomorrowDate("yyyyMMdd");
    }

    public static String getDate(String pattern, LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern(pattern));
    }

    public static String getDateForDB(String date) {
        if (StringUtils.isBlank(date)) return "";
        List<DateTimeFormatter> formatters = List.of(
                formatter,
                formatter2
        );
        LocalDateTime dateTime = null;
        for (DateTimeFormatter formatter : formatters) {
            try {
                dateTime = LocalDateTime.parse(date, formatter);
            } catch (Exception ignored) {
            }
        }
        return dateTime == null
                ? ""
                : dateTime.format(formatter3);
    }

    public static String getTomorrowDate(String pattern) {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        return tomorrow.format(DateTimeFormatter.ofPattern(pattern));
    }

    public LocalDateTime parseDate(String date) {
        try {
            return LocalDateTime.parse(date, formatter);
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(date, formatter2);
            } catch (Exception ex) {
                return LocalDateTime.parse(date);
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
        for (DateTimeFormatter formatter : FORMATTERS) {
            try {
                return LocalDateTime.parse(oddDate, formatter);
            } catch (Exception ignored) {
            }
        }
        return defaultDate;
    }

    public static String convertToGMT7(String isoDateTime) {
        try {
            OffsetDateTime offsetDateTime = OffsetDateTime.parse(isoDateTime);
            ZonedDateTime gmt7Time = offsetDateTime.atZoneSameInstant(ZoneId.of("Asia/Ho_Chi_Minh"));
            return gmt7Time.toLocalDateTime().format(formatter3); // formatter3 = "yyyy-MM-dd HH:mm:ss"
        } catch (Exception e) {
            return null; // hoặc ném lỗi tùy vào use-case
        }
    }

    public static void main(String[] args) {
        System.out.println(getDateForDB("1:00 AM 2025-06-01"));
    }
}

