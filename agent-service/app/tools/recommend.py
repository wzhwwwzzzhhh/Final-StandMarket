from elasticsearch import Elasticsearch
from app.config import settings

es = Elasticsearch(hosts=[settings.es_host])

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


def recommend_outfit(query: str, size: int = 4) -> list[dict]:
    """根据用户查询做搭配推荐，返回搭配组合（IK + 拼音双通道搜索）"""
    body = {
        "query": {
            "bool": {
                "should": [
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
            }
        },
        "size": size,
        "sort": ["_score"],
    }
    try:
        resp = es.search(index="products", body=body)
        return [h["_source"] for h in resp["hits"]["hits"]]
    except Exception:
        return []


def get_complementary(item_name: str, category_id: int) -> list[dict]:
    """根据某商品找搭配单品"""
    body = {
        "query": {
            "bool": {
                "must_not": [{"term": {"categoryId": category_id}}],
                "must": [{"range": {"price": {"gte": 50, "lte": 500}}}]
            }
        },
        "size": 3,
    }
    try:
        resp = es.search(index="products", body=body)
        return [h["_source"] for h in resp["hits"]["hits"]]
    except Exception:
        return []
