package com.ikdev.customersupportrouter.aiclassifierservice.kafka;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics;
import org.apache.kafka.clients.consumer.Consumer;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer lag metrics for ai-classifier-service.
 *
 * <p>Kafka consumer lag is exposed via Micrometer's {@link KafkaClientMetrics} binder,
 * which is registered as a {@link BeanPostProcessor} to intercept consumer factory
 * creation and bind metrics to each consumer instance via {@link ConsumerFactory.Listener}.
 *
 * <p><strong>Exposed meter:</strong> {@code kafka.consumer.fetch.manager.records.lag.max}
 * (Gauge), tagged by {@code client-id}, {@code topic}, {@code partition}. Micrometer builds
 * this name from Kafka's raw {@code records-lag-max} metric in the
 * {@code consumer-fetch-manager-metrics} group by stripping the {@code -metrics} suffix and
 * replacing {@code -} with {@code .}, prefixed with {@code kafka.} — verified against the
 * {@code KafkaMetrics}/{@code KafkaClientMetrics} binder in micrometer-core 1.17.0, not
 * assumed. Prometheus query:
 * {@code kafka_consumer_fetch_manager_records_lag_max{client_id="ai-classifier-group",topic="incoming-messages"}}
 */
@Component
public class KafkaConsumerLagMetrics implements BeanPostProcessor {

    private final MeterRegistry meterRegistry;
    private final Map<String, KafkaClientMetrics> boundMetricsByConsumerId = new ConcurrentHashMap<>();

    public KafkaConsumerLagMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DefaultKafkaConsumerFactory<?, ?> consumerFactory) {
            bindListener(consumerFactory);
        }
        return bean;
    }

    private <K, V> void bindListener(DefaultKafkaConsumerFactory<K, V> consumerFactory) {
        consumerFactory.addListener(new ConsumerFactory.Listener<K, V>() {
            @Override
            public void consumerAdded(String id, Consumer<K, V> consumer) {
                KafkaClientMetrics metrics = new KafkaClientMetrics(consumer);
                metrics.bindTo(meterRegistry);
                boundMetricsByConsumerId.put(id, metrics);
            }

            @Override
            public void consumerRemoved(String id, Consumer<K, V> consumer) {
                KafkaClientMetrics metrics = boundMetricsByConsumerId.remove(id);
                if (metrics != null) {
                    metrics.close();
                }
            }
        });
    }
}
