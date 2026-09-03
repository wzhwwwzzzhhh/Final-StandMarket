package com.fashion.seckill;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SeckillListenerPauser {
    private final RabbitListenerEndpointRegistry registry;
    private final DataSource dataSource;
    private final Set<String> pausedListeners = ConcurrentHashMap.newKeySet();

    public SeckillListenerPauser(RabbitListenerEndpointRegistry registry, DataSource dataSource) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public void pause(String listenerId) {
        MessageListenerContainer container = registry.getListenerContainer(listenerId);
        if (container != null) {
            pausedListeners.add(listenerId);
            if (container.isRunning()) {
                container.stop(() -> log.info("B6 listener paused, listenerId={}", listenerId));
            }
        }
    }

    @Scheduled(fixedDelayString = "${fashion.seckill.listener-health-delay-ms:5000}")
    public void resumeWhenPersistenceHealthy() {
        if (pausedListeners.isEmpty() || !persistenceHealthy()) return;
        for (String listenerId : pausedListeners.toArray(new String[0])) {
            MessageListenerContainer container = registry.getListenerContainer(listenerId);
            if (container == null) continue;
            try {
                if (!container.isRunning()) container.start();
                if (container.isRunning()) pausedListeners.remove(listenerId);
            } catch (RuntimeException startFailure) {
                log.warn("B6 listener resume deferred, listenerId={}", listenerId);
            }
        }
    }

    private boolean persistenceHealthy() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (Exception unavailable) {
            return false;
        }
    }
}
