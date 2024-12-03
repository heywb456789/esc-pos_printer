package com.pay.printer.printer.service;

import com.fazecast.jSerialComm.SerialPort;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PrinterService {
    
    @Value("${printer.port.name}")
    private String portName;  // application.yml에서 설정
    
    // ESC/POS 명령어
    private static final byte[] ESC = {0x1B};
    private static final byte[] GS = {0x1D};

    // 초기화
    private static final byte[] INIT = {0x1B, 0x40};

    // 한글 모드 설정
    private static final byte[] KOREAN_MODE = {0x1C, 0x26}; // 한글 모드 시작
    private static final byte[] ASCII_MODE = {0x1C, 0x2E};  // 한글 모드 해제

    // 줄바꿈
    private static final byte[] NEW_LINE = {0x0A};

    // 용지 컷팅 (전체 컷)
    private static final byte[] PAPER_CUT = {0x1D, 0x56, 0x00};
    // 용지 컷팅 (부분 컷)
    private static final byte[] PAPER_PART_CUT = {0x1D, 0x56, 0x01};
    private static final byte[] TEXT_NORMAL = {0x1B, 0x21, 0x00};  // 기본 크기
    private static final byte[] ALIGN_LEFT = {0x1B, 0x61, 0x00};   // 왼쪽 정렬
    private static final byte[] FEED_AND_CUT = {
        0x1D, 0x56, 0x42, 0x50  // GS V B n - n은 커팅 전 피드할 라인 수
    };

    public void print(String text) {
        SerialPort serialPort = null;
        try {
            serialPort = openSerialPort();
            if (serialPort == null) {
                throw new RuntimeException("프린터 포트를 열 수 없습니다.");
            }

            // 프린터 완전 초기화
            write(serialPort, INIT);
            Thread.sleep(100);  // 초기화 후 잠시 대기

            // 기본 설정
            write(serialPort, ALIGN_LEFT);     // 왼쪽 정렬
            write(serialPort, TEXT_NORMAL);    // 기본 글자 크기
            write(serialPort, KOREAN_MODE);    // 한글 모드

            // 텍스트 출력
            byte[] textBytes = text.getBytes("EUC-KR");
            write(serialPort, textBytes);

            // 충분한 여백 추가 (4~5줄 정도)
            for (int i = 0; i < 3; i++) {
                write(serialPort, NEW_LINE);
            }

            // 버퍼 비우기
            serialPort.flushIOBuffers();

            // 잠시 대기
            Thread.sleep(200);

            // 한글 모드 해제
            write(serialPort, ASCII_MODE);

            // 용지 커팅 전 추가 대기
            Thread.sleep(200);

            // 용지 커팅
//            write(serialPort, PAPER_CUT);
            write(serialPort, FEED_AND_CUT);

            // 최종 버퍼 비우기
            serialPort.flushIOBuffers();
        } catch (Exception e) {
            log.error("프린터 출력 중 오류 발생", e);
            throw new RuntimeException("프린터 출력 실패", e);
        } finally {
            if (serialPort != null && serialPort.isOpen()) {
                serialPort.closePort();
            }
        }
    }
    private void write(SerialPort serialPort, byte[] data) {
        serialPort.writeBytes(data, data.length);
    }
    
    private SerialPort openSerialPort() {
        SerialPort serialPort = SerialPort.getCommPort(portName);
        
        // 시리얼 포트 설정
        serialPort.setBaudRate(9600);
        serialPort.setNumDataBits(8);
        serialPort.setNumStopBits(1);
        serialPort.setParity(SerialPort.NO_PARITY);
        // 타임아웃 설정 수정
        serialPort.setComPortTimeouts(
            SerialPort.TIMEOUT_READ_SEMI_BLOCKING | SerialPort.TIMEOUT_WRITE_BLOCKING,
            2000,  // Read timeout
            2000   // Write timeout
        );
        
        if (!serialPort.openPort()) {
            log.error("포트를 열 수 없습니다: {}", portName);
            return null;
        }
        
        return serialPort;
    }
    
    // 사용 가능한 시리얼 포트 목록 조회
    public String[] getAvailablePorts() {
        return Arrays.stream(SerialPort.getCommPorts())
                .map(SerialPort::getSystemPortName)
                .toArray(String[]::new);
    }
}