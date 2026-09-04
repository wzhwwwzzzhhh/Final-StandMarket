package com.fashion.controller;

import com.fashion.controller.admin.ProductController;
import com.fashion.controller.user.UserProductController;
import com.fashion.dto.ProductQueryDTO;
import com.fashion.dto.ProductSaveDTO;
import com.fashion.dto.ProductUpdateDTO;
import com.fashion.entity.PageResult;
import com.fashion.entity.Product;
import com.fashion.product.ProductCatalogCacheService;
import com.fashion.service.ProductService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProductControllerB8Test {

    @Test
    void adminWritesDelegateOnlyToTransactionalProductService() {
        ProductService service = mock(ProductService.class);
        when(service.save(any())).thenReturn(true);
        when(service.update(any())).thenReturn(true);
        ProductController controller = new ProductController(service);
        ProductSaveDTO save = new ProductSaveDTO();
        save.setName("coat");
        save.setImage("coat.jpg");

        assertThat(controller.save(save).getCode()).isEqualTo(1);
        ArgumentCaptor<Product> created = ArgumentCaptor.forClass(Product.class);
        verify(service).save(created.capture());
        assertThat(created.getValue().getImage()).isEqualTo("coat.jpg");

        assertThat(controller.update(7L, new ProductUpdateDTO()).getCode()).isEqualTo(1);
        verify(service).update(any(Product.class));
    }

    @Test
    void userReadsDelegateToVersionedCatalogCacheService() {
        ProductCatalogCacheService cache = mock(ProductCatalogCacheService.class);
        when(cache.page(any())).thenReturn(new PageResult<>(0, Collections.emptyList()));
        Product product = new Product();
        product.setId(7L);
        when(cache.detail(7L)).thenReturn(product);
        UserProductController controller = new UserProductController(cache);

        assertThat(controller.page(new ProductQueryDTO()).getData().getTotal()).isZero();
        assertThat(controller.getById(7L).getData().getId()).isEqualTo(7L);
    }
}
