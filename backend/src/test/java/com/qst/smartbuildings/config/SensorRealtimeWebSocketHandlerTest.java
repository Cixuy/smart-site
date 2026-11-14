package com.qst.smartbuildings.config;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qst.smartbuildings.domain.AssetDeviceSensor;
import com.qst.smartbuildings.mapper.AssetDeviceSensorMapper;
import com.qst.smartbuildings.service.SensorTcpServer;
import com.qst.smartbuildings.util.RedisClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SensorRealtimeWebSocketHandlerTest {

    private static final String SUBSCRIBE_URL = "ws://localhost:8080/api/ws/sensor/subscribe?token=dev-token";

    @Mock
    private AssetDeviceSensorMapper sensorMapper;
    @Mock
    private RedisClient redisClient;
    @Mock
    private SensorTcpServer sensorTcpServer;

    private SensorRealtimeWebSocketHandler handler;
    private WebSocketSession session;

    @BeforeEach
    void setUp() throws Exception {
        handler = new SensorRealtimeWebSocketHandler(sensorMapper, redisClient, sensorTcpServer, false);
        session = session(SUBSCRIBE_URL);
        handler.afterConnectionEstablished(session);
    }

    @AfterEach
    void tearDown() {
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);
    }

    @Test
    void subscribeValidSensorsReturnsSubscribedMessage() throws Exception {
        when(sensorMapper.selectList(any())).thenReturn(List.of(sensor(1L, "WIND_SPEED", "m/s"),
                sensor(2L, "TEMP", "℃")));

        handler.handleTextMessage(session, new TextMessage("{\"type\":\"subscribe\",\"sensor_ids\":[1,2]}"));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        JSONObject msg = JSON.parseObject(captor.getValue().getPayload());
        assertThat(msg.getString("type")).isEqualTo("subscribed");
        JSONObject data = msg.getJSONObject("data");
        assertThat(asLongs(data.getJSONArray("sensor_ids"))).containsExactlyInAnyOrder(1L, 2L);
        assertThat(data.getString("message")).isEqualTo("成功订阅 2 个传感器");
    }

    @Test
    void subscribeWithMissingSensorReturnsError4001() throws Exception {
        when(sensorMapper.selectList(any())).thenReturn(List.of(sensor(1L, "WIND_SPEED", "m/s")));

        handler.handleTextMessage(session, new TextMessage("{\"type\":\"subscribe\",\"sensor_ids\":[1,999]}"));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        JSONObject msg = JSON.parseObject(captor.getValue().getPayload());
        assertThat(msg.getString("type")).isEqualTo("error");
        JSONObject data = msg.getJSONObject("data");
        assertThat(data.getIntValue("code")).isEqualTo(4001);
        assertThat(data.getString("message")).isEqualTo("传感器ID不存在: 999");
    }

    @Test
    void subscribeAcceptsRawJsonArray() throws Exception {
        when(sensorMapper.selectList(any())).thenReturn(List.of(sensor(1L, "WIND_SPEED", "m/s")));

        handler.handleTextMessage(session, new TextMessage("[1]"));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        JSONObject msg = JSON.parseObject(captor.getValue().getPayload());
        assertThat(msg.getString("type")).isEqualTo("subscribed");
        assertThat(asLongs(msg.getJSONObject("data").getJSONArray("sensor_ids"))).containsExactly(1L);
    }

    @Test
    void subscribeAcceptsDataNestedSensorIds() throws Exception {
        when(sensorMapper.selectList(any())).thenReturn(List.of(sensor(1L, "WIND_SPEED", "m/s"),
                sensor(2L, "TEMP", "℃")));

        handler.handleTextMessage(session, new TextMessage("{\"type\":\"subscribe\",\"data\":{\"sensor_ids\":[1,2]}}"));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        JSONObject msg = JSON.parseObject(captor.getValue().getPayload());
        assertThat(msg.getString("type")).isEqualTo("subscribed");
        assertThat(asLongs(msg.getJSONObject("data").getJSONArray("sensor_ids"))).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void streamsSensorDataFromReceiver() throws Exception {
        when(sensorMapper.selectList(any())).thenReturn(List.of(sensor(1L, 100L, "wind_speed", "m/s")));
        // mock 脚本 --sensor-id 1 上报，缓存键与传感器自身 id 一致
        when(sensorTcpServer.getLatestSensorData(1L)).thenReturn(java.util.Optional.of(
                "{\"monitorPointId\":1,\"metricType\":\"wind_speed\",\"metricValue\":12.34,\"unit\":\"m/s\",\"collectTime\":\"2026-08-15 10:00:00.123\"}"));

        handler.handleTextMessage(session, new TextMessage("[1]"));

        // 等待第一个定时推流周期触发（默认 250ms 扫描，这里留足余量）
        Thread.sleep(700);

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, atLeastOnce()).sendMessage(captor.capture());
        List<TextMessage> all = captor.getAllValues();
        JSONObject sensorData = all.stream()
                .map(t -> JSON.parseObject(t.getPayload()))
                .filter(m -> "sensor_data".equals(m.getString("type")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未收到 sensor_data 消息"));
        JSONObject data = sensorData.getJSONObject("data");
        assertThat(data.getLong("id")).isEqualTo(1L);
        assertThat(data.getString("monitor_type")).isEqualTo("wind_speed");
        assertThat(data.getBigDecimal("value")).isEqualByComparingTo("12.34");
        assertThat(data.getString("unit")).isEqualTo("m/s");
        assertThat(data.getString("collect_time")).isEqualTo("2026-08-15 10:00:00.123");
    }

    @Test
    void sensorDataDoesNotLeakAcrossSensorsSharingParent() throws Exception {
        when(sensorMapper.selectList(any())).thenReturn(List.of(sensor(1L, 1L, "TEMP", "℃"),
                sensor(16L, 1L, "PM25", "ug/m3")));
        // 两个传感器 parentId 相同（都挂在监测点1），但 mock --sensor-id 1 只该给传感器1
        when(sensorTcpServer.getLatestSensorData(1L)).thenReturn(java.util.Optional.of(
                "{\"monitorPointId\":1,\"metricType\":\"TEMP\",\"metricValue\":25.5,\"unit\":\"℃\",\"collectTime\":\"2026-08-15 10:00:00.123\"}"));

        handler.handleTextMessage(session, new TextMessage("[1,16]"));

        Thread.sleep(700);

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, atLeastOnce()).sendMessage(captor.capture());
        List<JSONObject> pushed = captor.getAllValues().stream()
                .map(t -> JSON.parseObject(t.getPayload()))
                .filter(m -> "sensor_data".equals(m.getString("type")))
                .toList();
        assertThat(pushed).hasSize(1);
        assertThat(pushed.get(0).getJSONObject("data").getLong("id")).isEqualTo(1L);
        assertThat(pushed.get(0).getJSONObject("data").getBigDecimal("value")).isEqualByComparingTo("25.5");
    }

    @Test
    void streamsSensorDataSkipsPushWhenReceiverHasNoReading() throws Exception {
        when(sensorMapper.selectList(any())).thenReturn(List.of(sensor(1L, "wind_speed", "m/s")));

        handler.handleTextMessage(session, new TextMessage("[1]"));

        Thread.sleep(700);

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, atLeastOnce()).sendMessage(captor.capture());
        List<TextMessage> all = captor.getAllValues();
        assertThat(all.stream()
                .map(t -> JSON.parseObject(t.getPayload()))
                .anyMatch(m -> "sensor_data".equals(m.getString("type"))))
                .isFalse();
    }

    @Test
    void unsubscribeReturnsUnsubscribedMessage() throws Exception {
        when(sensorMapper.selectList(any())).thenReturn(List.of(sensor(1L, "WIND_SPEED", "m/s"),
                sensor(2L, "TEMP", "℃")));
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"subscribe\",\"sensor_ids\":[1,2]}"));
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"unsubscribe\",\"data\":{\"sensor_ids\":[1,2]}}"));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, atLeastOnce()).sendMessage(captor.capture());
        JSONObject unsub = captor.getAllValues().stream()
                .map(t -> JSON.parseObject(t.getPayload()))
                .filter(m -> "unsubscribed".equals(m.getString("type")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未收到 unsubscribed 消息"));
        JSONObject data = unsub.getJSONObject("data");
        assertThat(asLongs(data.getJSONArray("sensor_ids"))).containsExactlyInAnyOrder(1L, 2L);
        assertThat(data.getString("message")).isEqualTo("成功取消订阅 2 个传感器");
        assertThat(session.getAttributes()).doesNotContainKey("sensor.subscribed");
        assertThat(session.getAttributes()).doesNotContainKey("sensor.executor");
    }

    @Test
    void unsubscribeOnlyRemovesRequestedSensors() throws Exception {
        when(sensorMapper.selectList(any())).thenReturn(List.of(sensor(1L, "WIND_SPEED", "m/s"),
                sensor(2L, "TEMP", "℃")));
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"subscribe\",\"sensor_ids\":[1,2]}"));
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"unsubscribe\",\"sensor_ids\":[1]}"));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, atLeastOnce()).sendMessage(captor.capture());
        JSONObject unsub = captor.getAllValues().stream()
                .map(t -> JSON.parseObject(t.getPayload()))
                .filter(m -> "unsubscribed".equals(m.getString("type")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未收到 unsubscribed 消息"));
        assertThat(asLongs(unsub.getJSONObject("data").getJSONArray("sensor_ids"))).containsExactlyInAnyOrder(1L);
        assertThat(session.getAttributes()).containsKey("sensor.subscribed");
    }

    @Test
    void unsubscribeWithoutActiveSubscriptionAcksEmpty() throws Exception {
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"unsubscribe\",\"sensor_ids\":[1]}"));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        JSONObject msg = JSON.parseObject(captor.getValue().getPayload());
        assertThat(msg.getString("type")).isEqualTo("unsubscribed");
        assertThat(asLongs(msg.getJSONObject("data").getJSONArray("sensor_ids"))).isEmpty();
    }

    @Test
    void pingReturnsPongWithTimestamp() throws Exception {
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"ping\"}"));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        JSONObject msg = JSON.parseObject(captor.getValue().getPayload());
        assertThat(msg.getString("type")).isEqualTo("pong");
        assertThat(msg.getJSONObject("data").getLong("timestamp")).isPositive();
    }

    @Test
    void missingTokenRejectsConnection() throws Exception {
        WebSocketSession noToken = mock(WebSocketSession.class);
        when(noToken.getUri()).thenReturn(URI.create("ws://localhost:8080/api/ws/sensor/subscribe"));
        when(noToken.getAttributes()).thenReturn(new HashMap<>());
        when(noToken.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(noToken);

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(noToken).sendMessage(captor.capture());
        JSONObject msg = JSON.parseObject(captor.getValue().getPayload());
        assertThat(msg.getString("type")).isEqualTo("error");
        assertThat(msg.getJSONObject("data").getIntValue("code")).isEqualTo(401);
    }

    private WebSocketSession session(String url) {
        WebSocketSession s = mock(WebSocketSession.class);
        when(s.getUri()).thenReturn(URI.create(url));
        when(s.getAttributes()).thenReturn(new HashMap<>());
        when(s.isOpen()).thenReturn(true);
        return s;
    }

    private AssetDeviceSensor sensor(Long id, String monitorType, String unit) {
        return sensor(id, null, monitorType, unit);
    }

    private AssetDeviceSensor sensor(Long id, Long parentId, String monitorType, String unit) {
        AssetDeviceSensor s = new AssetDeviceSensor();
        s.setId(id);
        s.setParentId(parentId);
        s.setMonitorType(monitorType);
        s.setUnit(unit);
        s.setRefreshRate(5);
        return s;
    }

    /** fastjson2 将小整数解析为 Integer，先归一化为 Long 再断言，避免类型不匹配。 */
    private List<Long> asLongs(com.alibaba.fastjson2.JSONArray array) {
        List<Long> list = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            list.add(array.getLongValue(i));
        }
        return list;
    }
}