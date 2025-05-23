package com.app.kira.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EventDTO {
    private Long eventId;
    private String eventName;
    private String leagueName;
    private Timestamp eventDate;
    private String oddType;
    private String oddValue;

    public EventDTO(ResultSet rs) throws SQLException {
        this.eventId = rs.getLong("event_id");
        this.eventName = rs.getString("event_name");
        this.eventDate = rs.getTimestamp("event_date");
        this.leagueName = rs.getString("league_name");
        this.oddType = rs.getString("odd_type");
        this.oddValue = rs.getString("odd_value");
    }
}
