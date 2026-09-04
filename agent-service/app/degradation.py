REASON_ORDER = (
    "REDIS_UNAVAILABLE",
    "ELASTICSEARCH_UNAVAILABLE",
    "LLM_UNAVAILABLE",
    "JAVA_TOOL_UNAVAILABLE",
    "PYTHON_AGENT_UNAVAILABLE",
)


def normalize_reasons(reasons) -> list[str]:
    observed = set(reasons or [])
    return [reason for reason in REASON_ORDER if reason in observed]


def add_reason(state: dict, reason: str) -> None:
    state["degradationReasons"] = normalize_reasons(
        [*(state.get("degradationReasons") or []), reason]
    )
