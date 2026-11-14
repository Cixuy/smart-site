package com.qst.smartbuildings.config;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qst.smartbuildings.common.AuthConst;
import com.qst.smartbuildings.common.SystemCodeEnum;
import com.qst.smartbuildings.domain.BizCamera;
import com.qst.smartbuildings.domain.BizVideoRecord;
import com.qst.smartbuildings.entity.Result;
import com.qst.smartbuildings.mapper.BizCameraMapper;
import com.qst.smartbuildings.mapper.BizVideoRecordMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;

@Component
public class VideoRealtimeWebSocketHandler extends TextWebSocketHandler {
    private final BizVideoRecordMapper videoRecordMapper;
    private final BizCameraMapper bizCameraMapper;

    public VideoRealtimeWebSocketHandler(BizVideoRecordMapper videoRecordMapper, BizCameraMapper bizCameraMapper) {
        this.videoRecordMapper = videoRecordMapper;
        this.bizCameraMapper = bizCameraMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = extractToken(session);
        if (!StringUtils.hasText(token)) {
            sendError(session, 401, "Token 无效或已过期");
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Unauthorized"));
            return;
        }
        session.sendMessage(new TextMessage(JSON.toJSONString(Map.of(
                "type", "connected",
                "msg", "WebSocket 连接已建立",
                "timestamp", OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).toString()
        ))));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        if (payload != null && payload.contains("\"type\":\"ping\"")) {
            session.sendMessage(new TextMessage(JSON.toJSONString(Map.of(
                    "type", "pong",
                    "timestamp", OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).toString()
            ))));
            return;
        }
        BizVideoRecord latestSnapshot = videoRecordMapper.selectOne(new LambdaQueryWrapper<BizVideoRecord>()
                .eq(BizVideoRecord::getRecordType, 3)
                .orderByDesc(BizVideoRecord::getStartTime)
                .last("LIMIT 1"));
        if (latestSnapshot == null) {
            session.sendMessage(new TextMessage(JSON.toJSONString(Map.of(
                    "type", "push",
                    "topic", "video:snapshot",
                    "data", Map.of("source", "video", "message", "暂无AI告警截图"),
                    "timestamp", OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).toString()
            ))));
            return;
        }
        BizCamera camera = latestSnapshot.getCameraId() == null ? null : bizCameraMapper.selectById(latestSnapshot.getCameraId());
        session.sendMessage(new TextMessage(JSON.toJSONString(Map.of(
                "type", "push",
                "topic", "video:camera:" + latestSnapshot.getCameraId(),
                "data", Map.of(
                        "source", "video",
                        "cameraId", latestSnapshot.getCameraId(),
                        "cameraName", camera == null ? "" : camera.getCameraName(),
                        "alarmTime", latestSnapshot.getStartTime(),
                        "snapshotUrl", latestSnapshot.getFilePath()
                ),
                "timestamp", OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).toString()
        ))));
    }

    private String extractToken(WebSocketSession session) {
        String token = UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams().getFirst("token");
        if (!StringUtils.hasText(token)) {
            return null;
        }
        if (token.startsWith(AuthConst.BEARER_PREFIX)) {
            return token.substring(AuthConst.BEARER_PREFIX.length());
        }
        return token;
    }

    private void sendError(WebSocketSession session, int code, String msg) throws IOException {
        session.sendMessage(new TextMessage(JSON.toJSONString(Result.error(code, msg))));
    }
}
