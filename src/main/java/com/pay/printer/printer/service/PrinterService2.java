package com.pay.printer.printer.service;

import com.fazecast.jSerialComm.SerialPort;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
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

    // 통신 제어 문자
    private static final byte STX = 0x02;
    private static final byte ETX = 0x03;
    private static final byte ACK = 0x06;
    private static final byte NAK = 0x15;
    private static final byte DLE = 0x10;

    // 명령어
    private static final byte CMD_PRINT = 0x20;
    private static final byte CMD_STATUS = 0x10;

    // 한글 설정 관련
    private static final byte[] SET_KOREAN = {0x1B, 0x74, 0x03};  // 한글 코드페이지
    private static final byte[] SET_KOREAN_ALT = {0x1C, 0x26, 0x1B, 0x74, 0x03};

    public void print(String text) {
        SerialPort serialPort = null;
        try {
            serialPort = openSerialPort();
            if (serialPort == null) {
                throw new RuntimeException("포트를 열 수 없습니다.");
            }

            // 한글 모드 설정 패킷 전송
            byte[] koreanPacket = buildPacket((byte) 0x1C, SET_KOREAN);
            sendPacket(serialPort, koreanPacket);
            checkResponse(serialPort);
            Thread.sleep(100);  // 설정 적용 대기

            // 텍스트를 라인별로 처리
            String[] lines = text.split("\n");
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    // 다양한 인코딩 시도
                    byte[] textBytes;
                    try {
                        textBytes = line.getBytes("KSC5601");  // 첫 번째 시도
                    } catch (Exception e) {
                        try {
                            textBytes = line.getBytes("MS949");  // 두 번째 시도
                        } catch (Exception e2) {
                            textBytes = line.getBytes("EUC-KR");  // 마지막 시도
                        }
                    }

                    // 출력 패킷 구성 및 전송
                    byte[] packet = buildPacket(CMD_PRINT, textBytes);
                    sendPacket(serialPort, packet);

                    if (!checkResponse(serialPort)) {
                        throw new RuntimeException("프린터 응답 오류");
                    }

                    // 줄바꿈 처리
                    byte[] newline = {0x0A};
                    byte[] newlinePacket = buildPacket(CMD_PRINT, newline);
                    sendPacket(serialPort, newlinePacket);
                    checkResponse(serialPort);
                }
                Thread.sleep(50);  // 라인 간 간격
            }

        } catch (Exception e) {
            log.error("프린터 출력 중 오류 발생", e);
            throw new RuntimeException("프린터 출력 실패", e);
        } finally {
            if (serialPort != null && serialPort.isOpen()) {
                serialPort.closePort();
            }
        }
    }

    private byte[] buildPacket(byte command, byte[] data) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            // STX
            bos.write(STX);

            // 길이 (데이터 길이 + 명령어 1바이트)
            int length = data.length + 1;
            bos.write((length >> 8) & 0xFF);
            bos.write(length & 0xFF);

            // 명령어
            bos.write(command);

            // 데이터
            bos.write(data);

            // ETX
            bos.write(ETX);

            // LRC
            byte[] temp = bos.toByteArray();
            byte lrc = calculateLRC(temp, 1, temp.length - 1);
            bos.write(lrc);

            return bos.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("패킷 생성 실패", e);
        }
    }

    // 패킷 전송 후 응답 대기 시간 추가
    private void sendPacket(SerialPort serialPort, byte[] packet) throws Exception {
        logBytes("전송 패킷", packet);

        // 전송 전 입력 버퍼 클리어
        serialPort.getInputStream().skip(serialPort.getInputStream().available());

        // 패킷 전송
        serialPort.writeBytes(packet, packet.length);
        serialPort.getOutputStream().flush();

        // 전송 후 잠시 대기
        Thread.sleep(200);  // 대기 시간 증가
    }
//    private void sendPacket(SerialPort serialPort, byte[] packet) throws Exception {
//        logBytes("전송 패킷", packet);
//
//        // 패킷 전송 전 버퍼 클리어
//        serialPort.getOutputStream().flush();
//
//        // 작은 단위로 나누어 전송
//        int chunkSize = 16;  // 더 작은 단위로 조정
//        for (int i = 0; i < packet.length; i += chunkSize) {
//            int length = Math.min(chunkSize, packet.length - i);
//            byte[] chunk = Arrays.copyOfRange(packet, i, i + length);
//            serialPort.writeBytes(chunk, length);
//            Thread.sleep(30);  // 전송 간격 증가
//        }
//
//        serialPort.getOutputStream().flush();
//    }

    private boolean checkResponse(SerialPort serialPort) throws Exception {
        byte[] buffer = new byte[1];
        long startTime = System.currentTimeMillis();
        int bytesRead;

        // 버퍼 클리어
        serialPort.getInputStream().skip(serialPort.getInputStream().available());

        while (System.currentTimeMillis() - startTime < 2000) {
            bytesRead = serialPort.readBytes(buffer, 1);

            // 디버깅 정보 추가
            log.debug("Bytes read: " + bytesRead);
            log.debug("Buffer value: 0x" + String.format("%02X", buffer[0]));

            if (bytesRead > 0) {
                logBytes("수신 응답", buffer);

                // 응답 값 확인
                switch (buffer[0]) {
                    case ACK:
                        log.debug("ACK 수신됨");
                        return true;
                    case NAK:
                        log.debug("NAK 수신됨");
                        return false;
                    default:
                        log.debug("알 수 없는 응답: 0x" + String.format("%02X", buffer[0]));
                }
            } else {
                log.debug("응답 대기 중...");
            }

            // 대기 시간 조정
            Thread.sleep(100);  // 50ms에서 100ms로 증가
        }

        log.warn("응답 시간 초과");
        return false;
    }

    private SerialPort openSerialPort() {
        SerialPort serialPort = SerialPort.getCommPort(portName);

        serialPort.setBaudRate(9600);  // 속도를 9600으로 변경
        serialPort.setNumDataBits(8);
        serialPort.setNumStopBits(1);
        serialPort.setParity(SerialPort.NO_PARITY);

        // 타임아웃 설정 수정
        serialPort.setComPortTimeouts(
            SerialPort.TIMEOUT_READ_SEMI_BLOCKING,
            2000,  // Read timeout 증가
            2000   // Write timeout 증가
        );

        if (!serialPort.openPort()) {
            log.error("포트를 열 수 없습니다: {}", portName);
            return null;
        }

        return serialPort;
    }

    private void logBytes(String message, byte[] data) {
        if (log.isDebugEnabled()) {
            StringBuilder sb = new StringBuilder(message + ": ");
            for (byte b : data) {
                sb.append(String.format("%02X ", b));
            }
            log.debug(sb.toString());
        }
    }

    private byte calculateLRC(byte[] data, int start, int length) {
        byte lrc = 0;
        for (int i = start; i < start + length; i++) {
            lrc ^= data[i];  // XOR 연산으로 모든 바이트를 누적
        }
        return lrc;
    }
}
