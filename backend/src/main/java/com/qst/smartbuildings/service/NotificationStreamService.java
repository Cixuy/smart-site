package com.qst.smartbuildings.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 通知 SSE 信号流服务
 *
 * <p>维护通知中心的新消息 SSE 长连接：当系统产生新的未读通知时，调用
 * {@link #publishNewMessage()} 向所有在线客户端推送 {@code event: new-message}，
 * 客户端收到后自行刷新未读计数。</p>
 */
@Service
public class NotificationStreamService {

    /** 连接超时时间（5分钟，超时后由客户端 EventSource 自动重连） */
    private static final long SSE_TIMEOUT = 300_000L;
    /** 心跳间隔（秒），维持长连接避免被代理断开 */
    private static final long HEARTBEAT_INTERVAL_SECONDS = 15L;
    /** 新消息事件名 */
    private static final String NEW_MESSAGE_EVENT = "new-message";
    /** 新消息事件数据（与接口文档约定一致） */
    private static final String NEW_MESSAGE_DATA = "{\"hasNew\":true}";

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "notification-sse-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    public NotificationStreamService() {
        heartbeatExecutor.scheduleWithFixedDelay(this::heartbeat,
                HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 注册一个 SSE 客户端连接
     *
     * @return 供控制器直接返回给客户端的 SseEmitter
     */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        String id = UUID.randomUUID().toString();
        emitters.put(id, emitter);
        emitter.onCompletion(() -> emitters.remove(id));
        emitter.onTimeout(() -> emitters.remove(id));
        emitter.onError(e -> emitters.remove(id));
        return emitter;
    }

    /**
     * 推送新消息信号给所有在线客户端
     *
     * <p>产生新的未读通知时调用；发送失败（连接已断开）的客户端会被移除。</p>
     */
    public void publishNewMessage() {
        emitters.forEach((id, emitter) -> {
            try {
                emitter.send(SseEmitter.event().name(NEW_MESSAGE_EVENT).data(NEW_MESSAGE_DATA));
            } catch (IOException ex) {
                emitters.remove(id);
            }
        });
    }

    /**
     * 心跳：周期性发送注释消息，防止空闲连接被网关或代理断开
     */
    private void heartbeat() {
        emitters.forEach((id, emitter) -> {
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException ex) {
                emitters.remove(id);
            }
        });
    }
}
