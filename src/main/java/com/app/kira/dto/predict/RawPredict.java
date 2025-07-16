package com.app.kira.dto.predict;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RawPredict {
    private Integer ftTotalGoal;
    private Integer cnt;
    private Double ratio;
}
