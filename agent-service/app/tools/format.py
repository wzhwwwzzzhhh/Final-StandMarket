"""订单 / 回复的格式化工具"""

ORDER_STATUS = {
    1: "待付款",
    2: "待发货",
    3: "已发货",
    4: "已完成",
    5: "已取消",
    6: "退款",
}

LOGISTICS_FALLBACK = "物流信息暂未开放，请稍后在订单页查看详情。"


def format_orders(orders: list) -> str:
    """将订单列表格式化为可读文本，空列表返回友好提示"""
    if not orders:
        return "你目前还没有订单。"
    lines = ["你的订单："]
    for o in orders[:3]:
        number = o.get("number", "")
        status = ORDER_STATUS.get(o.get("status"), "未知")
        amount = o.get("amount")
        order_time = str(o.get("orderTime", ""))[:16]
        line = f"· 订单 {number}｜{status}"
        if amount is not None:
            line += f"｜¥{amount}"
        if order_time:
            line += f"｜{order_time}"
        items = o.get("items") or []
        if items:
            names = "、".join(f"{it.get('name', '')}×{it.get('number', 1)}" for it in items[:2])
            line += f"｜{names}"
        lines.append(line)
    return "\n".join(lines)


def format_order_summary(order: dict) -> str:
    """单条订单/查询结果的摘要（含错误兜底）"""
    if isinstance(order, dict) and order.get("error"):
        return "订单查询失败，请稍后再试。"
    if isinstance(order, dict) and order.get("trackingNumber"):
        return (f"订单 {order.get('number', '')} 已发货：{order.get('trackingCompany', '')} "
                f"单号 {order.get('trackingNumber', '')}，预计 {order.get('deliveryTime', '')} 送达。")
    if isinstance(order, dict) and order.get("number"):
        return format_orders([order])
    return LOGISTICS_FALLBACK
