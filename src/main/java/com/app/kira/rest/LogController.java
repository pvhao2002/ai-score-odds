package com.app.kira.rest;

import com.app.kira.repo.LogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

@Log
@RestController
@RequestMapping("log")
@RequiredArgsConstructor
public class LogController {
    private final LogRepository logRepository;

    @GetMapping("ping")
    public Object ping() {
        log.log(java.util.logging.Level.INFO, "Ping received at LogController");
        return "pong";
    }

    @GetMapping(value = "/host", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamLogsByHost(@RequestParam String h) {
        AtomicLong lastLogId = new AtomicLong(0);
        return Flux.interval(Duration.ofSeconds(1))
                .flatMap(tick -> logRepository.findNewLogs(h, lastLogId.get())
                        .onErrorResume(e -> Flux.empty()))
                .map(l -> {
                    lastLogId.set(l.getLogId());
                    return ServerSentEvent.<String>builder()
                            .id(String.valueOf(l.getLogId()))
                            .event("log")
                            .data(l.formatMessage())
                            .build();
                })
                .doOnError(e -> log.warning("Stream error: " + e));
    }
}
