package com.app.kira.model;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Odd1x2 extends BaseOdd {
    private String _1;
    private String x;
    private String _2;

    public Odd1x2(String oddDate, String _1, String x, String _2) {
        super(oddDate);
        this._1 = _1;
        this.x = x;
        this._2 = _2;
    }
}
