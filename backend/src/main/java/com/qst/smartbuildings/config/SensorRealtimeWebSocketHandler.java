package com.qst.smartbuildings.config;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qst.smartbuildings.common.AuthConst;
import com.qst.smartbuildings.domain.AssetDeviceSensor;
import com.qst.smartbuildings.mapper.AssetDeviceSensorMapper;
import com.qst.smartbuildings.service.SensorTcpServer;
import com.qst.smartbuildings.util.RedisClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 传感器实时推流 WebSocket 接口（ws://localhost:8080/api/ws/sensor/subscribe）。
 * <p>
 * 订阅、取消订阅、心跳保活三个操作共享同一个连接，仅通过消息中的 {@code type} 区分行为
 * （文档中的 /ws/sensor/unsubscribe 与 /ws/sensor/ping 仅为提示，不实现为独立接口）：
 * <ol>
 *   <li>客户端以 {@code ?token=xxx} 建立连接（token 为空时拒绝）；</li>
 *   <li>{@code type=subscribe}（或省略 type）订阅传感器，消息体如 {@code {"type":"subscribe","data":{"sensor_ids":[1,2,3]}}}、
 *       顶层 {@code {"sensor_ids":[...]}} 或裸 JSON 数组 {@code [1,2,3]}，返回 {@code subscribed} 后持续推送 {@code sensor_data}；</li>
 *   <li>{@code type=unsubscribe} 取消订阅（消息体 {@code {"type":"unsubscribe","data":{"sensor_ids":[1]}}}），返回 {@code unsubscribed}；</li>
 *   <li>{@code type=ping} 心跳保活，返回 {@code {"type":"pong","data":{"timestamp":...}}}；</li>
 *   <li>传感器ID不存在时返回 {@code error}（code=4001）。</li>
 * </ol>
 * <p>
 * 推流数据来源为 mock_sensor_report.py 脚本通过 TCP 上报的数据（由 SensorTcpServer 接收并缓存），
 * 不再由 Spring Boot 自行模拟生成。
 */
@Component
public class SensorRealtimeWebSocketHandler extends TextWebSocketHandler {

    private static final String ATTR_SENSORS = "sensor.subscribed";
    private static final String ATTR_EXECUTOR = "sensor.executor";

    /** 订阅请求中允许的最大传感器数量 */
    private static final int MAX_SUBSCRIBE_IDS = 200;
    /** 定时器扫描周期（毫秒），用于按 refresh_rate 分发推流 */
    private static final long TICK_MILLIS = 250;
    /** 传感器 refresh_rate 为空/过小时的兜底刷新周期（毫秒） */
    private static final long DEFAULT_REFRESH_MS = 5000;
    private static final long MIN_REFRESH_MS = 1000;

    private final AssetDeviceSensorMapper sensorMapper;
    private final RedisClient redisClient;
    private final SensorTcpServer sensorTcpServer;
    private final boolean authEnabled;

    public SensorRealtimeWebSocketHandler(AssetDeviceSensorMapper sensorMapper,
                                          RedisClient redisClient,
                                          SensorTcpServer sensorTcpServer,
                                          @Value("${auth.enabled:false}") boolean authEnabled) {
        this.sensorMapper = sensorMapper;
        this.redisClient = redisClient;
        this.sensorTcpServer = sensorTcpServer;
        this.authEnabled = authEnabled;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = extractToken(session);
        if (!StringUtils.hasText(token)) {
            sendJson(session, buildError(401, "令牌不能为空"));
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Missing token"));
            return;
        }
        // 认证关闭时（开发环境）仅校验令牌存在；开启时进一步比对 Redis 登录态，与 AuthInterceptor 一致
        if (authEnabled) {
            Object cached = redisClient.get(AuthConst.USER_TOKEN_HEAD + token);
            if (cached == null) {
                sendJson(session, buildError(401, "令牌无效或已过期"));
                session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Unauthorized"));
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message == null ? null : message.getPayload();
        if (!StringUtils.hasText(payload)) {
            return;
        }
        JSONObject obj = tryParseObject(payload);
        String type = obj == null ? null : obj.getString("type");
        if ("ping".equalsIgnoreCase(type)) {
            handlePing(session);
            return;
        }
        List<Long> sensorIds = extractSensorIds(payload);
        if (sensorIds == null) {
            sendJson(session, buildError(4001, "sensor_ids 参数缺失或格式错误"));
            return;
        }
        if ("unsubscribe".equalsIgnoreCase(type) || "unsubscribed".equalsIgnoreCase(type)) {
            handleUnsubscribe(session, sensorIds);
        } else {
            handleSubscribe(session, sensorIds);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        cancelTicker(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        cancelTicker(session);
    }

    // ==================== 订阅/取消订阅/心跳逻辑 ====================

    private void handleSubscribe(WebSocketSession session, List<Long> requested) throws Exception {
        List<Long> ids = requested.stream().distinct().toList();
        if (ids.isEmpty()) {
            return;
        }
        if (ids.size() > MAX_SUBSCRIBE_IDS) {
            sendJson(session, buildError(4002, "单次最多订阅 " + MAX_SUBSCRIBE_IDS + " 个传感器"));
            return;
        }
        List<AssetDeviceSensor> found = sensorMapper.selectList(new LambdaQueryWrapper<AssetDeviceSensor>()
                .in(AssetDeviceSensor::getId, ids)
                .eq(AssetDeviceSensor::getIsDeleted, 0));
        Set<Long> existingIds = found.stream()
                .map(AssetDeviceSensor::getId)
                .collect(Collectors.toSet());
        for (Long id : ids) {
            if (!existingIds.contains(id)) {
                sendJson(session, buildError(4001, "传感器ID不存在: " + id));
                return;
            }
        }
        Map<Long, AssetDeviceSensor> byId = found.stream()
                .collect(Collectors.toMap(AssetDeviceSensor::getId, Function.identity()));

        List<SensorState> states = new ArrayList<>();
        for (Long id : ids) {
            states.add(new SensorState(byId.get(id)));
        }

        cancelTicker(session);
        session.getAttributes().put(ATTR_SENSORS, states);
        startTicker(session);

        sendJson(session, buildSubscribed(states));
    }

    private void handleUnsubscribe(WebSocketSession session, List<Long> requested) throws Exception {
        List<Long> ids = requested.stream().distinct().toList();
        if (ids.isEmpty()) {
            return;
        }
        @SuppressWarnings("unchecked")
        List<SensorState> states = (List<SensorState>) session.getAttributes().get(ATTR_SENSORS);
        List<Long> removed = new ArrayList<>();
        if (states != null) {
            Set<Long> toRemove = new HashSet<>(ids);
            List<SensorState> remaining = new ArrayList<>();
            for (SensorState state : states) {
                if (toRemove.contains(state.id)) {
                    removed.add(state.id);
                } else {
                    remaining.add(state);
                }
            }
            if (remaining.isEmpty()) {
                cancelTicker(session);
            } else {
                session.getAttributes().put(ATTR_SENSORS, remaining);
            }
        }
        sendJson(session, buildUnsubscribed(removed));
    }

    private void handlePing(WebSocketSession session) throws Exception {
        JSONObject data = new JSONObject();
        data.put("timestamp", System.currentTimeMillis());
        sendJson(session, of("pong", data));
    }

    private void startTicker(WebSocketSession session) {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            private final AtomicInteger seq = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "sensor-realtime-" + seq.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        });
        session.getAttributes().put(ATTR_EXECUTOR, executor);
        executor.scheduleWithFixedDelay(() -> pushDueSensors(session), TICK_MILLIS, TICK_MILLIS, TimeUnit.MILLISECONDS);
    }

    private void pushDueSensors(WebSocketSession session) {
        try {
            long now = System.currentTimeMillis();
            @SuppressWarnings("unchecked")
            List<SensorState> states = (List<SensorState>) session.getAttributes().get(ATTR_SENSORS);
            if (states == null || !session.isOpen()) {
                return;
            }
            for (SensorState state : states) {
                if (state.nextPushAt <= now) {
                    state.nextPushAt = now + state.refreshRateMs;
                    JSONObject sensorData = buildSensorData(state);
                    if (sensorData != null) {
                        sendJson(session, sensorData);
                    }
                }
            }
        } catch (Exception ignored) {
            cancelTicker(session);
        }
    }

    private void cancelTicker(WebSocketSession session) {
        Object executor = session.getAttributes().get(ATTR_EXECUTOR);
        if (executor instanceof ScheduledExecutorService service) {
            service.shutdownNow();
        }
        session.getAttributes().remove(ATTR_EXECUTOR);
        session.getAttributes().remove(ATTR_SENSORS);
    }

    // ==================== 消息构建 ====================

    private JSONObject buildSubscribed(List<SensorState> states) {
        List<Long> ids = states.stream().map(s -> s.id).toList();
        JSONObject data = new JSONObject();
        data.put("sensor_ids", ids);
        data.put("message", "成功订阅 " + ids.size() + " 个传感器");
        return of("subscribed", data);
    }

    private JSONObject buildUnsubscribed(List<Long> ids) {
        JSONObject data = new JSONObject();
        data.put("sensor_ids", ids);
        data.put("message", "成功取消订阅 " + ids.size() + " 个传感器");
        return of("unsubscribed", data);
    }

    // ==================== 数据推送 ====================

    /**
     * 读取 mock_sensor_report.py 经 SensorTcpServer 上报的最新读数；
     * 若尚未收到该传感器的任何数据，则返回 null（推流时跳过，不发送空值消息）。
     * 缓存键使用传感器自身 id（与 mock 脚本 --sensor-id 对应，前端也按传感器 id 订阅），
     * 不使用 parentId，避免同一监测点下的多个传感器共享数据。
     */
    private JSONObject buildSensorData(SensorState state) {
        String raw = sensorTcpServer.getLatestSensorData(state.id).orElse(null);
        JSONObject reported = raw == null ? null : tryParseObject(raw);
        if (reported == null) {
            return null;
        }
        JSONObject data = new JSONObject();
        data.put("id", state.id);
        data.put("monitor_type", state.monitorType);
        data.put("value", reported.getBigDecimal("metricValue"));
        data.put("unit", reported.getString("unit") == null
                ? state.unit : reported.getString("unit"));
        if (reported.getString("collectTime") != null) {
            data.put("collect_time", reported.getString("collectTime"));
        }
        return of("sensor_data", data);
    }

    // ==================== 解析工具 ====================

    private JSONObject buildError(int code, String message) {
        JSONObject data = new JSONObject();
        data.put("code", code);
        data.put("message", message);
        return of("error", data);
    }

    private JSONObject of(String type, JSONObject data) {
        JSONObject msg = new JSONObject();
        msg.put("type", type);
        msg.put("data", data);
        return msg;
    }

    // ==================== 解析工具 ====================

    /** 从订阅消息中提取传感器ID集合；消息不含合法ID列表时返回 null。 */
    private List<Long> extractSensorIds(String payload) {
        try {
            String trimmed = payload.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            if (trimmed.startsWith("[")) {
                JSONArray array = JSON.parseArray(trimmed);
                return array == null ? null : toSensors(array);
            }
            JSONObject obj = JSON.parseObject(trimmed);
            if (obj == null) {
                return null;
            }
            Object raw = obj.get("sensor_ids");
            if (raw == null) {
                raw = obj.get("sensorIds");
            }
            if (raw == null) {
                JSONObject data = obj.getJSONObject("data");
                if (data != null) {
                    raw = data.get("sensor_ids");
                    if (raw == null) {
                        raw = data.get("sensorIds");
                    }
                }
            }
            if (raw instanceof JSONArray array) {
                return toSensors(array);
            }
            if (raw instanceof String csv) {
                return toSensors(JSONArray.of(csv.split(",")));
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<Long> toSensors(JSONArray array) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            Long id = toLong(array.get(i));
            if (id != null) {
                ids.add(id);
            }
        }
        return ids.isEmpty() ? null : ids;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            String text = String.valueOf(value).trim();
            return text.isEmpty() ? null : Long.valueOf(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private JSONObject tryParseObject(String payload) {
        try {
            return payload == null ? null : JSON.parseObject(payload);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void sendJson(WebSocketSession session, Object message) throws IOException {
        synchronized (session) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(JSON.toJSONString(message)));
            }
        }
    }

    private String extractToken(WebSocketSession session) {
        if (session.getUri() == null) {
            return null;
        }
        String token = UriComponentsBuilder.fromUri(session.getUri()).build()
                .getQueryParams().getFirst("token");
        if (!StringUtils.hasText(token)) {
            return null;
        }
        if (token.startsWith(AuthConst.BEARER_PREFIX)) {
            return token.substring(AuthConst.BEARER_PREFIX.length());
        }
        return token;
    }

    /** 订阅的传感器运行时状态 */
    private static final class SensorState {
        private final Long id;
        private final String monitorType;
        private final String unit;
        private final long refreshRateMs;
        private long nextPushAt;

        private SensorState(AssetDeviceSensor sensor) {
            this.id = sensor.getId();
            this.monitorType = sensor.getMonitorType();
            this.unit = sensor.getUnit();
            int refreshRate = sensor.getRefreshRate() == null ? 0 : sensor.getRefreshRate();
            this.refreshRateMs = Math.max(MIN_REFRESH_MS, refreshRate <= 0 ? DEFAULT_REFRESH_MS : refreshRate * 1000L);
            this.nextPushAt = System.currentTimeMillis();
        }
    }
}