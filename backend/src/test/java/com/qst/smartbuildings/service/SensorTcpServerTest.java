package com.qst.smartbuildings.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensorTcpServerTest {
    private SensorTcpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void replaysCachedDataThenPushesNewDataToFrontend() throws Exception {
        server = new SensorTcpServer(new ObjectMapper(), 0, 2, null);
        server.start();

        try (Socket sensor = connect("sensor")) {
            BufferedWriter sensorWriter = writer(sensor);
            BufferedReader sensorReader = reader(sensor);
            sendAndAwaitAck(sensorWriter, sensorReader, payload(1));
            sendAndAwaitAck(sensorWriter, sensorReader, payload(2));
            sendAndAwaitAck(sensorWriter, sensorReader, payload(3));

            try (Socket frontend = connect("frontend")) {
                frontend.setSoTimeout(3000);
                BufferedReader frontendReader = reader(frontend);
                assertEquals(payload(2), frontendReader.readLine());
                assertEquals(payload(3), frontendReader.readLine());

                sendAndAwaitAck(sensorWriter, sensorReader, payload(4));
                assertEquals(payload(4), frontendReader.readLine());
                assertEquals(payload(4), server.getLatestSensorData(2L).orElseThrow());
                assertTrue(server.hasLatestSensorData(2L));
            }
        }
    }

    @Test
    void storesLatestDataBySensorId() throws Exception {
        server = new SensorTcpServer(new ObjectMapper(), 0, 10, null);
        server.start();

        try (Socket sensor = connect("sensor")) {
            BufferedWriter sensorWriter = writer(sensor);
            BufferedReader sensorReader = reader(sensor);
            sendAndAwaitAck(sensorWriter, sensorReader, payload(2, 1));
            sendAndAwaitAck(sensorWriter, sensorReader, payload(3, 10));
            sendAndAwaitAck(sensorWriter, sensorReader, payload(2, 4));

            assertEquals(payload(2, 4), server.getLatestSensorData(2L).orElseThrow());
            assertEquals(payload(3, 10), server.getLatestSensorData(3L).orElseThrow());

            Map<Long, String> snapshot = server.getLatestSensorDataSnapshot();
            assertEquals(2, snapshot.size());
            assertEquals(payload(2, 4), snapshot.get(2L));
            assertEquals(payload(3, 10), snapshot.get(3L));
        }
    }

    @Test
    void clearsLatestDataWhenSensorConnectionCloses() throws Exception {
        server = new SensorTcpServer(new ObjectMapper(), 0, 10, null);
        server.start();

        Socket sensor = connect("sensor");
        sendAndAwaitAck(writer(sensor), reader(sensor), payload(2, 1));
        sendAndAwaitAck(writer(sensor), reader(sensor), payload(3, 10));
        assertTrue(server.hasLatestSensorData(2L));
        assertTrue(server.hasLatestSensorData(3L));

        sensor.close();

        // 等待服务端处理连接关闭（异步清除缓存）
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline
                && (server.hasLatestSensorData(2L) || server.hasLatestSensorData(3L))) {
            Thread.sleep(50);
        }
        assertTrue(server.getLatestSensorDataSnapshot().isEmpty());
    }

    private Socket connect(String role) throws Exception {
        Socket socket = new Socket("127.0.0.1", server.getPort());
        send(writer(socket), "{\"role\":\"" + role + "\"}");
        return socket;
    }

    private static BufferedReader reader(Socket socket) throws Exception {
        return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    }

    private static BufferedWriter writer(Socket socket) throws Exception {
        return new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    private static void send(BufferedWriter writer, String line) throws Exception {
        writer.write(line);
        writer.newLine();
        writer.flush();
    }

    private static void sendAndAwaitAck(BufferedWriter writer, BufferedReader reader, String line) throws Exception {
        send(writer, line);
        assertEquals("{\"type\":\"ack\"}", reader.readLine());
    }

    private static String payload(int value) {
        return payload(2, value);
    }

    private static String payload(int sensorId, int value) {
        return "{\"monitorPointId\":" + sensorId + ",\"metricType\":\"PM25\",\"metricValue\":" + value
                + ",\"unit\":\"ug/m3\",\"collectTime\":\"2026-08-13 12:00:00.123\"}";
    }
}
