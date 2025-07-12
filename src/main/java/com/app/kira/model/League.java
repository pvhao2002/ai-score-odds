package com.app.kira.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class League {
    private Integer leagueId;
    private String leagueName;
    private Boolean isMain;
}
