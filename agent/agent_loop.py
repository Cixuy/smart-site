import json
import logging
import os
import re
import time
from dataclasses import dataclass
from typing import Any, Callable, ContextManager, Sequence

from langchain_core.messages import AIMessage, BaseMessage, HumanMessage, SystemMessage, ToolMessage
from langchain_core.tools import StructuredTool

from agent.errors import AgentLoopError
from agent.model import create_chat_model
from agent.tools import BuildingSafetyTools

logger = logging.getLogger('dialogue_agent')


@dataclass
class AgentRun:
    answer: str
    intent_type: str
    tool_trace: list[dict[str, Any]]


def classify_intent(question: str) -> str:
    if re.search('告警|报警', question):
        return 'alarm_analysis'
    if re.search('塔吊|升降机|设备.*健康|检修', question):
        return 'device_diagnosis'
    if re.search('建议|本周|本月|报告', question):
        return 'suggestion'
    return 'safety_query'


class BuildingSafetyAgentLoop:
    """Bounded LangChain tool-calling loop for read-only site safety analysis."""

    def __init__(self, db_factory: Callable[[], ContextManager[Any]], user_id: int,
                 project_id: int | None, request_context: dict[str, Any] | None,
                 chat_history: Sequence[dict[str, Any]] | None = None,
                 data_scope: int = 1):
        self.intent_type = classify_intent('')
        self.db_factory = db_factory
        self.user_id = user_id
        self.project_id = project_id
        self.request_context = request_context or {}
        self.chat_history = list(chat_history or [])
        self.trace: list[dict[str, Any]] = []
        self.safety_tools = BuildingSafetyTools(db_factory, user_id, project_id, self.request_context, data_scope)

    def run(self, question: str) -> AgentRun:
        self.intent_type = classify_intent(question)
        if os.getenv('AGENT_MOCK_MODEL', 'false').lower() == 'true':
            return self._run_mock(question)
        return self._run_langchain(question)

    def _run_tool(self, name: str, arguments: dict[str, Any]) -> str:
        started = time.monotonic()
        trace: dict[str, Any] = {'name': name, 'arguments': arguments, 'status': 'running'}
        self.trace.append(trace)
        logger.info('agent tool started tool=%s user_id=%s project_id=%s arguments=%s',
                    name, self.user_id, self.project_id, arguments)
        try:
            handler = {
                'query_safety_overview': self.safety_tools.safety_overview,
                'query_alarm_analysis': self.safety_tools.alarm_analysis,
                'query_device_health': self.safety_tools.device_health,
                'query_environment_latest': self.safety_tools.environment_latest,
            }.get(name)
            if handler is None:
                raise AgentLoopError(40002, '模型请求了未授权的业务工具')
            result = handler(**arguments)
            trace['status'] = 'completed'
            trace['durationMs'] = int((time.monotonic() - started) * 1000)
            trace['resultSummary'] = self._summary(result)
            logger.info('agent tool completed tool=%s duration_ms=%s', name, trace['durationMs'])
            return BuildingSafetyTools.stringify(result)
        except AgentLoopError as exc:
            trace['status'] = 'failed'
            trace['durationMs'] = int((time.monotonic() - started) * 1000)
            trace['error'] = exc.message
            logger.error('agent tool rejected tool=%s code=%s', name, exc.code)
            raise
        except Exception as exc:
            trace['status'] = 'failed'
            trace['durationMs'] = int((time.monotonic() - started) * 1000)
            trace['error'] = '业务数据查询失败'
            logger.exception('agent tool failed tool=%s user_id=%s project_id=%s', name, self.user_id, self.project_id)
            raise AgentLoopError(50304, '业务数据查询失败') from exc

    def _langchain_tools(self) -> list[StructuredTool]:
        def safety_overview() -> str:
            """查询当前项目今日告警、待处理告警和设备在线离线概况。"""
            return self._run_tool('query_safety_overview', {})

        def alarm_analysis(days: int = 1, device_keyword: str | None = None) -> str:
            """查询当前项目指定时间范围内的告警级别与类型分布。days 范围为 1 至 30。"""
            return self._run_tool('query_alarm_analysis', {'days': days, 'device_keyword': device_keyword})

        def device_health(device_id: int | None = None, device_keyword: str | None = None,
                          days: int = 7) -> str:
            """查询设备台账、近期开警以及塔吊或升降机最新实时运行数据。"""
            return self._run_tool('query_device_health', {
                'device_id': device_id, 'device_keyword': device_keyword, 'days': days,
            })

        def environment_latest() -> str:
            """查询各环境监测点各指标的最新监测值与状态。"""
            return self._run_tool('query_environment_latest', {})

        return [
            StructuredTool.from_function(safety_overview, name='query_safety_overview'),
            StructuredTool.from_function(alarm_analysis, name='query_alarm_analysis'),
            StructuredTool.from_function(device_health, name='query_device_health'),
            StructuredTool.from_function(environment_latest, name='query_environment_latest'),
        ]

    def _run_langchain(self, question: str) -> AgentRun:
        try:
            model = create_chat_model()
            tools = self._langchain_tools()
            bound_model = model.bind_tools(tools)
            messages: list[BaseMessage] = [SystemMessage(content=(
                '你是建筑安全智能监控助手。对涉及事实、设备状态、告警、环境数据的问题，'
                '必须优先调用已提供的只读工具，且只能依据工具返回的数据回答。'
                '不得编造数值；数据为空时说明暂无数据。工具调用最多三轮。'
                '告警分析应调用告警和趋势工具，设备诊断应调用设备工具并说明健康等级，'
                '安全建议应综合安全态势、告警趋势和环境结果，输出周期、主要问题和管控清单。'
            ))]
            messages.extend(self._history_messages())
            messages.append(HumanMessage(content=question))
            max_steps = self._max_steps()
            for step in range(max_steps):
                response = bound_model.invoke(messages)
                if not response.tool_calls:
                    answer = str(response.content).strip()
                    if not answer:
                        raise AgentLoopError(50202, '模型未返回有效回答')
                    logger.info('agent loop completed steps=%s tools=%s', step + 1, len(self.trace))
                    return AgentRun(answer, self.intent_type, self.trace)
                messages.append(response)
                for call in response.tool_calls:
                    tool_name = str(call.get('name', ''))
                    arguments = call.get('args') or {}
                    output = self._run_tool(tool_name, arguments)
                    messages.append(ToolMessage(content=output, tool_call_id=str(call.get('id', tool_name))))
            messages.append(SystemMessage(content='工具调用轮次已达到上限。请仅根据已返回的数据给出简洁的最终回答，不要继续调用工具。'))
            final_response = model.invoke(messages)
            answer = str(final_response.content).strip()
            if not answer:
                raise AgentLoopError(50202, '模型在工具调用上限后未返回有效回答')
            logger.warning('agent loop reached maximum steps=%s tools=%s', max_steps, len(self.trace))
            return AgentRun(answer, self.intent_type, self.trace)
        except AgentLoopError:
            raise
        except Exception as exc:
            logger.exception('agent model invocation failed user_id=%s project_id=%s', self.user_id, self.project_id)
            raise AgentLoopError(50202, '模型服务调用失败') from exc

    def _run_mock(self, question: str) -> AgentRun:
        """Deterministic local mode that still executes the same business tools."""
        tool_plan = self._mock_plan(question)
        outputs: dict[str, Any] = {}
        for name, arguments in tool_plan:
            outputs[name] = json.loads(self._run_tool(name, arguments))
        answer = self._mock_answer(question, outputs)
        logger.info('mock agent loop completed tools=%s', len(self.trace))
        return AgentRun(answer, self.intent_type, self.trace)

    def _mock_plan(self, question: str) -> list[tuple[str, dict[str, Any]]]:
        device_keyword = self._device_keyword(question)
        days = self._period_days(question)
        if self.intent_type == 'alarm_analysis':
            plan = [('query_alarm_analysis', {'days': days, 'device_keyword': device_keyword})]
            if device_keyword or self.request_context.get('deviceId'):
                plan.insert(0, ('query_device_health', {
                    'device_id': self.request_context.get('deviceId'),
                    'device_keyword': device_keyword,
                    'days': days,
                }))
            return plan
        if self.intent_type == 'device_diagnosis':
            return [
                ('query_device_health', {
                    'device_id': self.request_context.get('deviceId'),
                    'device_keyword': device_keyword,
                    'days': 7,
                }),
                ('query_alarm_analysis', {'days': 7, 'device_keyword': device_keyword}),
            ]
        if self.intent_type == 'suggestion':
            return [
                ('query_safety_overview', {}),
                ('query_alarm_analysis', {'days': days, 'device_keyword': None}),
                ('query_environment_latest', {}),
            ]
        return [('query_safety_overview', {}), ('query_environment_latest', {})]

    def _mock_answer(self, question: str, outputs: dict[str, Any]) -> str:
        if self.intent_type == 'suggestion':
            overview = outputs.get('query_safety_overview', {})
            alarm = outputs.get('query_alarm_analysis', {})
            environment = outputs.get('query_environment_latest', {})
            summary = alarm.get('summary') or {}
            return (
                f"模拟 Agent Loop 回复：安全管理建议（{alarm.get('days', 7)} 天）。"
                f"总体告警 {summary.get('total', 0) or 0} 条，在线设备 {((overview.get('deviceSummary') or {}).get('online', 0) or 0)} 台。"
                f"告警高峰时段 {alarm.get('peakHour') if alarm.get('peakHour') is not None else '暂无'} 时，"
                f"环境监测记录 {len(environment.get('records') or [])} 条。"
                '建议重点关注高等级告警、设备波动和环境超标点，并安排复核。'
                f"问题：{question}"
            )
        if 'query_device_health' in outputs:
            device = outputs['query_device_health'].get('device') or {}
            alarms = device.get('recentAlarmSummary') or {}
            return (
                '模拟 Agent Loop 回复：已完成设备健康诊断。'
                f"设备 {device.get('device_name', '未知设备')} 健康等级为【{device.get('healthLevel', '未知')}】，"
                f"近 {outputs['query_device_health'].get('days', 7)} 天告警 {alarms.get('total', 0) or 0} 条，"
                f"最高级别 {alarms.get('max_level', 0) or 0}。问题：{question}"
            )
        if 'query_alarm_analysis' in outputs:
            summary = outputs['query_alarm_analysis'].get('summary') or {}
            return (
                '模拟 Agent Loop 回复：已完成告警原因分析。'
                f"近 {outputs['query_alarm_analysis'].get('days', 1)} 天共 {summary.get('total', 0) or 0} 条告警，"
                f"控制级 {summary.get('control', 0) or 0} 条、警报级 {summary.get('alarm', 0) or 0} 条、"
                f"预警级 {summary.get('warning', 0) or 0} 条，"
                f"高峰时段 {outputs['query_alarm_analysis'].get('peakHour') if outputs['query_alarm_analysis'].get('peakHour') is not None else '暂无'} 时。"
                f"问题：{question}"
            )
        overview = outputs.get('query_safety_overview', {})
        devices = overview.get('deviceSummary') or {}
        alarms = overview.get('alarmSummary') or {}
        return (
            '模拟 Agent Loop 回复：已完成安全态势与环境数据查询。'
            f"当前设备在线 {devices.get('online', 0) or 0} 台、离线 {devices.get('offline', 0) or 0} 台；"
            f"今日项目告警 {alarms.get('total', 0) or 0} 条。问题：{question}"
        )

    def _history_messages(self) -> list[BaseMessage]:
        messages: list[BaseMessage] = []
        for item in self.chat_history[-10:]:
            content = str(item.get('content') or '').strip()
            if not content:
                continue
            content = content[:2000]
            messages.append(HumanMessage(content=content) if item.get('role') == 'user' else AIMessage(content=content))
        return messages

    @staticmethod
    def _device_keyword(question: str) -> str | None:
        match = re.search(r'\d+号(?:塔吊|升降机)|(?:塔吊|升降机)\d+号', question)
        return match.group(0) if match else None
    @staticmethod
    def _period_days(question: str) -> int:
        if re.search('本月|月度', question):
            return 30
        match = re.search(r'(?:近|过去|最近)\s*(\d{1,2})\s*天', question)
        if match:
            return max(1, min(int(match.group(1)), 30))
        return 7 if re.search('本周|周报|周', question) else 1

    @staticmethod
    def _summary(result: dict[str, Any]) -> str:
        if 'summary' in result:
            return '已获得告警统计结果'
        if 'device' in result:
            return '已获得设备健康数据'
        if 'deviceSummary' in result:
            return '已获得安全态势概览'
        return '已获得环境最新数据'

    @staticmethod
    def _max_steps() -> int:
        try:
            return max(1, min(int(os.getenv('AGENT_LOOP_MAX_STEPS', '3')), 5))
        except ValueError:
            return 3
