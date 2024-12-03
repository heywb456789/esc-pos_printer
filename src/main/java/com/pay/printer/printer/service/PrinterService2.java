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
        try {
            serialPort = openSerialPort();
            if (serialPort == null) {
                throw new RuntimeException("프린터 포트를 열 수 없습니다.");
            }

            // 프린터 상태 확인
            if (!isPrinterReady(serialPort)) {
                throw new RuntimeException("프린터가 준비되지 않았습니다.");
            }

            // 초기화 및 설정
            sendCommand(serialPort, INIT);
            sendCommand(serialPort, SET_KOREAN);

            // 텍스트 전송 (라인 단위로 처리)
            String[] lines = text.split("\n");
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    byte[] lineBytes = line.getBytes("EUC-KR");
                    sendData(serialPort, lineBytes);
                }
                sendCommand(serialPort, NEW_LINE);
            }

            // 용지 커팅
            sendCommand(serialPort, PAPER_CUT);

        } catch (Exception e) {
            log.error("프린터 출력 중 오류 발생", e);
            throw new RuntimeException("프린터 출력 실패", e);
        } finally {
            closePort(serialPort);
        }
    }

    private SerialPort openSerialPort() {
        SerialPort serialPort = SerialPort.getCommPort(portName);

        // 시리얼 포트 설정
        serialPort.setBaudRate(115200);
        serialPort.setNumDataBits(8);
        serialPort.setNumStopBits(1);
        serialPort.setParity(SerialPort.NO_PARITY);

        // RTS/CTS 흐름 제어
        serialPort.setFlowControl(SerialPort.FLOW_CONTROL_RTS_ENABLED |
                                SerialPort.FLOW_CONTROL_CTS_ENABLED);

        serialPort.setComPortTimeouts(
            SerialPort.TIMEOUT_WRITE_BLOCKING,
            1000,
            1000
        );

        if (!serialPort.openPort()) {
            log.error("포트를 열 수 없습니다: {}", portName);
            return null;
        }

        return serialPort;
    }

    private boolean isPrinterReady(SerialPort serialPort) {
        try {
            // 프린터 상태 요청
            byte[] statusRequest = {0x10, 0x04, 0x01};
            serialPort.writeBytes(statusRequest, statusRequest.length);

            // 응답 대기
            Thread.sleep(100);

            byte[] buffer = new byte[1];
            int bytesRead = serialPort.readBytes(buffer, 1);

            return bytesRead > 0 && (buffer[0] & 0x12) == 0x12;
        } catch (Exception e) {
            log.warn("프린터 상태 확인 실패", e);
            return true; // 상태 확인 실패시 계속 진행
        }
    }

    private void sendCommand(SerialPort serialPort, byte[] command) throws Exception {
        logBytes("Sending command", command);  // 명령어 전송 전 로그
        serialPort.writeBytes(command, command.length);
        Thread.sleep(50); // 명령어 처리 대기
    }

    private void sendData(SerialPort serialPort, byte[] data) throws Exception {
        int chunkSize = 32; // 작은 단위로 전송

        for (int i = 0; i < data.length; i += chunkSize) {
            int length = Math.min(chunkSize, data.length - i);
            byte[] chunk = Arrays.copyOfRange(data, i, i + length);

            logBytes("Sending data chunk", chunk);  // 데이터 청크 전송 전 로그
            serialPort.writeBytes(chunk, length);
            Thread.sleep(10); // 데이터 전송 간격
        }
    }

    private void closePort(SerialPort serialPort) {
        if (serialPort != null && serialPort.isOpen()) {
            try {
                Thread.sleep(100); // 포트 닫기 전 대기
                serialPort.closePort();
            } catch (Exception e) {
                log.error("포트 닫기 실패", e);
            }
        }
    }

    // 디버깅용 메소드
    private void logBytes(String message, byte[] data) {
        if (log.isDebugEnabled()) {
            StringBuilder sb = new StringBuilder(message + ": ");
            for (byte b : data) {
                sb.append(String.format("%02X ", b));
            }
            log.debug(sb.toString());
        }
    }
}
