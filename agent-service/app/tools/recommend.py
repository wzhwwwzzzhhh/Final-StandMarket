"""搭配推荐：基于类目的互补单品推荐（启用 OUTFIT_RULES / get_complementary）"""
from elasticsearch import Elasticsearch

from app.config import settings
from app.tools.outfit_rules import (
    OUTFIT_RULES,
    detect_category,
    complementary_categories,
    reason_for,
    build_es_category_query,
)

es = Elasticsearch(hosts=[settings.es_host])


def get_complementary(item_name: str, category_id: int = None) -> list[dict]:
    """根据某商品找搭配单品：优先按互补类目关键词，其次排除同 categoryId"""
    category = detect_category(item_name)
    if category:
        body = build_es_category_query(complementary_categories(category), size=3)
    else:
        must_not = []
        if category_id is not None:
            must_not.append({"term": {"categoryId": category_id}})
        body = {
            "query": {
                "bool": {
                    "must": [{"range": {"price": {"gte": 50, "lte": 500}}}],
                    "must_not": must_not,
                }
            },
            "size": 3,
        }
    try:
        resp = es.search(index="products", body=body)
        return [h["_source"] for h in resp["hits"]["hits"]]
    except Exception:
        return []


def recommend_outfit(query: str, size: int = 4) -> dict:
    """搭配推荐入口：识别主类目 -> 搜互补类目单品，返回
    {"products": [...], "main_category": str, "reason": str}
    """
    main_category = detect_category(query) or "上装"
    keywords = complementary_categories(main_category)
    body = build_es_category_query(keywords, size=size)
    try:
        resp = es.search(index="products", body=body)
        products = [h["_source"] for h in resp["hits"]["hits"]]
    except Exception:
        products = []
    return {
        "products": products,
        "main_category": main_category,
        "reason": reason_for(main_category, "搭配单品"),
    }
