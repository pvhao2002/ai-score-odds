package com.app.kira.model.analyst;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CrawlDate {
    private Integer id;
    private String date;
    private String status;
    private Timestamp createdAt;
}
