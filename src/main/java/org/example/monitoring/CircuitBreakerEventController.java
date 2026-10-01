package org.example.monitoring;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.awt.*;

@RestController
@RequiredArgsConstructor
public class CircuitBreakerEventController {

    private final CircuitBreakerEventBridge bridge;

    @GetMapping(value = "/monitoring/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<CircuitBreakerEventDto>> streamEvents() {
        return bridge.stream().map(event -> ServerSentEvent.builder(event).build());
    }
}
