import json
import logging
import os
import re
import sys
import time
import uuid
from contextlib import contextmanager
from datetime import datetime
from threading import Lock

# Support both `uvicorn agent.app:app` from the repository root and
# `uvicorn app:app` from the agent directory.
if not __package__:
    sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from typing import Annotated, Any, Generator

import httpx
from fastapi import Depends, FastAPI, Header, Query, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, StreamingResponse
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy import create_engine, text
from agent.access import AccessError, authorize_project, data_scope
from sqlalchemy.exc import DBAPIError, OperationalError

from agent.agent_loop import BuildingSafetyAgentLoop, classify_intent
from agent.errors import AgentLoopError

TZ_FORMAT = '%Y-%m-%d %H:%M:%S'

logging.basicConfig(
    level=os.getenv('AGENT_LOG_LEVEL', 'INFO').upper(),
    format='%(asctime)s %(levelname)s [dialogue-agent] %(message)s',
)
logger = logging.getLogger('dialogue_agent')
_response_locks: dict[str, Lock] = {}
_response_locks_guard = Lock()


def response_lock(message_id: str) -> Lock:
    with _response_locks_guard:
        return _response_locks.setdefault(message_id, Lock())


def load_env() -> None:
    path = os.path.join(os.path.dirname(__file__), '.env')
    if not os.path.exists(path):
        return
    with open(path, encoding='utf-8-sig') as env_file:
        for raw_line in env_file:
            line = raw_line.strip()
            if not line or line.startswith('#') or '=' not in line:
                continue
            key, value = line.split('=', 1)
            value = value.strip().strip('"').strip("'")
            os.environ.setdefault(key.strip(), value)


load_env()
DATABASE_URL = os.getenv('AGENT_DATABASE_URL', '').strip()
TEST_MODE = os.getenv('AGENT_TEST_MODE', 'false').lower() == 'true'
MOCK_MODEL = os.getenv('AGENT_MOCK_MODEL', 'false').lower() == 'true'
PLACEHOLDER_VALUES = {'', '你的数据库密码', 'CHANGE_ME', '你的阿里云DashScope_API_Key'}
if DATABASE_URL in PLACEHOLDER_VALUES:
    DATABASE_URL = ''
engine = create_engine(DATABASE_URL, pool_pre_ping=True, future=True) if DATABASE_URL else None


class AppError(Exception):
    def __init__(self, code: int, message: str, http_status: int):
        self.code, self.message, self.http_status = code, message, http_status


def now() -> datetime:
    return datetime.now().replace(microsecond=0)


def public_time(value: Any) -> str | None:
    if value is None:
        return None
    if isinstance(value, str):
        value = datetime.strptime(value[:19], TZ_FORMAT)
    return value.isoformat() + '+08:00'


def result(data: Any = None, msg: str = 'success') -> dict[str, Any]:
    return {'code': 0, 'msg': msg, 'data': data}


class CreateSession(BaseModel):
    model_config = ConfigDict(populate_by_name=True)
    title: str | None = Field(default=None, min_length=1, max_length=100)
    project_id: Annotated[int | None, Field(default=None, alias='projectId', gt=0)]


class SendMessage(BaseModel):
    model_config = ConfigDict(populate_by_name=True)
    content: str = Field(min_length=1, max_length=4000)
    project_id: Annotated[int | None, Field(default=None, alias='projectId', gt=0)]
    context: dict[str, Any] | None = None


@contextmanager
def db():
    if engine is None:
        raise AppError(50301, '数据库连接未配置', 503)
    try:
        with engine.begin() as conn:
            yield conn
    except AppError:
        raise
    except (OperationalError, DBAPIError) as exc:
        raise AppError(50301, '数据库连接或执行失败', 503) from exc


def current_user(authorization: str | None = Header(default=None), access_token: str | None = Query(default=None)) -> int:
    if TEST_MODE:
        configured_id = int(os.getenv('AGENT_TEST_USER_ID', '1'))
        if engine is None:
            raise AppError(50301, '数据库连接未配置', 503)
        try:
            with engine.connect() as conn:
                exists = conn.execute(text('SELECT id FROM sys_user WHERE id=:id AND status=1 AND is_deleted=0'), {'id': configured_id}).scalar_one_or_none()
                if exists is not None:
                    return int(exists)
                fallback = conn.execute(text('SELECT id FROM sys_user WHERE status=1 AND is_deleted=0 ORDER BY id LIMIT 1')).scalar_one_or_none()
        except (OperationalError, DBAPIError) as exc:
            raise AppError(50301, '数据库连接或执行失败', 503) from exc
        if fallback is None:
            raise AppError(40102, '测试用户不存在或没有可用用户，请配置 AGENT_TEST_USER_ID', 401)
        return int(fallback)
    if not authorization and access_token:
        authorization = 'Bearer ' + access_token
    if not authorization or not authorization.startswith('Bearer '):
        raise AppError(40101, '缺少或无效的 Authorization Bearer Token', 401)
    try:
        response = httpx.get(os.environ['AGENT_AUTH_URL'], headers={'Authorization': authorization}, timeout=5)
    except (httpx.ConnectError, httpx.TimeoutException) as exc:
        raise AppError(50302, '认证服务不可用', 503) from exc
    if response.status_code == 401:
        raise AppError(40101, '登录已失效', 401)
    try:
        body = response.json()
        user_id = body['data']['id']
    except (ValueError, KeyError, TypeError) as exc:
        raise AppError(50201, '认证服务返回格式异常', 502) from exc
    if body.get('code') != 0 or not user_id:
        raise AppError(40101, body.get('msg', '登录已失效'), 401)
    return int(user_id)


def row_dict(row: Any) -> dict[str, Any]:
    return dict(row._mapping)


def require_session(conn: Any, session_id: str, user_id: int) -> dict[str, Any]:
    row = conn.execute(text('SELECT * FROM biz_agent_session WHERE session_id=:sid'), {'sid': session_id}).first()
    if row is None:
        raise AppError(40401, '会话不存在', 404)
    data = row_dict(row)
    if int(data['user_id']) != user_id:
        if data.get('project_id') is None:
            raise AppError(40301, '无权访问该会话', 403)
        authorize_project(conn, user_id, int(data['project_id']))
    return data


def session_view(row: dict[str, Any]) -> dict[str, Any]:
    return {'sessionId': row['session_id'], 'title': row['title'], 'projectId': row['project_id'],
            'lastMessagePreview': row.get('last_message_preview'), 'lastMessageAt': public_time(row.get('last_message_at')),
            'createdAt': public_time(row['created_at'])}


def classify(question: str) -> str:
    return classify_intent(question)

def persist_answer(message_id: str, question: str) -> dict[str, Any]:
    started = time.monotonic()
    intent = classify(question)
    tool_trace: list[dict[str, Any]] = []
    try:
        with db() as conn:
            source = conn.execute(text('''
                SELECT session_id, user_id, project_id, context_json
                FROM biz_agent_message WHERE message_id=:id'''), {'id': message_id}).first()
            if source is None:
                raise AppError(40402, '消息不存在', 404)
            source_data = row_dict(source)
            try:
                request_context = json.loads(source_data['context_json'] or '{}')
            except (TypeError, ValueError) as exc:
                raise AppError(50002, '消息上下文数据格式异常', 500) from exc
            request_context.pop('requestMessageId', None)
            scope_value = request_context.pop('dataScope', None)
            access_scope = int(scope_value) if scope_value is not None else data_scope(conn, int(source_data['user_id']))
            history_rows = conn.execute(text('''
                SELECT role, content FROM biz_agent_message
                WHERE session_id=:session_id AND message_id<>:message_id AND status='completed'
                ORDER BY created_at DESC, message_id DESC LIMIT 10'''),
                {'session_id': source_data['session_id'], 'message_id': message_id}).all()
        history = [row_dict(row) for row in reversed(history_rows)]
        loop = BuildingSafetyAgentLoop(db, int(source_data['user_id']), source_data['project_id'], request_context, history, access_scope)
        run = loop.run(question)
        answer, intent, tool_trace = run.answer, run.intent_type, run.tool_trace
        completed, status_value, error, error_code = now(), 'completed', None, None
    except AgentLoopError as exc:
        logger.error('agent loop failed message_id=%s code=%s error=%s', message_id, exc.code, exc.message)
        answer, completed, status_value, error, error_code = '', now(), 'failed', exc.message, exc.code
    except AppError as exc:
        logger.error('answer generation failed message_id=%s code=%s error=%s', message_id, exc.code, exc.message)
        answer, completed, status_value, error, error_code = '', now(), 'failed', exc.message, exc.code
    except AccessError as exc:
        logger.error('answer authorization failed message_id=%s code=%s error=%s', message_id, exc.code, exc.message)
        answer, completed, status_value, error, error_code = '', now(), 'failed', exc.message, exc.code
    duration = int((time.monotonic() - started) * 1000)
    try:
        with db() as conn:
            row = conn.execute(text('SELECT * FROM biz_agent_message WHERE message_id=:id'), {'id': message_id}).first()
            if row is None:
                raise AppError(40402, '消息不存在', 404)
            msg = row_dict(row)
            if msg['status'] == 'generating':
                try:
                    assistant_context = json.loads(msg['context_json'] or '{}')
                except (TypeError, ValueError):
                    assistant_context = {'requestMessageId': None}
                assistant_context['agentLoop'] = {'toolCalls': tool_trace}
                assistant_context['errorCode'] = error_code
                conn.execute(text('''
                    UPDATE biz_agent_message
                    SET content=:content, status=:status, intent_type=:intent,
                        response_duration_ms=:duration, error_message=:error,
                        context_json=:context_json, completed_at=:completed
                    WHERE message_id=:id'''),
                    {'content': answer, 'status': status_value, 'intent': intent, 'duration': duration,
                     'error': error, 'context_json': json.dumps(assistant_context, ensure_ascii=False),
                     'completed': completed, 'id': message_id})
                if status_value == 'completed':
                    conn.execute(text('''
                        INSERT INTO biz_chat_record
                        (session_id,user_id,project_id,question,answer,question_time,answer_time,
                         response_duration_ms,intent_type,context_json,is_success,created_at)
                        VALUES (:sid,:uid,:pid,:question,:answer,:qt,:at,:duration,:intent,:context,1,:at)'''),
                        {'sid': msg['session_id'], 'uid': msg['user_id'], 'pid': msg['project_id'],
                         'question': question, 'answer': answer, 'qt': msg['created_at'], 'at': completed,
                         'duration': duration, 'intent': intent,
                         'context': json.dumps(assistant_context, ensure_ascii=False)})
                conn.execute(text('UPDATE biz_agent_session SET updated_at=:updated WHERE session_id=:sid'),
                             {'updated': completed, 'sid': msg['session_id']})
        logger.info('agent answer persisted message_id=%s status=%s duration_ms=%s tools=%s',
                    message_id, status_value, duration, len(tool_trace))
        return {'messageId': message_id, 'content': answer, 'status': status_value,
                'intentType': intent, 'responseDurationMs': duration, 'errorMessage': error,
                'errorCode': error_code, 'toolTrace': tool_trace}
    except AppError:
        raise
    except (OperationalError, DBAPIError) as exc:
        logger.exception('answer persistence failed message_id=%s', message_id)
        raise AppError(50301, '数据库连接或执行失败', 503) from exc

app = FastAPI(title='建筑安全智能监控平台 - 13. 对话智能体', version='1.0.0')

app.add_middleware(
    CORSMiddleware,
    allow_origins=['https://app.apifox.com', 'http://app.apifox.com'],
    allow_credentials=True,
    allow_methods=['*'],
    allow_headers=['*'],
)


@app.exception_handler(AppError)
async def app_error(_: Request, exc: AppError):
    return JSONResponse(status_code=exc.http_status, content={'code': exc.code, 'msg': exc.message, 'data': None})

@app.exception_handler(AccessError)
async def access_error(_: Request, exc: AccessError):
    return JSONResponse(status_code=exc.http_status, content={'code': exc.code, 'msg': exc.message, 'data': None})



@app.exception_handler(RequestValidationError)
async def validation_error(_: Request, exc: RequestValidationError):
    return JSONResponse(status_code=400, content={'code': 40001, 'msg': '请求参数错误', 'data': None})


def optional_positive_int(value: str | None, name: str, default: int | None, maximum: int | None = None) -> int | None:
    if value is None or not value.strip():
        return default
    try:
        parsed = int(value)
    except ValueError as exc:
        raise AppError(40001, f'{name}必须是整数', 400) from exc
    if parsed < 1 or (maximum is not None and parsed > maximum):
        raise AppError(40001, f'{name}超出允许范围', 400)
    return parsed


@app.get('/api/agent/sessions')
def list_sessions(projectId: str | None = Query(default=None), pageNo: str | None = Query(default=None), pageSize: str | None = Query(default=None), user_id: int = Depends(current_user)):
    project_id = optional_positive_int(projectId, 'projectId', None)
    page_no = optional_positive_int(pageNo, 'pageNo', 1)
    page_size = optional_positive_int(pageSize, 'pageSize', 20, 100)
    current_user_id = int(user_id)
    where, params = 's.user_id=:current_user_id', {'current_user_id': current_user_id, 'limit': page_size, 'offset': (page_no - 1) * page_size}
    if project_id: where += ' AND s.project_id=:pid'; params['pid'] = project_id
    with db() as conn:
        authorize_project(conn, current_user_id, project_id)
        total = conn.execute(text('SELECT COUNT(*) FROM biz_agent_session s WHERE ' + where), params).scalar_one()
        rows = conn.execute(text('SELECT s.*, m.content AS last_message_preview, m.completed_at AS last_message_at FROM biz_agent_session s LEFT JOIN biz_agent_message m ON m.message_id=(SELECT message_id FROM biz_agent_message WHERE session_id=s.session_id AND role=\'assistant\' ORDER BY created_at DESC LIMIT 1) WHERE ' + where + ' ORDER BY s.updated_at DESC LIMIT :limit OFFSET :offset'), params).all()
    return result({'records': [session_view(row_dict(x)) for x in rows], 'total': total, 'pageNo': page_no, 'pageSize': page_size})


@app.post('/api/agent/sessions', status_code=status.HTTP_201_CREATED)
def create_session(payload: CreateSession | None = None, user_id: int = Depends(current_user)):
    payload = payload or CreateSession()
    session_id, created = 'sess_' + uuid.uuid4().hex, now()
    with db() as conn:
        authorize_project(conn, int(user_id), payload.project_id)
        conn.execute(text('INSERT INTO biz_agent_session (session_id,user_id,project_id,title,created_at,updated_at) VALUES (:sid,:uid,:pid,:title,:created,:updated)'), {'sid': session_id, 'uid': user_id, 'pid': payload.project_id, 'title': payload.title or '新对话', 'created': created, 'updated': created})
    return result({'sessionId': session_id, 'title': payload.title or '新对话', 'projectId': payload.project_id, 'lastMessagePreview': None, 'lastMessageAt': None, 'createdAt': public_time(created)})


@app.get('/api/agent/sessions/{session_id}/messages')
def list_messages(session_id: str, pageNo: str | None = Query(default=None), pageSize: str | None = Query(default=None), user_id: int = Depends(current_user)):
    page_no = optional_positive_int(pageNo, 'pageNo', 1)
    page_size = optional_positive_int(pageSize, 'pageSize', 20, 100)
    with db() as conn:
        require_session(conn, session_id, user_id)
        total = conn.execute(text('SELECT COUNT(*) FROM biz_agent_message WHERE session_id=:sid'), {'sid': session_id}).scalar_one()
        rows = conn.execute(text('SELECT * FROM biz_agent_message WHERE session_id=:sid ORDER BY created_at ASC LIMIT :limit OFFSET :offset'), {'sid': session_id, 'limit': page_size, 'offset': (page_no - 1) * page_size}).all()
    records = []
    for row in rows:
        x = row_dict(row); records.append({'messageId': x['message_id'], 'role': x['role'], 'content': x['content'], 'status': x['status'], 'intentType': x['intent_type'], 'context': json.loads(x['context_json']) if x['context_json'] else None, 'responseDurationMs': x['response_duration_ms'], 'errorMessage': x['error_message'], 'createdAt': public_time(x['created_at']), 'completedAt': public_time(x['completed_at'])})
    return result({'records': records, 'total': total, 'pageNo': page_no, 'pageSize': page_size})


@app.post('/api/agent/sessions/{session_id}/messages', status_code=status.HTTP_202_ACCEPTED)
def send_message(session_id: str, payload: SendMessage, user_id: int = Depends(current_user)):
    created, user_message_id, assistant_message_id = now(), 'msg_' + uuid.uuid4().hex, 'msg_' + uuid.uuid4().hex
    with db() as conn:
        session = require_session(conn, session_id, user_id)
        if conn.execute(text("SELECT 1 FROM biz_agent_message WHERE session_id=:sid AND role='assistant' AND status='generating' LIMIT 1"), {'sid': session_id}).first(): raise AppError(40901, '当前会话已有生成中的响应，请等待完成后再发送消息', 409)
        project_id = payload.project_id or session['project_id']
        access_scope = authorize_project(conn, int(user_id), project_id)
        context = json.dumps(payload.context, ensure_ascii=False) if payload.context else None
        assistant_context = dict(payload.context or {})
        assistant_context['requestMessageId'] = user_message_id
        assistant_context['dataScope'] = access_scope
        assistant_context_json = json.dumps(assistant_context, ensure_ascii=False)
        for mid, role, content, state, message_context in ((user_message_id, 'user', payload.content, 'completed', context), (assistant_message_id, 'assistant', '', 'generating', assistant_context_json)):
            conn.execute(text('INSERT INTO biz_agent_message (message_id,session_id,user_id,role,content,status,project_id,context_json,created_at,completed_at) VALUES (:id,:sid,:uid,:role,:content,:status,:pid,:context,:created,:completed)'), {'id': mid, 'sid': session_id, 'uid': user_id, 'role': role, 'content': content, 'status': state, 'pid': project_id, 'context': message_context, 'created': created, 'completed': created if role == 'user' else None})
        conn.execute(text('UPDATE biz_agent_session SET updated_at=:updated WHERE session_id=:sid'), {'updated': created, 'sid': session_id})
    return result({'userMessageId': user_message_id, 'assistantMessageId': assistant_message_id, 'status': 'generating', 'streamUrl': f'/api/agent/sessions/{session_id}/responses/{assistant_message_id}/stream'}, 'accepted')


@app.get('/api/agent/sessions/{session_id}/responses/{message_id}/stream')
def stream_response(session_id: str, message_id: str, user_id: int = Depends(current_user), last_event_id: str | None = Header(default=None, alias='Last-Event-ID')):
    started = time.monotonic()
    logger.info('SSE response requested session_id=%s message_id=%s user_id=%s', session_id, message_id, user_id)
    with db() as conn:
        require_session(conn, session_id, user_id)
        row = conn.execute(text("SELECT * FROM biz_agent_message WHERE message_id=:id AND session_id=:sid AND role='assistant'"), {'id': message_id, 'sid': session_id}).first()
        if not row:
            raise AppError(40402, '消息不存在或不是智能体响应', 404)
        msg = row_dict(row)
        request_message_id = json.loads(msg['context_json'] or '{}').get('requestMessageId')
        question = conn.execute(text("SELECT content FROM biz_agent_message WHERE message_id=:id AND session_id=:sid AND role='user'"), {'id': request_message_id, 'sid': session_id}).scalar_one_or_none()
        if question is None:
            raise AppError(40402, '当前响应关联的用户消息不存在', 404)

    def events() -> Generator[str, None, None]:
        yield ': ping\n\n'
        resume = (last_event_id or '').strip()
        resume_kind, resume_index = '', 0
        if resume.lower() == 'done':
            resume_kind = 'done'
        elif resume.startswith(('tool-', 'delta-')):
            prefix, _, value = resume.partition('-')
            if value.isdigit():
                resume_kind, resume_index = prefix, int(value)
        elif resume.isdigit():
            resume_kind, resume_index = 'delta', int(resume)

        def skipped(kind: str, index: int) -> bool:
            if resume_kind == 'done':
                return True
            if resume_kind == 'tool':
                return kind == 'tool' and index <= resume_index
            if resume_kind == 'delta':
                return kind == 'tool' or (kind == 'delta' and index <= resume_index)
            return False
        with response_lock(message_id):
            with db() as conn:
                current_row = conn.execute(text("SELECT * FROM biz_agent_message WHERE message_id=:id AND session_id=:sid AND role='assistant'"), {'id': message_id, 'sid': session_id}).first()
            if current_row is None:
                yield 'event: error\ndata: ' + json.dumps({'messageId': message_id, 'code': 40402, 'message': '\u6d88\u606f\u4e0d\u5b58\u5728\u6216\u4e0d\u662f\u667a\u80fd\u4f53\u54cd\u5e94'}, ensure_ascii=False) + '\n\n'
                return
            msg = row_dict(current_row)
            if msg['status'] == 'generating':
                final = persist_answer(message_id, question)
            else:
                context = json.loads(msg['context_json'] or '{}')
                final = {'messageId': message_id, 'content': msg['content'], 'status': msg['status'], 'intentType': msg['intent_type'], 'responseDurationMs': msg['response_duration_ms'], 'errorMessage': msg['error_message'], 'errorCode': context.get('errorCode'), 'toolTrace': (context.get('agentLoop') or {}).get('toolCalls', [])}

        if final['status'] == 'failed':
            logger.error('SSE response failed session_id=%s message_id=%s code=%s error=%s', session_id, message_id, final.get('errorCode'), final['errorMessage'])
            yield 'event: error\ndata: ' + json.dumps({'messageId': message_id, 'code': final.get('errorCode') or 50202, 'message': final['errorMessage']}, ensure_ascii=False) + '\n\n'
            return
        for tool_index, trace in enumerate(final.get('toolTrace') or [], 1):
            logger.info('SSE tool session_id=%s message_id=%s tool=%s status=%s', session_id, message_id, trace.get('name'), trace.get('status'))
            if not skipped('tool', tool_index):
                yield f'id: tool-{tool_index}\nevent: tool\ndata: ' + json.dumps({'messageId': message_id, 'tool': trace}, ensure_ascii=False) + '\n\n'

        for index, chunk in enumerate((final['content'][i:i+32] for i in range(0, len(final['content']), 32)), 1):
            logger.info('SSE delta session_id=%s message_id=%s sequence=%s chunk_length=%s', session_id, message_id, index, len(chunk))
            if not skipped('delta', index):
                yield f'id: delta-{index}\nevent: delta\ndata: ' + json.dumps({'messageId': message_id, 'delta': chunk}, ensure_ascii=False) + '\n\n'
            if index % 8 == 0:
                yield ': ping\n\n'
        logger.info('SSE response completed session_id=%s message_id=%s duration_ms=%s', session_id, message_id, int((time.monotonic() - started) * 1000))
        if resume_kind != 'done':
            yield 'id: done\nevent: done\ndata: ' + json.dumps({'messageId': message_id, 'status': 'completed', 'intentType': final['intentType'], 'responseDurationMs': final['responseDurationMs'], 'toolCalls': len(final.get('toolTrace') or [])}, ensure_ascii=False) + '\n\n'
    return StreamingResponse(events(), media_type='text/event-stream', headers={'Cache-Control': 'no-cache', 'X-Accel-Buffering': 'no'})
