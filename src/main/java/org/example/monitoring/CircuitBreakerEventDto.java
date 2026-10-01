package org.example.monitoring;

import java.time.Instant;

public record CircuitBreakerEventDto(String breakerName, String fromState, String toState, Instant timestamp) {
}