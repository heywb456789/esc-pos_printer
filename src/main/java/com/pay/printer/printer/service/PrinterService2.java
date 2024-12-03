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

    // 명령어 (예시)
    private static final byte CMD_PRINT = 0x20;  // 출력 명령
    private static final byte CMD_STATUS = 0x10; // 상태 확인

    public void print(String text) {
        SerialPort serialPort = null;
        try {
            serialPort = openSerialPort();
            if (serialPort == null) {
                throw new RuntimeException("포트를 열 수 없습니다.");
            }

            // 텍스트를 바이트 배열로 변환
            byte[] textBytes = text.getBytes("EUC-KR");

            // 패킷 구성
            byte[] packet = buildPacket(CMD_PRINT, textBytes);

            // 패킷 전송 및 응답 대기
            sendPacket(serialPort, packet);

            // 응답 확인
            if (!checkResponse(serialPort)) {
                throw new RuntimeException("프린터 응답 오류");
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
        // 패킷 길이 계산 (STX + LEN + CMD + DATA + ETX + LRC)
        int length = data.length + 1;  // 명령어 1바이트 포함

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(STX);  // 시작 문자

        // 길이 (2바이트)
        bos.write((length >> 8) & 0xFF);
        bos.write(length & 0xFF);

        bos.write(command);  // 명령어
        bos.write(data, 0, data.length);  // 데이터
        bos.write(ETX);  // 종료 문자

        // LRC 계산 및 추가
        byte[] packet = bos.toByteArray();
        byte lrc = calculateLRC(packet, 1, packet.length - 1);
        bos.write(lrc);

        return bos.toByteArray();
    }

    private void sendPacket(SerialPort serialPort, byte[] packet) throws Exception {
        logBytes("Sending packet", packet);  // 디버깅용 로그

        // 패킷을 작은 단위로 나누어 전송
        int chunkSize = 32;
        for (int i = 0; i < packet.length; i += chunkSize) {
            int length = Math.min(chunkSize, packet.length - i);
            byte[] chunk = Arrays.copyOfRange(packet, i, i + length);
            serialPort.writeBytes(chunk, length);
            Thread.sleep(20);  // 전송 간격
        }
    }

    private boolean checkResponse(SerialPort serialPort) throws Exception {
        byte[] buffer = new byte[1];
        long startTime = System.currentTimeMillis();

        // 응답 대기 (최대 3초)
        while (System.currentTimeMillis() - startTime < 3000) {
            if (serialPort.readBytes(buffer, 1) > 0) {
                logBytes("Received response", buffer);  // 디버깅용 로그
                return buffer[0] == ACK;
            }
            Thread.sleep(100);
        }

        return false;
    }

    private byte calculateLRC(byte[] data, int start, int length) {
        byte lrc = 0;
        for (int i = start; i < start + length; i++) {
            lrc ^= data[i];
        }
        return lrc;
    }

    private SerialPort openSerialPort() {
        SerialPort serialPort = SerialPort.getCommPort(portName);

        serialPort.setBaudRate(115200);  // 또는 9600
        serialPort.setNumDataBits(8);
        serialPort.setNumStopBits(1);
        serialPort.setParity(SerialPort.NO_PARITY);

        serialPort.setComPortTimeouts(
            SerialPort.TIMEOUT_READ_SEMI_BLOCKING |
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
