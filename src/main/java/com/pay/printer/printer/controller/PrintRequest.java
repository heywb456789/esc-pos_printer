package com.pay.printer.printer.controller;

import lombok.Data;

// DTO 클래스
@Data  // lombok 사용
public class PrintRequest {
    private String text;
}
