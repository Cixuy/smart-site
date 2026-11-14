package com.qst.smartbuildings.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Receives newline-delimited sensor JSON over TCP and forwards it to TCP frontend clients. */
@Component
public class SensorTcpServer {
    private static final Logger log = LoggerFactory.getLogger(SensorTcpServer.class);

    private final ObjectMapper objectMapper;
    private final int configuredPort;
    private final int cacheCapacity;
    private final SensorDataPersistenceService persistenceService;
    private final Object stateLock = new Object();
    private final Deque<String> cache = new ArrayDeque<>();
    private final Map<Long, String> latestSensorData = new LinkedHashMap<>();
    private final Set<ClientConnection> frontendClients = new HashSet<>();
    private final Set<Socket> activeConnections = new HashSet<>();
    private final AtomicInteger threadNumber = new AtomicInteger();
    private final ExecutorService executor = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "sensor-tcp-" + threadNumber.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });

    private volatile boolean running;
    private ServerSocket serverSocket;

    public SensorTcpServer(ObjectMapper objectMapper,
                           @Value("${sensor.tcp.port:9090}") int configuredPort,
                           @Value("${sensor.tcp.cache-capacity:100}") int cacheCapacity,
                           SensorDataPersistenceService persistenceService) {
        this.objectMapper = objectMapper;
        this.configuredPort = configuredPort;
        this.cacheCapacity = Math.max(1, cacheCapacity);
        this.persistenceService = persistenceService;
    }

    @PostConstruct
    public void start() throws IOException {
        serverSocket = new ServerSocket(configuredPort);
        running = true;
        executor.execute(this::acceptConnections);
        log.info("Sensor TCP server listening on port {}, cache capacity {}", getPort(), cacheCapacity);
    }

    @PreDestroy
    public void stop() {
        running = false;
        closeQuietly(serverSocket);
        synchronized (stateLock) {
            activeConnections.forEach(SensorTcpServer::closeQuietly);
            activeConnections.clear();
            frontendClients.clear();
            cache.clear();
            latestSensorData.clear();
        }
        executor.shutdownNow();
    }

    public int getPort() {
        return serverSocket == null ? configuredPort : serverSocket.getLocalPort();
    }

    public Optional<String> getLatestSensorData(Long sensorId) {
        if (sensorId == null) {
            return Optional.empty();
        }
        synchronized (stateLock) {
            return Optional.ofNullable(latestSensorData.get(sensorId));
        }
    }

    public Map<Long, String> getLatestSensorDataSnapshot() {
        synchronized (stateLock) {
            return Map.copyOf(latestSensorData);
        }
    }

    public boolean hasLatestSensorData(Long sensorId) {
        if (sensorId == null) {
            return false;
        }
        synchronized (stateLock) {
            return latestSensorData.containsKey(sensorId);
        }
    }

    private void acceptConnections() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                socket.setKeepAlive(true);
                synchronized (stateLock) {
                    activeConnections.add(socket);
                }
                executor.execute(() -> handleConnection(socket));
            } catch (IOException ex) {
                if (running) {
                    log.warn("Failed to accept sensor TCP connection", ex);
                }
            }
        }
    }

    private void handleConnection(Socket socket) {
        Set<Long> contributedKeys = new HashSet<>();
        Set<String> contributedLines = new HashSet<>();
        try (socket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            String handshake = reader.readLine();
            String role = readRole(handshake);
            if ("sensor".equals(role)) {
                receiveSensorData(reader, writer, contributedKeys, contributedLines);
            } else if ("frontend".equals(role)) {
                serveFrontend(socket, reader, writer);
            } else {
                writeLine(writer, "{\"type\":\"error\",\"message\":\"role must be sensor or frontend\"}");
            }
        } catch (IOException ex) {
            log.debug("Sensor TCP client disconnected: {}", ex.getMessage());
        } finally {
            synchronized (stateLock) {
                activeConnections.remove(socket);
                // 该连接断开后不再有数据源，清掉它上报过的数据，避免持续推送最后一条旧数据
                contributedKeys.forEach(latestSensorData::remove);
                cache.removeIf(contributedLines::contains);
            }
        }
    }

    private String readRole(String handshake) throws IOException {
        if (handshake == null) {
            return null;
        }
        JsonNode node = objectMapper.readTree(handshake);
        return node.path("role").asText(null);
    }

    private void receiveSensorData(BufferedReader reader, BufferedWriter writer,
                                   Set<Long> contributedKeys, Set<String> contributedLines) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            JsonNode node = validateSensorData(line);
            Long key = extractSensorKey(node);
            cacheLatestAndBroadcast(line, key);
            contributedKeys.add(key);
            contributedLines.add(line);
            persistReading(key, node);
            writeLine(writer, "{\"type\":\"ack\"}");
        }
    }

    /** 将读数持久化到对应实时监测数据表；失败仅记日志，不影响 TCP 缓存与转发。 */
    private void persistReading(Long sensorId, JsonNode node) {
        if (persistenceService == null) {
            return;
        }
        try {
            persistenceService.persist(sensorId, node.path("metricType").asText(),
                    node.path("metricValue").decimalValue(),
                    node.path("unit").asText(null),
                    node.path("collectTime").asText());
        } catch (Exception ex) {
            log.warn("持久化传感器 {} 读数失败: {}", sensorId, ex.getMessage());
        }
    }

    private JsonNode validateSensorData(String line) throws IOException {
        JsonNode node = objectMapper.readTree(line);
        if (!node.isObject() || !node.hasNonNull("monitorPointId") || !node.hasNonNull("metricType")
                || !node.hasNonNull("metricValue") || !node.hasNonNull("collectTime")) {
            throw new IOException("Invalid sensor data JSON");
        }
        return node;
    }

    private Long extractSensorKey(JsonNode node) {
        JsonNode sensorId = node.get("sensorId");
        if (sensorId != null && sensorId.canConvertToLong()) {
            return sensorId.asLong();
        }
        return node.path("monitorPointId").asLong();
    }

    private void cacheLatestAndBroadcast(String line, Long sensorId) {
        synchronized (stateLock) {
            cache.addLast(line);
            latestSensorData.put(sensorId, line);
            while (cache.size() > cacheCapacity) {
                cache.removeFirst();
            }
            List<ClientConnection> failed = new ArrayList<>();
            for (ClientConnection client : frontendClients) {
                try {
                    client.write(line);
                } catch (IOException ex) {
                    failed.add(client);
                }
            }
            failed.forEach(client -> {
                frontendClients.remove(client);
                client.close();
            });
        }
    }

    private void serveFrontend(Socket socket, BufferedReader reader, BufferedWriter writer) throws IOException {
        ClientConnection client = new ClientConnection(socket, writer);
        synchronized (stateLock) {
            for (String cached : cache) {
                client.write(cached);
            }
            frontendClients.add(client);
        }
        try {
            while (reader.readLine() != null) {
                // The frontend channel is server-push only; input is consumed to detect disconnects.
            }
        } finally {
            synchronized (stateLock) {
                frontendClients.remove(client);
            }
        }
    }

    private static void writeLine(BufferedWriter writer, String line) throws IOException {
        writer.write(line);
        writer.newLine();
        writer.flush();
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }

    private record ClientConnection(Socket socket, BufferedWriter writer) {
        void write(String line) throws IOException {
            writeLine(writer, line);
        }

        void close() {
            closeQuietly(socket);
        }
    }
}
