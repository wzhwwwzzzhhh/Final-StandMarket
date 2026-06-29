from elasticsearch import Elasticsearch
from app.config import settings

es = Elasticsearch(hosts=[settings.es_host])


def search_products(query: str, size: int = 5) -> list[dict]:
    """ES 多字段搜索商品，返回列表中包含 id/name/price/image/description/score"""
    body = {
        "query": {
            "multi_match": {
                "query": query,
                "fields": ["name^3", "description^2", "tag"],
                "fuzziness": "AUTO",
                "type": "best_fields"
            }
        },
        "size": size
    }
    resp = es.search(index="products", body=body)
    hits = resp["hits"]["hits"]
    results = []
    for h in hits:
        src = h["_source"]
        src["score"] = h["_score"]
        results.append(src)
    return results
