package com.fashion.product;

import com.fashion.entity.ProductProjectionTask;

public interface ProductProjectionDelivery {
    String target();
    void deliver(ProductProjectionTask task);
}
