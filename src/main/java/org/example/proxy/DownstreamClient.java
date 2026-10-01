package org.example.proxy;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class DownstreamClient {

    private final WebClient downstreamWebClient;

    public String fetch(String downstreamName) {
        return downstreamWebClient.get()
                .uri("/downstream/{name}/data", downstreamName)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(2))
                .block();
    }
}
