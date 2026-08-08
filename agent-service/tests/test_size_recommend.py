"""尺码推荐引擎单元测试

测试身高体重 -> 服装尺码的规则映射。
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.tools.size_recommend import (
    recommend_size,
    is_physical_info,
    get_physical_info,
    detect_garment_type,
)

# ===================== 基础尺码映射 =====================

def test_male_175_70_top_is_l():
    assert recommend_size(175, 70, "上装") == "L"


def test_slim_160_50_top_is_s():
    assert recommend_size(160, 50, "上装") == "S"


def test_short_slim_gets_s():
    assert recommend_size(155, 45, "上装") == "S"


def test_tall_heavy_gets_xl():
    assert recommend_size(180, 90, "上装") == "XL"


def test_lower_body_is_looser_than_top():
    # 同身高体重，下装尺码应 >= 上装尺码
    top = recommend_size(170, 75, "上装")
    bottom = recommend_size(170, 75, "下装")
    order = ["S", "M", "L", "XL"]
    assert order.index(bottom) >= order.index(top)


def test_dress_uses_lower_body_rule():
    assert recommend_size(165, 60, "连衣裙") == "M"


# ===================== 物理信息识别 =====================

def test_detect_height_weight_mentions():
    assert is_physical_info("我身高175，体重70公斤")
    assert is_physical_info("我身高1米75，体重70公斤")
    assert is_physical_info("身高165体重55")
    assert is_physical_info("我1.75米 70kg")


def test_not_physical_info_for_normal_message():
    assert not is_physical_info("帮我推荐一套穿搭")
    assert not is_physical_info("我的订单到哪了？")


def test_extract_physical_info():
    info = get_physical_info("我身高175，体重70公斤，想买件上衣")
    assert info["height"] == 175
    assert info["weight"] == 70


def test_extract_partial_info():
    info = get_physical_info("我身高165，请问穿什么尺码的裙子？")
    assert info["height"] == 165
    assert info["weight"] is None


# ===================== 服装类目识别 =====================

def test_detect_garment_type_by_keyword():
    assert "上装" in detect_garment_type("帮我选件T恤")
    assert "下装" in detect_garment_type("这条牛仔裤")
    assert "连衣裙" in detect_garment_type("这条连衣裙")


def test_unknown_garment_defaults_top():
    assert detect_garment_type("随便看看") == "上装"
