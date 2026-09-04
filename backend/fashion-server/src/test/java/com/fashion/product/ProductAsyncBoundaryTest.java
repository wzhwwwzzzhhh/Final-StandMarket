package com.fashion.product;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProductAsyncBoundaryTest {

    @Test
    void afterCommitEventOnlyQueuesWorkerIo() {
        ProductProjectionTaskRepository repository = mock(ProductProjectionTaskRepository.class);
        ProductProjectionDelivery redis = mock(ProductProjectionDelivery.class);
        ProductProjectionDelivery es = mock(ProductProjectionDelivery.class);
        when(redis.target()).thenReturn("REDIS");
        when(es.target()).thenReturn("ES");
        AtomicReference<Runnable> queued = new AtomicReference<>();
        Executor executor = queued::set;
        ProductProjectionWorker worker = new ProductProjectionWorker(
                repository, Arrays.asList(redis, es), mock(ProductCatalogAuthority.class),
                new ProductProjectionProperties(), executor);

        worker.onReady(new ProductProjectionReadyEvent(7L, 11L));

        verifyNoInteractions(repository);
        assertThat(queued.get()).isNotNull();
        queued.get().run();
        verify(repository).claim("REDIS");
        verify(repository).claim("ES");
    }
}
