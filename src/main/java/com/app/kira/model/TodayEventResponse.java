package com.app.kira.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TodayEventResponse {
    private String leagueName;
    private List<EventResult> events;
}
