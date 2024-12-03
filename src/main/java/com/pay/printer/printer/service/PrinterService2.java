package com.pay.printer.printer.service;

import com.fazecast.jSerialComm.SerialPort;
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


    // 프린터 명령어
    private static final byte[] INIT = {0x1B, 0x40};  // 초기화
    private static final byte[] NEW_LINE = {0x0A};    // 줄바꿈
    private static final byte[] PAPER_CUT = {0x1D, 0x56, 0x41};  // 용지 커팅

    // KS-1420 한글 모드 설정
    private static final byte[] KOREAN_START = {
        0x1B, 0x40,  // 초기화
        0x1B, 0x74, 0x03,  // 코드페이지 설정
        0x1C, 0x26   // 한글 모드 시작
    };
    private static final byte[] KOREAN_END = {0x1C, 0x2E};

    @Value("${printer.port.name}")
    private String portName;  // application.yml에서 설정

    public void print(String text) {
        SerialPort serialPort = null;
        try {
            serialPort = openSerialPort();
            if (serialPort == null) {
                throw new RuntimeException("프린터 포트를 열 수 없습니다.");
            }

            // 프린터 초기화
            write(serialPort, INIT);
            Thread.sleep(100);

            // 한글 모드 시작
            write(serialPort, new byte[]{0x1B, 0x74, 0x03});
            Thread.sleep(50);

            // 텍스트를 청크로 나누어 전송
            byte[] textBytes = text.getBytes("EUC-KR");
            int chunkSize = 256;  // 더 작은 청크 크기 사용

            for (int i = 0; i < textBytes.length; i += chunkSize) {
                int length = Math.min(chunkSize, textBytes.length - i);
                byte[] chunk = Arrays.copyOfRange(textBytes, i, i + length);
                serialPort.writeBytes(chunk, length);
                Thread.sleep(50);  // 각 청크 사이 대기 시간 증가
            }

            // 여백 추가
            for (int i = 0; i < 5; i++) {
                write(serialPort, NEW_LINE);
                Thread.sleep(20);
            }

            // 한글 모드 종료
            write(serialPort, KOREAN_END);
            Thread.sleep(100);

            // 용지 커팅
            write(serialPort, PAPER_CUT);
            Thread.sleep(200);

        } catch (Exception e) {
            log.error("프린터 출력 중 오류 발생", e);
            throw new RuntimeException("프린터 출력 실패", e);
        } finally {
            if (serialPort != null && serialPort.isOpen()) {
                try {
                    serialPort.flushIOBuffers();
                    Thread.sleep(100);
                } catch (Exception e) {
                    log.error("버퍼 비우기 실패", e);
                }
                serialPort.closePort();
            }
        }
    }

    private SerialPort openSerialPort() {
        SerialPort serialPort = SerialPort.getCommPort(portName);

        // 버퍼 크기 증가 및 통신 설정
        serialPort.setBaudRate(115200);
        serialPort.setNumDataBits(8);
        serialPort.setNumStopBits(1);
        serialPort.setParity(SerialPort.NO_PARITY);

        // 타임아웃 설정
        serialPort.setComPortTimeouts(
            SerialPort.TIMEOUT_WRITE_BLOCKING,
            0,
            10000  // 쓰기 타임아웃 증가
        );

        if (!serialPort.openPort()) {
            log.error("포트를 열 수 없습니다: {}", portName);
            return null;
        }

        return serialPort;
    }

    private void write(SerialPort serialPort, byte[] data) {
        // 디버깅을 위한 로그 추가
        if (log.isDebugEnabled()) {
            log.debug("Writing bytes (length=" + data.length + "): " +
                Arrays.toString(data));
        }

        // 데이터를 작은 청크로 나누어 전송
        int chunkSize = 1024;  // 1KB씩 전송
        for (int i = 0; i < data.length; i += chunkSize) {
            int length = Math.min(chunkSize, data.length - i);
            byte[] chunk = Arrays.copyOfRange(data, i, i + length);
            serialPort.writeBytes(chunk, length);

            try {
                Thread.sleep(10);  // 각 청크 사이에 약간의 대기 시간
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

}
