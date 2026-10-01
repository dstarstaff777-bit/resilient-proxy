package org.example.monitoring;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Instant;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;

@Component
public class CircuitBreakerEventBridge {

    private final Sinks.Many<CircuitBreakerEventDto> sink =
            Sinks.many().multicast().onBackpressureBuffer();

    public CircuitBreakerEventBridge(CircuitBreakerRegistry registry) {
        registry.getAllCircuitBreakers().forEach(this::subscribeTo);
        registry.getEventPublisher().onEntryAdded(event -> subscribeTo(event.getAddedEntry()));
    }

    private void subscribeTo(CircuitBreaker circuitBreaker) {
        circuitBreaker.getEventPublisher().onStateTransition(event -> sink.tryEmitNext(new CircuitBreakerEventDto(
                circuitBreaker.getName(),
                event.getStateTransition().getFromState().name(),
                event.getStateTransition().getToState().name(),
                Instant.now())));
    }

    public Flux<CircuitBreakerEventDto> stream() {
        return sink.asFlux();
    }
}
