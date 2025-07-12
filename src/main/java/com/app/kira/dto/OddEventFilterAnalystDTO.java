package com.app.kira.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
public class OddEventFilterAnalystDTO {
    String oddType;
    String line;
    String homeLine;
    String awayLine;
    Double odd1;
    Double odd2;
}
