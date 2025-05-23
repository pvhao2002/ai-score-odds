package com.app.kira.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class OddCorner extends BaseOdd {
    private String corner;
    private String over;
    private String under;

    public OddCorner(String oddDate, String corner, String over, String under) {
        super(oddDate);
        this.corner = corner;
        this.over = over;
        this.under = under;
    }
}
