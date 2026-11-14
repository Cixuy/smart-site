package com.qst.smartbuildings.vo;

import lombok.Data;

import java.time.LocalDate;

@Data
public class TrendPoint {
    private LocalDate date;
    private int value;
}
