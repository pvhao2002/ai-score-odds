package com.app.kira.util;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

@UtilityClass
public class OddConverter {

    /**
     * Chuyển đổi chuỗi kèo kiểu "2.5/3" thành số double (trung bình).
     *
     * @param oddsStr chuỗi kèo, ví dụ "2.5/3"
     * @return giá trị trung bình, ví dụ 2.75
     * @throws IllegalArgumentException nếu đầu vào không hợp lệ
     */
    public static double convertOverUnderOdds(String oddsStr) {
        if (StringUtils.isBlank(oddsStr)) {
            throw new IllegalArgumentException("Chuỗi odds không được rỗng.");
        }

        var parts = oddsStr.trim().split("/");
        if (parts.length == 1) {
            return Double.parseDouble(parts[0]);
        }

        if (parts.length != 2) {
            throw new IllegalArgumentException("Định dạng odds không hợp lệ: " + oddsStr);
        }

        try {
            double first = Double.parseDouble(parts[0]);
            double second = Double.parseDouble(parts[1]);
            return (first + second) / 2.0;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Không thể parse số từ: " + oddsStr);
        }
    }
}
