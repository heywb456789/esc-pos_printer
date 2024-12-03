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

    public void testPrinter() {
        SerialPort serialPort = null;
        try {
            serialPort = openSerialPort();
            if (serialPort == null) {
                throw new RuntimeException("포트를 열 수 없습니다.");
            }

            // 줄바꿈 6번 실행
            byte[] newLine = {0x0A};
            for (int i = 0; i < 6; i++) {
                byte[] packet = buildPacket(CMD_PRINT, newLine);
                sendPacket(serialPort, packet);
                Thread.sleep(100);  // 각 줄바꿈 사이 대기
            }

            // 용지 커팅
            byte[] cutCommand = {0x1D, 0x56, 0x41};
            byte[] cutPacket = buildPacket(CMD_PRINT, cutCommand);
            sendPacket(serialPort, cutPacket);

        } catch (Exception e) {
            log.error("프린터 테스트 중 오류 발생", e);
            throw new RuntimeException("프린터 테스트 실패", e);
        } finally {
            if (serialPort != null && serialPort.isOpen()) {
                serialPort.closePort();
            }
        }
    }

    private byte[] buildPacket(byte command, byte[] data) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            bos.write(STX);

            // 길이 (데이터 길이 + 명령어 1바이트)
            int length = data.length + 1;
            bos.write((length >> 8) & 0xFF);
            bos.write(length & 0xFF);

            bos.write(command);
            bos.write(data);
            bos.write(ETX);

            // LRC 계산 및 추가
            byte[] temp = bos.toByteArray();
            byte lrc = calculateLRC(temp, 1, temp.length - 1);
            bos.write(lrc);

            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("패킷 생성 실패", e);
        }
    }

    private void sendPacket(SerialPort serialPort, byte[] packet) throws Exception {
        logBytes("전송 패킷", packet);
        serialPort.writeBytes(packet, packet.length);
        serialPort.getOutputStream().flush();
        Thread.sleep(50);  // 패킷 전송 후 대기
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

        // 115200 통신 설정
        serialPort.setBaudRate(115200);
        serialPort.setNumDataBits(8);
        serialPort.setNumStopBits(1);
        serialPort.setParity(SerialPort.NO_PARITY);

        // 타임아웃 설정
        serialPort.setComPortTimeouts(
            SerialPort.TIMEOUT_WRITE_BLOCKING,
            0,
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
