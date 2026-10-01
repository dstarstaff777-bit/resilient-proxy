package org.example;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import org.example.proxy.ProxyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = ResilientProxyApplication.class)
@ActiveProfiles("test")
class ProxyServiceCircuitBreakerTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void overrideDownstreamUrl(DynamicPropertyRegistry registry) {
        registry.add("downstream.url", wireMock::baseUrl);
    }

    @Autowired
    private ProxyService proxyService;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void resetCircuitBreakers() {
        circuitBreakerRegistry.circuitBreaker("orders").reset();
        circuitBreakerRegistry.circuitBreaker("notifications").reset();
        wireMock.resetAll();
    }

    @Test
    void ordersBreakerOpensAfterFailureRateThresholdExceeded() {
        wireMock.stubFor(get(urlEqualTo("/downstream/orders/data")).willReturn(serverError()));

        IntStream.range(0, 5).forEach(i -> proxyService.fetchOrders());

        assertThat(circuitBreakerRegistry.circuitBreaker("orders").getState())
                .isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void ordersBreakerRejectsCallsWithoutHittingDownstreamWhenOpen() {
        wireMock.stubFor(get(urlEqualTo("/downstream/orders/data")).willReturn(serverError()));
        IntStream.range(0, 5).forEach(i -> proxyService.fetchOrders());

        wireMock.resetRequests();
        wireMock.stubFor(get(urlEqualTo("/downstream/orders/data")).willReturn(ok()));

        String result = proxyService.fetchOrders();

        assertThat(result).contains("fallback");
        wireMock.verify(0, getRequestedFor(urlEqualTo("/downstream/orders/data")));
    }

    @Test
    void openingOrdersBreakerDoesNotAffectNotificationsBreaker() {
        wireMock.stubFor(get(urlEqualTo("/downstream/orders/data")).willReturn(serverError()));
        IntStream.range(0, 5).forEach(i -> proxyService.fetchOrders());
        assertThat(circuitBreakerRegistry.circuitBreaker("orders").getState())
                .isEqualTo(CircuitBreaker.State.OPEN);

        wireMock.stubFor(get(urlEqualTo("/downstream/notifications/data")).willReturn(ok("notifications data: ok")));
        String result = proxyService.fetchNotifications();

        assertThat(result).doesNotContain("fallback");
        assertThat(circuitBreakerRegistry.circuitBreaker("notifications").getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void ordersBreakerTransitionsToHalfOpenThenClosesOnSuccess() throws InterruptedException {
        wireMock.stubFor(get(urlEqualTo("/downstream/orders/data")).willReturn(serverError()));
        IntStream.range(0, 5).forEach(i -> proxyService.fetchOrders());
        assertThat(circuitBreakerRegistry.circuitBreaker("orders").getState())
                .isEqualTo(CircuitBreaker.State.OPEN);

        wireMock.stubFor(get(urlEqualTo("/downstream/orders/data")).willReturn(ok("orders data: ok")));
        Thread.sleep(1100);

        IntStream.range(0, 3).forEach(i -> proxyService.fetchOrders());

        assertThat(circuitBreakerRegistry.circuitBreaker("orders").getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void verifyRetryCircuitBreakerOrdering() {
        wireMock.stubFor(get(urlEqualTo("/downstream/orders/data"))
                .willReturn(aResponse().withFixedDelay(3000).withStatus(200).withBody("too slow")));

        String result = proxyService.fetchOrders();

        assertThat(result).contains("fallback");

        long failedCalls = circuitBreakerRegistry.circuitBreaker("orders")
                .getMetrics().getNumberOfFailedCalls();

        System.out.println(">>> CircuitBreaker засчитал failed calls: " + failedCalls);

    }

    @Test
    void bulkheadRejectsSecondConcurrentCall() throws Exception {
        Bulkhead bulkhead = Bulkhead.of("isolatedBulkheadTest",
                BulkheadConfig.custom().maxConcurrentCalls(1).maxWaitDuration(Duration.ZERO).build());

        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> bulkhead.executeRunnable(() -> {
            try {
                release.await();
            } catch (InterruptedException ignored) {
            }
        }));

        while (bulkhead.getMetrics().getAvailableConcurrentCalls() > 0) {
            Thread.onSpinWait();
        }

        assertThatThrownBy(() -> bulkhead.executeRunnable(() -> {
        }))
                .isInstanceOf(BulkheadFullException.class);

        release.countDown();
        executor.shutdown();
    }

    @Test
    void rateLimiterRejectsCallBeyondLimit() {
        RateLimiter rateLimiter = RateLimiter.of("isolatedRateLimiterTest",
                RateLimiterConfig.custom()
                        .limitForPeriod(1)
                        .limitRefreshPeriod(Duration.ofSeconds(10))
                        .timeoutDuration(Duration.ZERO)
                        .build());

        assertThat(rateLimiter.acquirePermission()).isTrue();
        assertThat(rateLimiter.acquirePermission()).isFalse();
    }

}