package com.app.kira.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DashboardDTO {
    private Long totalEvents = 0L;
    private Long todayEvents = 0L;
    private Long upcomingEvents = 0L;
    private Long totalLeagues = 0L;
}
