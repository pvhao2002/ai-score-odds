package com.app.kira.dto;

import lombok.Data;

@Data
public class OddTypeDTO {
    private String line;
    private String homeOdds;
    private String awayOdds;
    private String overOdds;
    private String underOdds;
    private String isClear;
}
