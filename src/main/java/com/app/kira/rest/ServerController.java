package com.app.kira.rest;

import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Log
@RestController
@RequestMapping("server")
@RequiredArgsConstructor
public class ServerController {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    @GetMapping
    public Object listServer() {
        return jdbcTemplate.query("select node from router_setting", (rs, i) -> rs.getString("node"));
    }
}
