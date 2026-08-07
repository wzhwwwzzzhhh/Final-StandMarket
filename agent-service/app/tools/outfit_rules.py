"""搭配规则 + 规则兜底回复"""

# 类目关键词 -> 归一化类目
CATEGORY_KEYWORDS = {
    "连衣裙": ["连衣裙"],
    "下装": ["牛仔裤", "休闲裤", "阔腿裤", "短裤", "半身裙", "长裤", "长裙", "短裙", "裤", "裙"],
    "T恤": ["T恤", "t恤", "tee"],
    "衬衫": ["衬衫", "衬衣"],
    "毛织": ["毛衣", "针织", "毛织", "卫衣"],
    "外套": ["外套", "开衫", "夹克", "风衣", "大衣", "牛仔衣"],
    "上装": ["上衣", "打底", "吊带", "背心"],
}

# 类目 -> 互补搭配类目（搭配理由用）
OUTFIT_RULES = {
    "上衣": ["下装", "裤", "裙", "牛仔裤"],
    "T恤": ["牛仔裤", "休闲裤", "短裤", "半身裙"],
    "衬衫": ["西裤", "牛仔裤", "半身裙", "阔腿裤"],
    "连衣裙": ["开衫", "外套", "腰带", "凉鞋"],
    "外套": ["连衣裙", "T恤", "衬衫", "牛仔裤"],
    "毛织": ["牛仔裤", "休闲裤", "半身裙"],
    "下装": ["上衣", "T恤", "衬衫", "毛织"],
    "裤": ["T恤", "衬衫", "毛织", "卫衣"],
    "裙": ["衬衫", "毛织", "T恤", "开衫"],
}

# 归一化类目 -> 互补归一化类目（用于 ES 关键词搜索）
COMPLEMENTARY_MAP = {
    "T恤": ["下装", "裤", "裙", "牛仔裤", "半身裙", "短裤"],
    "衬衫": ["下装", "裤", "裙", "牛仔裤", "西裤", "阔腿裤", "半身裙"],
    "毛织": ["下装", "裤", "裙", "牛仔裤", "半身裙", "休闲裤"],
    "外套": ["连衣裙", "T恤", "衬衫", "牛仔裤", "毛织"],
    "连衣裙": ["外套", "开衫", "T恤", "衬衫"],
    "下装": ["T恤", "衬衫", "毛织", "卫衣", "上装", "上衣"],
    "上装": ["下装", "裤", "裙", "牛仔裤"],
    "裙": ["衬衫", "毛织", "T恤", "开衫", "上装"],
    "裤": ["T恤", "衬衫", "毛织", "卫衣", "上装"],
}

# 搭配理由模板
REASON_TEMPLATES = {
    "T恤": "这件{main}休闲百搭，搭配{comp}既能日常通勤也适合出街，风格很和谐。",
    "衬衫": "这件{main}偏简约干练，和{comp}搭配显得利落又有层次感。",
    "毛织": "这件{main}柔软保暖，配{comp}在秋冬既舒适又显气质。",
    "外套": "这件{main}版型挺括，叠穿{comp}可盐可甜，层次感十足。",
    "连衣裙": "这条{main}优雅显气质，外搭{comp}早晚温差也从容应对。",
    "下装": "这件{comp}与{main}是经典组合，上下呼应很显比例。",
    "裙": "这件{comp}搭配{main}温柔又有设计感，很适合日常出街。",
    "裤": "这件{comp}配上{main}简约随性，怎么搭都不出错。",
}


def detect_category(text: str) -> str:
    """从文本中识别归一化类目，返回 OUTFIT_RULES 中的 key 或空串"""
    if not text:
        return ""
    for category, keywords in CATEGORY_KEYWORDS.items():
        for kw in keywords:
            if kw.lower() in text.lower():
                return category
    return ""


def complementary_categories(category: str) -> list:
    """返回某个类目的互补类目关键词列表（用于搜索）"""
    if category in COMPLEMENTARY_MAP:
        return COMPLEMENTARY_MAP[category]
    return ["上装", "下装"]


def reason_for(main_category: str, comp_name: str) -> str:
    """生成搭配理由"""
    template = REASON_TEMPLATES.get(main_category)
    if not template:
        return f"这件{main_category}和{comp_name}是很百搭的组合，推荐搭配购买。"
    return template.format(main=main_category, comp=comp_name)


def build_es_category_query(keywords: list, size: int = 3) -> dict:
    """构建按类目关键词检索的 ES 查询体（多字段 best_fields + 拼音）"""
    should = []
    for kw in keywords:
        should.append({
            "multi_match": {
                "query": kw,
                "fields": ["name^3", "description"],
                "type": "best_fields",
                "analyzer": "ik_smart",
            }
        })
        should.append({
            "multi_match": {
                "query": kw,
                "fields": ["name.pinyin"],
                "analyzer": "pinyin_analyzer",
            }
        })
    return {
        "query": {"bool": {"should": should, "minimum_should_match": 1}},
        "size": size,
        "sort": ["_score"],
    }


# ===================== 规则兜底回复 =====================

def rule_based_reply(intent: str, search_total: int = 0, order_text: str = "", size_text: str = "",
                     product_names: list = None, reason: str = "") -> str:
    """LLM 不可用时，基于意图和数据生成规则回复，保证 /chat 永不 500"""
    product_names = product_names or []
    if intent == "order":
        return order_text or "已为你查询订单，暂未获取到订单信息。"
    if intent == "size":
        return size_text or "请告诉我你的身高和体重，我来帮你推荐合适的尺码。"
    if intent == "recommend":
        if product_names:
            joined = "、".join(product_names[:3])
            return f"为你推荐这套搭配：{joined}。{reason}".strip()
        return "搭配推荐暂时没有合适的单品，稍后再试试看吧。"
    if intent == "search":
        if search_total > 0 and product_names:
            return f"为你找到 {search_total} 件相关商品：{('、'.join(product_names[:3]))}，点击卡片可以查看详情。"
        if search_total > 0:
            return f"为你找到 {search_total} 件相关商品，点击卡片查看详情。"
        return "抱歉，暂时没有找到相关商品，换个关键词试试吧。"
    return "我是 AI 导购小衣，可以帮你推荐商品、搭配穿搭、查询订单和推荐尺码，试试看吧。"
