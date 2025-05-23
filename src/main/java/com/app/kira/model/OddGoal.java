package com.app.kira.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class OddGoal extends BaseOdd {
    private String goals;
    private String over;
    private String under;

    public OddGoal(String oddDate, String goals, String over, String under) {
        super(oddDate);
        this.goals = goals;
        this.over = over;
        this.under = under;
    }
}
