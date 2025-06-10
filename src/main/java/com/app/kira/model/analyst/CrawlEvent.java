package com.app.kira.model.analyst;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CrawlEvent {
    private Integer id;
    private String eventName;
    private String eventDate;
    private String detailLink;
    private String status;
    private Timestamp createdAt;
}
