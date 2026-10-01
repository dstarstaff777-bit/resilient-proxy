package org.example.downstream;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ChaosRegistry {

    private final Map<String, AtomicInteger> failureRates = new ConcurrentHashMap<>();

    public int getFailureRatePercent(String downstreamName) {
        return failureRates.computeIfAbsent(downstreamName, name -> new AtomicInteger(0)).get();
    }

    public void setFailureRatePercent(String downstreamName, int value) {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException("failureRate must be between 0 and 100");
        }
        failureRates.computeIfAbsent(downstreamName, name -> new AtomicInteger(0)).set(value);
    }
}