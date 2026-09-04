from elasticsearch import Elasticsearch
from app.config import settings

es = Elasticsearch(hosts=[settings.es_host], request_timeout=5)


def search_products(
    query: str,
    size: int = 5,
    category_id: int = None,
    min_price: float = None,
    max_price: float = None,
    tag: str = None,
    sort_by: str = "_score",
    page: int = 1,
) -> dict:
    """
    ES 增强搜索商品，支持 IK 分词 + 拼音 + 筛选 + 高亮 + 排序

    Args:
        query: 搜索关键词
        size: 每页数量
        category_id: 分类ID筛选
        min_price: 最低价格
        max_price: 最高价格
        tag: 标签筛选（如"热卖"、"新品"）
        sort_by: 排序方式 (_score, price_asc, price_desc, sales_desc)
        page: 页码

    Returns:
        {
            "hits": [ { id/name/price/image/description/score/highlight } ],
            "total": int,
            "page": int,
            "size": int
        }
    """
    # 构建 bool 查询
    must_clauses = []
    filter_clauses = []

    if query:
        # 关键词搜索：IK 分词 + 拼音双通道
        should_clauses = [
            {"multi_match": {
                "query": query,
                "fields": ["name^3", "description"],
                "type": "best_fields",
                "analyzer": "ik_smart"
            }},
            {"multi_match": {
                "query": query,
                "fields": ["name.pinyin"],
                "analyzer": "pinyin_analyzer"
            }},
        ]
        must_clauses.append({"bool": {"should": should_clauses}})

    # 筛选条件（仅在提供值时添加）
    if category_id is not None:
        filter_clauses.append({"term": {"categoryId": category_id}})
    if min_price is not None or max_price is not None:
        range_clause = {}
        if min_price is not None:
            range_clause["gte"] = min_price
        if max_price is not None:
            range_clause["lte"] = max_price
        filter_clauses.append({"range": {"price": range_clause}})
    if tag is not None:
        filter_clauses.append({"term": {"tag": tag}})

    # 组装 bool query
    bool_query = {}
    if must_clauses:
        bool_query["must"] = must_clauses
    if filter_clauses:
        bool_query["filter"] = filter_clauses

    body = {"query": {"bool": bool_query}}

    # 高亮
    body["highlight"] = {
        "fields": {
            "name": {"pre_tags": ["<em>"], "post_tags": ["</em>"]},
            "description": {"pre_tags": ["<em>"], "post_tags": ["</em>"]},
        }
    }

    # 排序
    sort_map = {
        "_score": ["_score"],
        "price_asc": [{"price": {"order": "asc"}}, "_score"],
        "price_desc": [{"price": {"order": "desc"}}, "_score"],
        "sales_desc": [{"sales": {"order": "desc"}}, "_score"],
    }
    body["sort"] = sort_map.get(sort_by, ["_score"])

    # 分页
    body["from"] = (page - 1) * size
    body["size"] = size

    try:
        resp = es.search(index="products", body=body)
        hits_container = resp["hits"]
        hits = hits_container["hits"]
        if not isinstance(hits, list):
            raise ValueError("invalid Elasticsearch hits")
        total_value = hits_container.get("total", 0)
        total = total_value.get("value", 0) if isinstance(total_value, dict) else total_value
        if not isinstance(total, int) or total < 0:
            raise ValueError("invalid Elasticsearch total")

        results = []
        for h in hits:
            if not isinstance(h, dict) or not isinstance(h.get("_source"), dict):
                continue
            src = dict(h["_source"])
            highlight = h.get("highlight", {})
            if not isinstance(highlight, dict):
                continue
            src["highlight"] = {
                "name": highlight.get("name", [src.get("name", "")]),
                "description": highlight.get("description", [src.get("description", "")]),
            }
            src["score"] = h.get("_score")
            results.append(src)
    except Exception:
        return {"hits": [], "total": 0, "page": page, "size": size, "error": True}

    return {
        "hits": results,
        "total": total,
        "page": page,
        "size": size,
    }
