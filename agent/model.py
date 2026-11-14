import os

from langchain_openai import ChatOpenAI

from agent.errors import AgentLoopError


def create_chat_model() -> ChatOpenAI:
    """Create the DashScope OpenAI-compatible model used by the production loop."""
    api_key = os.getenv('DASHSCOPE_API_KEY', '').strip()
    if not api_key:
        raise AgentLoopError(50303, '模型服务未配置 DASHSCOPE_API_KEY')
    return ChatOpenAI(
        model=os.getenv('DASHSCOPE_MODEL', 'tongyi-xiaomi-analysis-flash'),
        api_key=api_key,
        base_url=os.getenv('DASHSCOPE_BASE_URL', 'https://dashscope.aliyuncs.com/compatible-mode/v1').rstrip('/'),
        temperature=0,
        timeout=60,
    )
