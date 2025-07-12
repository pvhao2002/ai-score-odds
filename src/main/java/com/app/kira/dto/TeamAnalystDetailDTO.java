package com.app.kira.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TeamAnalystDetailDTO {
    private Integer minFtScore = 0;
    private Integer maxFtScore = 0;

    private Integer minHtScore = 0;
    private Integer maxHtScore = 0;

    private Integer minCorner = 0;
    private Integer maxCorner = 0;

    private Double avgFtScore = 0.0;
    private Double avgHtScore = 0.0;
    private Double avgCorner = 0.0;
}
