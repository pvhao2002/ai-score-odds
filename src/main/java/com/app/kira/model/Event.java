package com.app.kira.model;

import lombok.*;

import java.sql.ResultSet;
import java.sql.SQLException;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Event {
    private Long eventId;
    private String eventName;
    private String eventDate;
    private String leagueName;
    private String detailLink;

    public Event(ResultSet rs) throws SQLException {
        this.eventId = rs.getLong("event_id");
        this.eventName = rs.getString("event_name");
        this.eventDate = rs.getString("event_date");
        this.leagueName = rs.getString("league_name");
        this.detailLink = rs.getString("detail_link");
    }
}
