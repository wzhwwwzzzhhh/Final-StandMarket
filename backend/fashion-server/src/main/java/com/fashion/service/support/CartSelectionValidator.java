package com.fashion.service.support;

import java.util.HashSet;
import java.util.List;

import static com.fashion.exception.PublicBusinessException.Code.CART_ITEM_DUPLICATE;
import static com.fashion.exception.PublicBusinessException.Code.CART_ITEM_ID_INVALID;
import static com.fashion.exception.PublicBusinessException.Code.CART_ITEM_LIMIT;
import static com.fashion.exception.PublicBusinessException.Code.SELECT_CART_ITEMS;
import static com.fashion.exception.PublicBusinessException.of;

/**
 * 结算购物车项输入边界。该校验必须在 Redis、数据库等外部写入之前执行。
 */
public final class CartSelectionValidator {

    public static final int MAX_CART_ITEM_COUNT = 100;

    private CartSelectionValidator() {
    }

    public static void validate(List<Long> cartItemIds) {
        if (cartItemIds == null || cartItemIds.isEmpty()) {
            throw of(SELECT_CART_ITEMS);
        }
        if (cartItemIds.size() > MAX_CART_ITEM_COUNT) {
            throw of(CART_ITEM_LIMIT);
        }
        if (cartItemIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw of(CART_ITEM_ID_INVALID);
        }
        if (new HashSet<>(cartItemIds).size() != cartItemIds.size()) {
            throw of(CART_ITEM_DUPLICATE);
        }
    }
}
