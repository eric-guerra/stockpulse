package com.ericguerra.stockpulse.events;

import com.ericguerra.stockpulse.service.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * Consumes store inventory events. Invalid messages are rejected to the DLQ immediately;
 * transient failures (lock contention, DB hiccups) throw so the broker redelivers.
 */
@Component
@Profile("!test")
public class InventoryEventListener {
    private static final Logger log = LoggerFactory.getLogger(InventoryEventListener.class);
    private final InventoryService inventory;

    public InventoryEventListener(InventoryService inventory) {
        this.inventory = inventory;
    }

    @RabbitListener(queues = RabbitConfig.INBOUND_QUEUE, concurrency = "4-8")
    public void onEvent(InventoryEvent event) {
        try {
            inventory.apply(event);
        } catch (IllegalArgumentException e) {
            log.warn("rejecting invalid event {}: {}", event, e.getMessage());
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw e; // redelivered and retried
        }
    }
}
