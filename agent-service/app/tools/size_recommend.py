"""尺码推荐引擎：身高/体重 -> S/M/L/XL 规则映射，按服装类目区分上装/下装"""
import re

SIZES = ["S", "M", "L", "XL"]

DRESS_KEYWORDS = ["连衣裙"]
BOTTOM_KEYWORDS = ["牛仔裤", "休闲裤", "阔腿裤", "短裤", "半身裙", "长裙", "短裙", "裤", "裙"]
TOP_KEYWORDS = ["T恤", "t恤", "tee", "上衣", "衬衫", "衬衣", "卫衣", "毛衣",
                "针织", "毛织", "外套", "开衫", "夹克", "风衣", "大衣", "吊带", "背心", "打底", "polo"]


def _bmi_index(bmi: float) -> int:
    """BMI -> 尺码下标（S/M/L/XL）"""
    if bmi < 20:
        return 0
    if bmi < 22.5:
        return 1
    if bmi < 25:
        return 2
    return 3


def recommend_size(height, weight, garment_type="上装") -> str:
    """根据身高体重和服装类目推荐尺码

    规则：以 BMI 为基准映射 S/M/L/XL；下装比上装大一个尺码（更宽松）。
    """
    try:
        height = float(height)
        weight = float(weight)
    except (TypeError, ValueError):
        return "M"
    if height <= 0 or weight <= 0:
        return "M"

    bmi = weight / ((height / 100) ** 2)
    idx = _bmi_index(bmi)
    if garment_type == "下装":
        idx = min(idx + 1, len(SIZES) - 1)
    return SIZES[idx]


def detect_garment_type(text: str) -> str:
    """从文本中识别服装类目：连衣裙 / 下装 / 上装，默认上装"""
    if not text:
        return "上装"
    if any(k in text for k in DRESS_KEYWORDS):
        return "连衣裙"
    if any(k in text for k in BOTTOM_KEYWORDS):
        return "下装"
    if any(k in text for k in TOP_KEYWORDS):
        return "上装"
    return "上装"


# ===================== 身高体重提取 =====================

_HEIGHT_MARKER_RE = re.compile(r"身高|身长|个子|多高|体重")
_MIX_RE = re.compile(r"(\d)\s*米\s*(\d{1,2})\s*(?:厘米|cm)?", re.I)
_DEC_M_RE = re.compile(r"(\d(?:\.\d{1,2})?)\s*米", re.I)
_CM_RE = re.compile(r"(\d{3})\s*(?:厘米|cm)?", re.I)
_WEIGHT_UNIT_RE = re.compile(r"(\d{1,3})\s*(公斤|千克|kg|KG|斤)")
_WEIGHT_BARE_RE = re.compile(r"体重[^\d]{0,6}(\d{1,3})")


def get_physical_info(message: str) -> dict:
    """从用户消息中提取身高体重，返回 {"height": int|None, "weight": int|None}"""
    info = {"height": None, "weight": None}

    m_w = _WEIGHT_UNIT_RE.search(message)
    if m_w:
        val = int(m_w.group(1))
        # 斤 -> 公斤
        info["weight"] = val if m_w.group(2) != "斤" else val / 2
    else:
        m_bare = _WEIGHT_BARE_RE.search(message)
        if m_bare:
            info["weight"] = int(m_bare.group(1))

    has_marker = bool(_HEIGHT_MARKER_RE.search(message))
    m_mix = _MIX_RE.search(message)
    if m_mix:
        info["height"] = int(m_mix.group(1)) * 100 + int(m_mix.group(2))
    else:
        m_dec = _DEC_M_RE.search(message)
        if m_dec:
            info["height"] = int(float(m_dec.group(1)) * 100)
        else:
            m_cm = _CM_RE.search(message)
            if m_cm and (has_marker or info["weight"] is not None):
                info["height"] = int(m_cm.group(1))
    return info


def is_physical_info(message: str) -> bool:
    """判断消息是否包含身高或体重信息"""
    info = get_physical_info(message)
    return info["height"] is not None or info["weight"] is not None
