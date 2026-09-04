package com.fashion.product;

import com.fashion.entity.ProductProjectionTask;

public interface ProductBaselineProjectionRepairer {
    ProductProjectionTask ensureCurrentEsTask(long productId);
}
