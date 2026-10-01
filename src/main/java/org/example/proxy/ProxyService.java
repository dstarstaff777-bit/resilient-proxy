package org.example.proxy;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Slf4j
@Service
@RequiredArgsConstructor
public class ProxyService {

    private final DownstreamClient downstreamClient;

    @CircuitBreaker(name = "orders", fallbackMethod = "ordersFallback")
    @RateLimiter(name = "orders")
    @Bulkhead(name = "orders")
    @Retry(name = "orders")
    public String fetchOrders() {
        return downstreamClient.fetch("orders");
    }

    @CircuitBreaker(name = "notifications", fallbackMethod = "notificationsFallback")
    @RateLimiter(name = "notifications")
    @Bulkhead(name = "notifications")
    @Retry(name = "notifications")
    public String fetchNotifications() {
        return downstreamClient.fetch("notifications");
    }

    private String ordersFallback(Throwable t) {
        log.warn("orders fallback: {}", t.toString());
        return "orders unavailable, returning fallback response";
    }

    private String notificationsFallback(Throwable t) {
        log.warn("notifications fallback: {}", t.toString());
        return "notifications unavailable, returning fallback response";
    }
}