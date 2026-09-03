package com.fashion.seckill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("B6 listener 持久层故障暂停与恢复")
class B6ListenerPauserTest {
    @Test
    @DisplayName("MySQL 探针恢复后自动重启已暂停 listener")
    void healthyProbeResumesPausedListener() throws Exception {
        RabbitListenerEndpointRegistry registry = mock(RabbitListenerEndpointRegistry.class);
        MessageListenerContainer container = mock(MessageListenerContainer.class);
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(registry.getListenerContainer("seckillOrderConsumer")).thenReturn(container);
        when(container.isRunning()).thenReturn(true, false, true);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true);
        SeckillListenerPauser pauser = new SeckillListenerPauser(registry, dataSource);

        pauser.pause("seckillOrderConsumer");
        pauser.resumeWhenPersistenceHealthy();

        org.mockito.InOrder order = inOrder(container);
        order.verify(container).stop(org.mockito.ArgumentMatchers.any(Runnable.class));
        order.verify(container).start();
    }

    @Test
    @DisplayName("探针仍失败时保持暂停且不盲目启动")
    void unhealthyProbeKeepsListenerPaused() throws Exception {
        RabbitListenerEndpointRegistry registry = mock(RabbitListenerEndpointRegistry.class);
        MessageListenerContainer container = mock(MessageListenerContainer.class);
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(registry.getListenerContainer("seckillTimeoutConsumer")).thenReturn(container);
        when(container.isRunning()).thenReturn(true);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(false);
        SeckillListenerPauser pauser = new SeckillListenerPauser(registry, dataSource);

        pauser.pause("seckillTimeoutConsumer");
        pauser.resumeWhenPersistenceHealthy();

        verify(container).stop(org.mockito.ArgumentMatchers.any(Runnable.class));
        verify(container, never()).start();
    }
}
