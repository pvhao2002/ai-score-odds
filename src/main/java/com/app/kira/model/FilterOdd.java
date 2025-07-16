package com.app.kira.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FilterOdd {
    boolean isCompareOdd; // just compare line, do not compare odds
    String type; // e.g: ou, hdc, corner
    String line;
    Double odd1; // over or home odds
    Double odd2; // under or away odds
    String firstLine;
    String lastLine;
}
