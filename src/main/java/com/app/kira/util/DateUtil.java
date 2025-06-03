package com.app.kira.util;

import lombok.experimental.UtilityClass;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@UtilityClass
public class DateUtil {
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("h:mm a dd-MM-yyyy");
    private static final DateTimeFormatter formatter2 = DateTimeFormatter.ofPattern("h:mm a yyyy-MM-dd");
    private static final DateTimeFormatter ODD_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("h:mm a EEEE, MMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter ODD_DATE_FORMATTER1 =
            DateTimeFormatter.ofPattern("h:mm a EEEE, MMMM d, yyyy", Locale.ENGLISH);

    private static final DateTimeFormatter FORMATTER1 = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final DateTimeFormatter FORMATTER2 = DateTimeFormatter.ofPattern("yyyy-MM-dd");

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

    public static String getTomorrowDate(String pattern) {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        return tomorrow.format(DateTimeFormatter.ofPattern(pattern));
    }

    public LocalDateTime parseDate(String date) {
        try {
            return LocalDateTime.parse(date, formatter);
        } catch (Exception e) {
            return LocalDateTime.parse(date, formatter2);
        }
    }

    private static final List<DateTimeFormatter> FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("h:mm a EEEE, MMMM d, yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("h:mm a EEE, MMM d, yyyy", Locale.ENGLISH)
    );

    public LocalDateTime parseOddDate(String oddDate) {
        for (DateTimeFormatter formatter : FORMATTERS) {
            try {
                return LocalDateTime.parse(oddDate, formatter);
            } catch (Exception ignored) {
            }
        }
        return LocalDateTime.now();
    }

}

