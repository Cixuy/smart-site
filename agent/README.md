# 对话智能体服务

独立 FastAPI 服务，提供已设计的 5 个 `/api/agent/**` 接口。启动前将 `.env.example` 复制为 `.env`，在 `DASHSCOPE_API_KEY=` 后填写阿里云 DashScope API Key。

```powershell
.\agent\.venv\Scripts\python.exe -m uvicorn agent.app:app --host 0.0.0.0 --port 8000
```

If PowerShell is already in the `agent` directory, use:

```powershell
.\.venv\Scripts\python.exe -m uvicorn app:app --host 0.0.0.0 --port 8000
```

首次部署运行 `agent/sql/001_dialogue_agent.sql`。它只创建 `biz_agent_session` 与 `biz_agent_message`；最终完成的问答会写入既有 `biz_chat_record`。所有数据库时间以 `Asia/Shanghai` 无时区 `DATETIME` 参数写入，未使用分区 `MAXVALUE` 语义。

生产环境配置 `AGENT_AUTH_URL` 为现有 Spring Boot 的 `/api/auth/currentUser`，以平台 Bearer Token 获取当前用户。不要设置 `AGENT_TEST_MODE=true` 或 `AGENT_MOCK_MODEL=true`。

服务使用 LangChain Agent Loop 调用受限的只读工具：项目安全态势、告警分析、设备健康诊断和环境最新数据。每次响应最多执行 `AGENT_LOOP_MAX_STEPS`（默认 3）轮工具调用；SSE 除原有 `delta`、`done`、`error` 外，还会发送 `tool` 事件显示工具执行情况。

测试：

```powershell
.\agent\.venv\Scripts\python.exe -m pytest agent\tests -q
```
