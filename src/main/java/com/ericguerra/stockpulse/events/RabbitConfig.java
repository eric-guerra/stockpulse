package com.ericguerra.stockpulse.events;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Topology:
 *   inventory.events (topic)  --inventory.#-->  stockpulse.inventory  (DLX -> stockpulse.inventory.dlq)
 *   availability.changed (fanout) for downstream consumers
 */
@Configuration
@Profile("!test")
public class RabbitConfig {
    public static final String INBOUND_EXCHANGE = "inventory.events";
    public static final String INBOUND_QUEUE = "stockpulse.inventory";
    public static final String DLX = "stockpulse.dlx";
    public static final String DLQ = "stockpulse.inventory.dlq";
    public static final String OUTBOUND_EXCHANGE = "availability.changed";

    @Bean
    TopicExchange inventoryExchange() {
        return new TopicExchange(INBOUND_EXCHANGE, true, false);
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    Queue inventoryQueue() {
        return QueueBuilder.durable(INBOUND_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", DLQ)
                .build();
    }

    @Bean
    Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    Binding inventoryBinding() {
        return BindingBuilder.bind(inventoryQueue()).to(inventoryExchange()).with("inventory.#");
    }

    @Bean
    Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with(DLQ);
    }

    @Bean
    FanoutExchange availabilityExchange() {
        return new FanoutExchange(OUTBOUND_EXCHANGE, true, false);
    }

    @Bean
    MessageConverter jsonConverter(com.fasterxml.jackson.databind.ObjectMapper mapper) {
        return new Jackson2JsonMessageConverter(mapper);
    }

    @Bean
    AvailabilityPublisher rabbitPublisher(RabbitTemplate template) {
        return change -> template.convertAndSend(OUTBOUND_EXCHANGE, "", change);
    }
}
