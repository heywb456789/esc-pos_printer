package com.pay.printer.printer.controller;

import com.pay.printer.printer.service.PrinterService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author : MinjaeKim
 * @packageName : com.pay.printer.printer.controller
 * @fileName : PrinterController
 * @date : 2024-12-03
 * @description : ===========================================================
 * @DATE @AUTHOR       @NOTE ----------------------------------------------------------- 2024-12-03
 * MinjaeKim       최초 생성
 */
@RestController
@RequestMapping("/api/printer")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")  // CORS 설정 추가
public class PrinterController {

    private final PrinterService printerService;

    @PostMapping("/print")
    public String print(@RequestBody PrintRequest request) {
        try {
            printerService.print(request.getText());
            return "인쇄 성공";
        } catch (Exception e) {
            return "인쇄 실패: " + e.getMessage();
        }
    }

    @GetMapping("/ports")
    public String[] getAvailablePorts() {
        return printerService.getAvailablePorts();
    }
}

