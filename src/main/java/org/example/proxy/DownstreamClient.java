package org.example.proxy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class DownstreamClient {

    private final WebClient downstreamWebClient;

    public String fetch(String downstreamName) {
        log.info("Calling downstream '{}'", downstreamName);
        return downstreamWebClient.get()
                .uri("/downstream/{name}/data", downstreamName)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(2))
                .block();
    }
}
