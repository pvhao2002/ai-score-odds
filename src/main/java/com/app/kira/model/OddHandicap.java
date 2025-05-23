package com.app.kira.model;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OddHandicap extends BaseOdd {
    private String home;
    private String away;

    public OddHandicap(String oddDate, String home, String away) {
        super(oddDate);
        this.home = home;
        this.away = away;
    }
}
