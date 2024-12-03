package com.pay.printer.printer.service;

import com.fazecast.jSerialComm.SerialPort;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * @author : MinjaeKim
 * @packageName : com.pay.printer.printer.service
 * @fileName : PrinterService2
 * @date : 2024-12-03
 * @description : ===========================================================
 * @DATE @AUTHOR       @NOTE ----------------------------------------------------------- 2024-12-03
 * MinjaeKim       최초 생성
 */
@Slf4j
@Service
public class PrinterService2 {

    @Value("${printer.port.name}")
    private String portName;  // application.yml에서 설정

    // 프린터 명령어 정의
    private static final byte[] INIT = {0x1B, 0x40};  // 프린터 초기화
    private static final byte[] NEW_LINE = {0x0A};    // 줄바꿈
    private static final byte[] PAPER_CUT = {0x1D, 0x56, 0x41};  // 용지 자르기

    // 프린터 설정 명령어
    private static final byte[] SET_KOREAN = {
        0x1B, 0x40,       // 초기화
        0x1B, 0x52, 0x0D, // 국제 문자 설정 (한국)
        0x1B, 0x74, 0x03, // 문자 코드표 설정 (한국)
        0x1C, 0x71, 0x03  // 한글 코드 설정
    };

    // 프린터 텍스트 정렬 및 크기 설정
    private static final byte[] ALIGN_LEFT = {0x1B, 0x61, 0x00};    // 왼쪽 정렬
    private static final byte[] CHAR_SIZE_NORMAL = {0x1D, 0x21, 0x00};  // 기본 크기

    public void print(String text) {
        SerialPort serialPort = null;
        BufferedOutputStream bos = null;

        try {
            serialPort = openSerialPort();
            if (serialPort == null) {
                throw new RuntimeException("프린터 포트를 열 수 없습니다.");
            }

            bos = new BufferedOutputStream(serialPort.getOutputStream());

            // 초기 설정
            writeToStream(bos, INIT);
            Thread.sleep(100);

            // 한글 설정
            writeToStream(bos, SET_KOREAN);
            Thread.sleep(100);

            // 정렬 및 크기 설정
            writeToStream(bos, ALIGN_LEFT);
            writeToStream(bos, CHAR_SIZE_NORMAL);

            // 텍스트 출력 (라인별로 처리)
            String[] lines = text.split("\n");
            for (String line : lines) {
                // 빈 줄 처리
                if (line.trim().isEmpty()) {
                    writeToStream(bos, NEW_LINE);
                    continue;
                }

                // 일반 텍스트 처리
                byte[] lineBytes = line.getBytes("EUC-KR");
                writeToStream(bos, lineBytes);
                writeToStream(bos, NEW_LINE);
                bos.flush();
                Thread.sleep(30);
            }

            // 여백 추가
            for (int i = 0; i < 5; i++) {
                writeToStream(bos, NEW_LINE);
            }
            bos.flush();
            Thread.sleep(200);

            // 용지 커팅
            writeToStream(bos, PAPER_CUT);
            bos.flush();

        } catch (Exception e) {
            log.error("프린터 출력 중 오류 발생", e);
            throw new RuntimeException("프린터 출력 실패", e);
        } finally {
            // 리소스 정리
            try {
                if (bos != null) {
                    bos.close();
                }
                if (serialPort != null && serialPort.isOpen()) {
                    serialPort.closePort();
                }
            } catch (Exception e) {
                log.error("리소스 정리 중 오류 발생", e);
            }
        }
    }

    private void writeToStream(BufferedOutputStream bos, byte[] data) throws IOException {
        bos.write(data);
    }

    private SerialPort openSerialPort() {
        SerialPort serialPort = SerialPort.getCommPort(portName);

        serialPort.setBaudRate(115200);
        serialPort.setNumDataBits(8);
        serialPort.setNumStopBits(1);
        serialPort.setParity(SerialPort.NO_PARITY);
        // XON/XOFF 흐름 제어 설정
        serialPort.setFlowControl(SerialPort.FLOW_CONTROL_XONXOFF_IN_ENABLED |
            SerialPort.FLOW_CONTROL_XONXOFF_OUT_ENABLED);

        serialPort.setComPortTimeouts(
            SerialPort.TIMEOUT_WRITE_BLOCKING,
            0,
            2000
        );

        if (!serialPort.openPort()) {
            log.error("포트를 열 수 없습니다: {}", portName);
            return null;
        }

        return serialPort;
    }

    // 디버깅을 위한 메소드
    private void logBytes(String prefix, byte[] data) {
        if (log.isDebugEnabled()) {
            StringBuilder sb = new StringBuilder(prefix);
            for (byte b : data) {
                sb.append(String.format("%02X ", b));
            }
            log.debug(sb.toString());
        }
    }
}
