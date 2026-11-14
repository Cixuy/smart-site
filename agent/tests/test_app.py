import os

os.environ['AGENT_TEST_MODE'] = 'true'
os.environ['AGENT_TEST_USER_ID'] = '1'
os.environ['AGENT_MOCK_MODEL'] = 'true'

from fastapi.testclient import TestClient
from sqlalchemy import create_engine, text
from sqlalchemy.pool import StaticPool

from agent import app


def create_schema() -> None:
    with app.engine.begin() as conn:
        conn.execute(text('CREATE TABLE sys_user (id INTEGER PRIMARY KEY, status INTEGER NOT NULL DEFAULT 1, is_deleted INTEGER NOT NULL DEFAULT 0)'))
        conn.execute(text('INSERT INTO sys_user (id, status, is_deleted) VALUES (1, 1, 0)'))
        conn.execute(text('''CREATE TABLE biz_agent_session (
            session_id TEXT PRIMARY KEY, user_id INTEGER NOT NULL, project_id INTEGER,
            title TEXT NOT NULL, created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL)'''))
        conn.execute(text('''CREATE TABLE biz_agent_message (
            message_id TEXT PRIMARY KEY, session_id TEXT NOT NULL, user_id INTEGER NOT NULL,
            role TEXT NOT NULL, content TEXT NOT NULL, status TEXT NOT NULL, project_id INTEGER,
            intent_type TEXT, context_json TEXT, response_duration_ms INTEGER,
            error_message TEXT, created_at DATETIME NOT NULL, completed_at DATETIME)'''))
        conn.execute(text('''CREATE TABLE biz_alarm_record (
            id INTEGER PRIMARY KEY, alarm_no TEXT, project_id INTEGER NOT NULL,
            alarm_level INTEGER NOT NULL, alarm_type_code TEXT, alarm_content TEXT,
            device_id INTEGER, alarm_time DATETIME, status INTEGER, is_deleted INTEGER NOT NULL DEFAULT 0)'''))
        conn.execute(text('''CREATE TABLE asset_device (
            id INTEGER PRIMARY KEY, device_code TEXT, device_name TEXT, brand TEXT, model TEXT,
            online_status INTEGER, enable_status INTEGER, last_online_time DATETIME,
            location_id INTEGER, is_deleted INTEGER NOT NULL DEFAULT 0)'''))
        conn.execute(text('''CREATE TABLE biz_env_monitor_data (
            id INTEGER PRIMARY KEY, parent_id INTEGER, collect_time DATETIME,
            metric_type TEXT, metric_value NUMERIC, unit TEXT, status INTEGER)'''))
        conn.execute(text('''CREATE TABLE biz_tower_crane_realtime (
            id INTEGER PRIMARY KEY, device_id TEXT, collect_time DATETIME,
            load_weight NUMERIC, amplitude NUMERIC, moment NUMERIC, moment_percent NUMERIC,
            wind_speed NUMERIC, height NUMERIC, slew_angle NUMERIC, alarm_status INTEGER)'''))
        conn.execute(text('''CREATE TABLE biz_hoist_realtime (
            id INTEGER PRIMARY KEY, device_id TEXT, collect_time DATETIME,
            load_weight NUMERIC, load_percent NUMERIC, height NUMERIC, run_direction INTEGER,
            front_door_status INTEGER, rear_door_status INTEGER, passenger_count INTEGER,
            wind_speed NUMERIC, interlock_alarm INTEGER)'''))
        conn.execute(text('''CREATE TABLE biz_chat_record (
            id INTEGER PRIMARY KEY AUTOINCREMENT, session_id TEXT NOT NULL, user_id INTEGER NOT NULL,
            project_id INTEGER, question TEXT NOT NULL, answer TEXT NOT NULL, question_time DATETIME NOT NULL,
            answer_time DATETIME NOT NULL, response_duration_ms INTEGER, intent_type TEXT,
            context_json TEXT, token_usage INTEGER, is_success INTEGER NOT NULL, error_msg TEXT,
            created_at DATETIME NOT NULL)'''))


def setup_function() -> None:
    app.engine = create_engine('sqlite+pysqlite:///:memory:', connect_args={'check_same_thread': False}, poolclass=StaticPool, future=True)
    create_schema()


def test_five_endpoint_workflow() -> None:
    client = TestClient(app.app)
    created = client.post('/api/agent/sessions', json={'title': '塔吊安全分析', 'projectId': 1})
    assert created.status_code == 201
    assert created.json()['data']['projectId'] == 1
    session_id = created.json()['data']['sessionId']
    assert client.get('/api/agent/sessions').json()['data']['records'][0]['sessionId'] == session_id
    sent = client.post(f'/api/agent/sessions/{session_id}/messages', json={'content': '分析今日塔吊告警原因', 'context': {'deviceId': 101}})
    assert sent.status_code == 202
    response_id = sent.json()['data']['assistantMessageId']
    stream = client.get(f'/api/agent/sessions/{session_id}/responses/{response_id}/stream')
    assert stream.status_code == 200
    assert stream.headers['content-type'].startswith('text/event-stream')
    assert 'event: tool' in stream.text and 'event: delta' in stream.text and 'event: done' in stream.text
    records = client.get(f'/api/agent/sessions/{session_id}/messages').json()['data']['records']
    assert [x['role'] for x in records] == ['user', 'assistant']
    assert records[1]['status'] == 'completed' and records[1]['intentType'] == 'alarm_analysis'


def test_message_conflict_and_not_found_errors() -> None:
    client = TestClient(app.app)
    session_id = client.post('/api/agent/sessions').json()['data']['sessionId']
    assert client.post(f'/api/agent/sessions/{session_id}/messages', json={'content': '当前安全吗'}).status_code == 202
    conflict = client.post(f'/api/agent/sessions/{session_id}/messages', json={'content': '再问一次'})
    assert conflict.status_code == 409 and conflict.json()['code'] == 40901
    missing = client.get('/api/agent/sessions/not-found/messages')
    assert missing.status_code == 404 and missing.json()['code'] == 40401


def test_database_not_configured_error() -> None:
    client = TestClient(app.app)
    old = app.engine
    app.engine = None
    try:
        response = client.get('/api/agent/sessions')
        assert response.status_code == 503 and response.json()['code'] == 50301
    finally:
        app.engine = old

def test_empty_list_query_uses_defaults() -> None:
    client = TestClient(app.app)
    response = client.get('/api/agent/sessions?projectId=&pageNo=&pageSize=')
    assert response.status_code == 200
    body = response.json()
    assert body['code'] == 0
    assert body['data']['pageNo'] == 1 and body['data']['pageSize'] == 20


def test_invalid_list_query_uses_standard_error() -> None:
    client = TestClient(app.app)
    response = client.get('/api/agent/sessions?pageNo=abc')
    assert response.status_code == 400
    assert response.json() == {'code': 40001, 'msg': 'pageNo必须是整数', 'data': None}

def test_empty_history_query_uses_defaults() -> None:
    client = TestClient(app.app)
    session_id = client.post('/api/agent/sessions').json()['data']['sessionId']
    response = client.get(f'/api/agent/sessions/{session_id}/messages?pageNo=&pageSize=')
    assert response.status_code == 200
    assert response.json()['data']['pageNo'] == 1
    assert response.json()['data']['pageSize'] == 20

def test_session_list_is_scoped_to_current_user() -> None:
    client = TestClient(app.app)
    first = client.post('/api/agent/sessions', json={'title': '当前用户会话'}).json()['data']['sessionId']
    with app.engine.begin() as conn:
        conn.execute(text("INSERT INTO biz_agent_session (session_id,user_id,project_id,title,created_at,updated_at) VALUES ('sess_other_user',2,NULL,'其他用户会话','2026-08-13 12:00:00','2026-08-13 12:00:00')"))
    response = client.get('/api/agent/sessions')
    assert response.status_code == 200
    ids = [item['sessionId'] for item in response.json()['data']['records']]
    assert first in ids
    assert 'sess_other_user' not in ids


def test_agent_loop_uses_tools_and_frontend_context() -> None:
    with app.engine.begin() as conn:
        conn.execute(text("INSERT INTO biz_alarm_record VALUES (1,'ALM-001',7,2,'tower-overload','塔吊载荷超限',101,'2026-08-13 10:00:00',0,0)"))
        conn.execute(text("INSERT INTO asset_device VALUES (101,'TC-001','一号塔吊','品牌A','QTZ80',1,1,'2026-08-13 10:01:00',5,0)"))
        conn.execute(text("INSERT INTO biz_tower_crane_realtime VALUES (1,'TC-001','2026-08-13 10:02:00',20,30,40,80,3,50,90,0)"))
    loop = app.BuildingSafetyAgentLoop(app.db, 1, 7, {'deviceId': 101})
    run = loop.run('检查 1 号塔吊健康状况')
    assert run.intent_type == 'device_diagnosis'
    assert [trace['name'] for trace in run.tool_trace] == ['query_device_health', 'query_alarm_analysis']
    assert run.tool_trace[0]['status'] == 'completed'
    assert '模拟 Agent Loop 回复' in run.answer

def test_sse_outputs_success_logs(caplog) -> None:
    client = TestClient(app.app)
    session_id = client.post('/api/agent/sessions', json={'projectId': 1}).json()['data']['sessionId']
    sent = client.post(f'/api/agent/sessions/{session_id}/messages', json={'content': '查询项目安全信息'})
    response_id = sent.json()['data']['assistantMessageId']
    with caplog.at_level('INFO', logger='dialogue_agent'):
        response = client.get(f'/api/agent/sessions/{session_id}/responses/{response_id}/stream')
    assert response.status_code == 200
    assert 'SSE response requested' in caplog.text
    assert 'SSE tool' in caplog.text
    assert 'SSE delta' in caplog.text
    assert 'SSE response completed' in caplog.text

def test_sse_outputs_business_query_error_and_log(caplog) -> None:
    client = TestClient(app.app)
    session_id = client.post('/api/agent/sessions').json()['data']['sessionId']
    sent = client.post(f'/api/agent/sessions/{session_id}/messages', json={'content': '查询当前安全信息'})
    response_id = sent.json()['data']['assistantMessageId']
    with app.engine.begin() as conn:
        conn.execute(text('DROP TABLE asset_device'))
    with caplog.at_level('ERROR', logger='dialogue_agent'):
        response = client.get(f'/api/agent/sessions/{session_id}/responses/{response_id}/stream')
    assert response.status_code == 200
    assert 'event: error' in response.text
    assert '"code": 50304' in response.text
    assert 'agent tool failed' in caplog.text
    assert 'SSE response failed' in caplog.text

def test_langchain_tool_call_loop_without_network(monkeypatch) -> None:
    from langchain_core.messages import AIMessage, ToolMessage
    from agent import agent_loop

    with app.engine.begin() as conn:
        conn.execute(text("INSERT INTO biz_alarm_record VALUES (1,'ALM-002',3,1,'WIND_EXCEED','风速超限',NULL,'2026-08-13 10:00:00',0,0)"))

    class FakeBoundModel:
        def __init__(self) -> None:
            self.calls = 0

        def invoke(self, messages):
            self.calls += 1
            if self.calls == 1:
                return AIMessage(content='', tool_calls=[{
                    'name': 'query_alarm_analysis', 'args': {'days': 1}, 'id': 'call_alarm', 'type': 'tool_call',
                }])
            assert any(isinstance(message, ToolMessage) for message in messages)
            return AIMessage(content='已根据告警数据完成分析。')

    class FakeModel:
        def __init__(self) -> None:
            self.bound = FakeBoundModel()

        def bind_tools(self, _tools):
            return self.bound

    monkeypatch.setenv('AGENT_MOCK_MODEL', 'false')
    monkeypatch.setattr(agent_loop, 'create_chat_model', lambda: FakeModel())
    run = agent_loop.BuildingSafetyAgentLoop(app.db, 1, 3, {}).run('分析今日告警原因')
    assert run.answer == '已根据告警数据完成分析。'
    assert run.tool_trace[0]['name'] == 'query_alarm_analysis'
    assert run.tool_trace[0]['status'] == 'completed'