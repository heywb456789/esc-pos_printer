package com.pay.printer.printer.service;

import com.fazecast.jSerialComm.SerialPort;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @author : MinjaeKim
 * @packageName : com.pay.printer.printer.service
 * @fileName : PrinterService3
 * @date : 2024-12-03
 * @description : ===========================================================
 * @DATE @AUTHOR       @NOTE ----------------------------------------------------------- 2024-12-03
 * MinjaeKim       최초 생성
 */
@Service
@Slf4j
public class PrinterService3 {

    // ESC/POS 명령어
    private static final byte[] ESC_INIT = {0x1B, 0x40};         // 프린터 초기화
    private static final byte[] LF = {0x0A};                     // 라인피드
    private static final byte[] LINE_FEED = {0x0A};                     // 라인피드
    private static final byte[] ESC_CUT = {0x1D, 0x56, 0x41};   // 용지 커팅
    private static final byte[] PARTIAL_CUT = {0x1D, 0x56, 0x01};    // 부분 커팅

    // 시리얼 통신용 패킷 명령어
    private static final byte STX = 0x02;
    private static final byte ETX = 0x03;
    private static final byte CMD_PRINT = 0x20;
    private static final String portName = "COM4";

    // 한글 설정
    private static final byte[] SET_KOREAN = {
        0x1B, 0x40,       // 초기화
        0x1B, 0x74, 0x03  // 한글 코드페이지
    };

    // 영어 설정
    private static final byte[] SET_ENGLISH = {
        0x1B, 0x74, 0x00  // 영어 코드페이지
    };

    // 9600 통신으로 ESC/POS 명령어 전송
    public void testWithESCPOS() {
        SerialPort serialPort = null;
        try {
            serialPort = openSerialPort(9600);
            if (serialPort == null) {
                throw new RuntimeException("포트를 열 수 없습니다.");
            }

            System.out.println("ESC/POS 명령어로 테스트 시작");

            // 초기화
            sendRawCommand(serialPort, ESC_INIT);
            Thread.sleep(100);

            // 영어 출력
            write(serialPort, SET_ENGLISH);
            Thread.sleep(50);

            String[] englishSamples = {
                "=================",
                "   TEST PRINT    ",
                "=================",
                "Hello World!     ",
                "ABCDEFGHIJKLMNOP",
                "1234567890      "
            };

            for (String text : englishSamples) {
                write(serialPort, text.getBytes());
                write(serialPort, LINE_FEED);
                Thread.sleep(50);
            }

            // 빈 줄
            write(serialPort, LINE_FEED);
            write(serialPort, LINE_FEED);

            // 한글 출력
            write(serialPort, SET_KOREAN);
            Thread.sleep(50);

            String[] koreanSamples = {
                "=================",
                "    테스트 출력    ",
                "=================",
                "안녕하세요!      ",
                "한글 출력 테스트  ",
                "영수증 출력      "
            };

            for (String text : koreanSamples) {
                try {
                    write(serialPort, text.getBytes("EUC-KR"));
                    write(serialPort, LINE_FEED);
                    Thread.sleep(50);
                } catch (Exception e) {
                    System.out.println("한글 인코딩 실패: " + e.getMessage());
                }
            }

            // 5줄 띄우기
            for (int i = 0; i < 5; i++) {
                sendRawCommand(serialPort, LF);
                Thread.sleep(50);
            }

            // 용지 커팅
//            sendRawCommand(serialPort, ESC_CUT);
            sendRawCommand(serialPort, PARTIAL_CUT);

        } catch (Exception e) {
            System.out.println("프린터 테스트 중 오류 발생: " + e.getMessage());
        } finally {
            if (serialPort != null && serialPort.isOpen()) {
                serialPort.closePort();
            }
        }
    }

    private void write(SerialPort serialPort, byte[] data) {
        try {
            System.out.print("전송 데이터: ");
            for (byte b : data) {
                System.out.printf("%02X ", b);
            }
            System.out.println();

            serialPort.getOutputStream().write(data);
            serialPort.getOutputStream().flush();

        } catch (Exception e) {
            System.out.println("데이터 전송 실패: " + e.getMessage());
        }
    }

    // 115200 통신으로 패킷 방식 전송
    public void testWithPacket() {
        SerialPort serialPort = null;
        try {
            serialPort = openSerialPort(115200);
            if (serialPort == null) {
                throw new RuntimeException("포트를 열 수 없습니다.");
            }

            System.out.println("패킷 방식으로 테스트 시작");

            // 5줄 띄우기
            for (int i = 0; i < 5; i++) {
                byte[] packet = buildPacket(CMD_PRINT, LF);
                sendPacket(serialPort, packet);
                Thread.sleep(50);
            }

            // 용지 커팅
            byte[] cutPacket = buildPacket(CMD_PRINT, ESC_CUT);
            sendPacket(serialPort, cutPacket);

        } catch (Exception e) {
            System.out.println("프린터 테스트 중 오류 발생: " + e.getMessage());
        } finally {
            if (serialPort != null && serialPort.isOpen()) {
                serialPort.closePort();
            }
        }
    }

    private SerialPort openSerialPort(int baudRate) {
        SerialPort serialPort = SerialPort.getCommPort(portName);

        serialPort.setBaudRate(baudRate);
        serialPort.setNumDataBits(8);
        serialPort.setNumStopBits(1);
        serialPort.setParity(SerialPort.NO_PARITY);

        serialPort.setComPortTimeouts(
            SerialPort.TIMEOUT_WRITE_BLOCKING,
            0,
            1000
        );

        if (!serialPort.openPort()) {
            System.out.println("포트를 열 수 없습니다: " + portName);
            return null;
        }

        return serialPort;
    }

    // ESC/POS 명령어 직접 전송
    private void sendRawCommand(SerialPort serialPort, byte[] command) {
        try {
            printBytes("전송 명령어", command);
            serialPort.writeBytes(command, command.length);
            serialPort.getOutputStream().flush();
            Thread.sleep(50);
        } catch (Exception e) {
            System.out.println("명령어 전송 실패: " + e.getMessage());
        }
    }

    // 패킷 구성
    private byte[] buildPacket(byte command, byte[] data) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            bos.write(STX);
            int length = data.length + 1;
            bos.write((length >> 8) & 0xFF);
            bos.write(length & 0xFF);
            bos.write(command);
            bos.write(data);
            bos.write(ETX);

            byte[] temp = bos.toByteArray();
            byte lrc = calculateLRC(temp, 1, temp.length - 1);
            bos.write(lrc);

            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("패킷 생성 실패", e);
        }
    }

    // 패킷 전송
    private void sendPacket(SerialPort serialPort, byte[] packet) {
        try {
            printBytes("전송 패킷", packet);
            serialPort.writeBytes(packet, packet.length);
            serialPort.getOutputStream().flush();
            Thread.sleep(50);
        } catch (Exception e) {
            System.out.println("패킷 전송 실패: " + e.getMessage());
        }
    }

    private byte calculateLRC(byte[] data, int start, int length) {
        byte lrc = 0;
        for (int i = start; i < start + length; i++) {
            lrc ^= data[i];
        }
        return lrc;
    }

    private void printBytes(String message, byte[] data) {
        StringBuilder sb = new StringBuilder(message + ": ");
        for (byte b : data) {
            sb.append(String.format("%02X ", b));
        }
        System.out.println(sb.toString());
    }
}


