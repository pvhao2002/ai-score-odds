package com.app.kira.util;

import lombok.experimental.UtilityClass;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@UtilityClass
public class DateUtil {
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("h:mm a dd-MM-yyyy");
    private static final DateTimeFormatter ODD_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("h:mm a EEEE, MMM d, yyyy", Locale.ENGLISH);

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
        return LocalDateTime.parse(date, formatter);
    }

    public LocalDateTime parseOddDate(String oddDate) {
        return LocalDateTime.parse(oddDate, ODD_DATE_FORMATTER);
    }
}

