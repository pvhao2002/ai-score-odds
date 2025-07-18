package com.app.kira.rest;

import com.app.kira.util.DateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDate;
import java.util.ArrayList;

@Log
@RequestMapping("/analyst")
@RestController
@RequiredArgsConstructor
public class AnalystController {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private static final int BROWSER_POOL_SIZE = 10;
    private String osName;

    @GetMapping("os")
    public Object os() throws UnknownHostException {
        var address = InetAddress.getLocalHost();
        var hostName = address.getHostName();
        return System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + hostName + " " + address.getHostAddress();
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
}
