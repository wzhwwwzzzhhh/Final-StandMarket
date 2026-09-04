package com.fashion.product;

import com.fashion.entity.Product;
import com.fashion.mapper.ProductMapper;
import com.fashion.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductCatalogServiceTest {

    @Test
    void stockOnlyAndNoopDoNotAdvanceCatalogWhileMixedAdvancesExactlyOnce() throws Exception {
        ProductMapper mapper = mock(ProductMapper.class);
        ProductCatalogMutationCoordinator coordinator = mock(ProductCatalogMutationCoordinator.class);
        ProductServiceImpl service = new ProductServiceImpl(mapper, coordinator);
        Product existing = product();

        Product stock = new Product();
        stock.setId(7L);
        stock.setStock(9);
        when(mapper.getByIdForUpdate(7L)).thenReturn(existing);
        when(mapper.update(stock)).thenReturn(1);
        assertThat(service.update(stock)).isTrue();
        verifyNoInteractions(coordinator);

        reset(mapper, coordinator);
        when(mapper.getByIdForUpdate(7L)).thenReturn(existing);
        Product noOp = new Product();
        noOp.setId(7L);
        noOp.setName("coat");
        assertThat(service.update(noOp)).isTrue();
        verify(mapper, never()).update(any());
        verifyNoInteractions(coordinator);

        reset(mapper, coordinator);
        Product mixed = new Product();
        mixed.setId(7L);
        mixed.setStock(8);
        mixed.setPrice(new BigDecimal("12.00"));
        Product committed = product();
        committed.setStock(8);
        committed.setPrice(new BigDecimal("12.00"));
        when(mapper.getByIdForUpdate(7L)).thenReturn(existing);
        when(mapper.update(mixed)).thenReturn(1);
        when(mapper.getByIdIncludingInactive(7L)).thenReturn(committed);
        assertThat(service.update(mixed)).isTrue();
        verify(coordinator, times(1)).record(committed, ProductItemState.ACTIVE);

        assertThat(ProductServiceImpl.class.getMethod("update", Product.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    void savePersistsNormalizedSnapshotAndDeleteKeepsTombstone() {
        ProductMapper mapper = mock(ProductMapper.class);
        ProductCatalogMutationCoordinator coordinator = mock(ProductCatalogMutationCoordinator.class);
        ProductServiceImpl service = new ProductServiceImpl(mapper, coordinator);
        Product created = product();
        created.setId(null);
        created.setSales(null);
        doAnswer(invocation -> {
            Product argument = invocation.getArgument(0);
            argument.setId(7L);
            return 1;
        }).when(mapper).save(created);
        Product committed = product();
        when(mapper.getByIdIncludingInactive(7L)).thenReturn(committed);

        assertThat(service.save(created)).isTrue();
        assertThat(created.getSales()).isZero();
        verify(coordinator).record(committed, ProductItemState.ACTIVE);

        reset(mapper, coordinator);
        Product deleting = product();
        when(mapper.getByIdForUpdate(7L)).thenReturn(deleting);
        when(mapper.deleteById(7L)).thenReturn(1);
        assertThat(service.removeById(7L)).isTrue();
        verify(coordinator).record(deleting, ProductItemState.DELETED);
    }

    private Product product() {
        Product product = new Product();
        product.setId(7L);
        product.setName("coat");
        product.setDescription("warm");
        product.setCategoryId(3L);
        product.setPrice(new BigDecimal("10.00"));
        product.setImage("coat.jpg");
        product.setTag("new");
        product.setStatus(1);
        product.setStock(10);
        product.setSales(0);
        return product;
    }
}
