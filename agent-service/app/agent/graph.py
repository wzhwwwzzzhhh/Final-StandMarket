from langgraph.graph import StateGraph, END
from app.agent.nodes import (
    recognize_intent,
    search_product_node,
    recommend_node,
    order_node,
    size_node,
    generate_reply,
)


def create_initial_state(message: str, user_id: int, session_id: str, history: list = None,
                         user_authorization: str = "", slots: dict = None,
                         degradation_reasons: list = None) -> dict:
    return {
        "message": message,
        "userId": user_id,
        "sessionId": session_id,
        "history": history or [],
        "userAuthorization": user_authorization,
        "slots": slots or {},
        "intent": "",
        "search_results": [],
        "search_total": 0,
        "recommendations": [],
        "recommend_category": "",
        "recommend_reason": "",
        "order_info": {},
        "size_recommend": "",
        "size_garment": "",
        "size_waiting": False,
        "reply": "",
        "degradationReasons": list(degradation_reasons or []),
    }


def router(state: dict) -> str:
    """意图路由：根据 intent 选择下一节点"""
    intent_map = {
        "search": "search_product",
        "recommend": "recommend",
        "order": "order_query",
        "size": "size_recommend",
        "chat": "chat_reply",
    }
    return intent_map.get(state["intent"], "chat_reply")


def build_graph() -> StateGraph:
    workflow = StateGraph(dict)

    workflow.add_node("recognize_intent", recognize_intent)
    workflow.add_node("search_product", search_product_node)
    workflow.add_node("recommend", recommend_node)
    workflow.add_node("order_query", order_node)
    workflow.add_node("size_recommend", size_node)
    workflow.add_node("generate_reply", generate_reply)

    workflow.set_entry_point("recognize_intent")

    workflow.add_conditional_edges("recognize_intent", router, {
        "search_product": "search_product",
        "recommend": "recommend",
        "order_query": "order_query",
        "size_recommend": "size_recommend",
        "chat_reply": "generate_reply",
    })

    workflow.add_edge("search_product", "generate_reply")
    workflow.add_edge("recommend", "generate_reply")
    workflow.add_edge("order_query", "generate_reply")
    workflow.add_edge("size_recommend", "generate_reply")

    workflow.add_edge("generate_reply", END)

    return workflow.compile()


agent_graph = build_graph()
