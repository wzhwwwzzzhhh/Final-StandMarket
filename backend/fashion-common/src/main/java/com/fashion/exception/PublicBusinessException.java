package com.fashion.exception;

/**
 * Marks a deliberately user-safe business message that may cross the HTTP boundary.
 * Generic runtime and {@link BaseException} messages must never be exposed directly.
 */
public final class PublicBusinessException extends BaseException {

    public enum Code {
        COUPON_UNAVAILABLE("优惠券不可用"),
        CLAIM_BUSY("领取人数过多，请稍后再试"),
        CLAIM_LIMIT_REACHED("已达每人限领数量"),
        COUPON_SOLD_OUT("优惠券已领完"),
        CLAIM_FAILED("领取优惠券失败"),
        USER_NOT_LOGGED_IN("用户未登录"),
        LOGIN_REQUIRED("请先登录"),
        CART_FORBIDDEN("购物车商品不存在或无权操作"),
        CART_SNAPSHOT_INVALID("购物车快照无效"),
        PRODUCT_PRICE_INVALID("商品不存在或价格无效"),
        ORDER_REQUEST_REQUIRED("下单参数不能为空"),
        ADDRESS_NOT_FOUND("地址不存在"),
        CART_ITEM_NOT_FOUND("购物车商品不存在"),
        CART_ITEM_FORBIDDEN("存在无权操作的商品"),
        CART_QUANTITY_INVALID("购物车商品数量必须大于零"),
        PRODUCT_NOT_FOUND("商品不存在"),
        PRODUCT_OUT_OF_STOCK("商品库存不足"),
        CART_EMPTY("购物车为空"),
        SELECT_CART_ITEMS("请选择要结算的商品"),
        CART_ITEM_LIMIT("一次最多结算100个购物车项"),
        CART_ITEM_ID_INVALID("购物车项标识必须为正整数"),
        CART_ITEM_DUPLICATE("购物车项不能重复"),
        CART_ITEM_FORMAT_INVALID("购物车项格式错误"),
        COUPON_ORDER_AMOUNT_INVALID("订单金额非法"),
        ORDER_PRODUCTS_REQUIRED("订单商品不能为空"),
        COUPON_TYPE_INVALID("优惠券类型非法"),
        COUPON_THRESHOLD_INVALID("优惠券门槛非法"),
        COUPON_THRESHOLD_NOT_MET("未达到优惠券使用门槛"),
        COUPON_DISCOUNT_INVALID("优惠券折扣非法"),
        COUPON_SCOPE_INVALID("优惠券适用范围非法"),
        ORDER_PRODUCT_SNAPSHOT_INCOMPLETE("订单商品快照不完整"),
        COUPON_CATEGORY_SCOPE_INVALID("优惠券分类范围非法"),
        COUPON_NOT_APPLICABLE("优惠券不适用当前商品"),
        COUPON_PRODUCT_SCOPE_INVALID("优惠券商品范围非法"),
        REVIEW_DUPLICATE("该订单商品已评价"),
        REVIEW_NOT_ELIGIBLE("订单不存在、未完成或商品不属于订单"),
        REVIEW_REQUIRED("评价不能为空"),
        RATING_INVALID("评分必须在1-5之间"),
        REVIEW_CONTENT_TOO_LONG("评价内容不能超过500字"),
        REVIEW_IMAGES_TOO_LONG("评价图片信息不能超过1000字"),
        RATING_FILTER_INVALID("评分筛选参数错误"),
        PAGE_INVALID("分页参数错误"),
        PRODUCT_ID_INVALID("商品标识错误"),
        ORDER_ID_INVALID("订单标识错误");

        private final String message;

        Code(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    private final Code code;

    private PublicBusinessException(Code code) {
        super(requireCode(code).getMessage());
        this.code = code;
    }

    public static PublicBusinessException of(Code code) {
        return new PublicBusinessException(code);
    }

    public Code getCode() {
        return code;
    }

    private static Code requireCode(Code code) {
        if (code == null) {
            throw new IllegalArgumentException("public business code is required");
        }
        return code;
    }
}
