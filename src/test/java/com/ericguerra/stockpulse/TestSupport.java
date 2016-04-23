package com.ericguerra.stockpulse;

import com.ericguerra.stockpulse.events.AvailabilityPublisher;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

@TestConfiguration
public class TestSupport {
    public static final AtomicReference<Instant> NOW = new AtomicReference<>(Instant.parse("2016-03-01T15:00:00Z"));

    public static class RecordingPublisher implements AvailabilityPublisher {
        public final List<AvailabilityChanged> published = new CopyOnWriteArrayList<>();

        @Override
        public void publish(AvailabilityChanged change) {
            published.add(change);
        }
    }

    @Bean
    RecordingPublisher recordingPublisher() {
        return new RecordingPublisher();
    }

    @Bean
    @Primary
    Clock testClock() {
        return new Clock() {
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() { return NOW.get(); }
        };
    }
}
