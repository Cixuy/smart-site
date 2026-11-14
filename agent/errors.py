class AgentLoopError(Exception):
    """Expected errors raised while an Agent Loop is planning or using a tool."""

    def __init__(self, code: int, message: str):
        self.code = code
        self.message = message
        super().__init__(message)
