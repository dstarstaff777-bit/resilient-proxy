package org.example.downstream;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/downstream")
@RequiredArgsConstructor
public class FlakyDownstreamController {

    private final ChaosRegistry chaosRegistry;

    @GetMapping("/{name}/data")
    public ResponseEntity<String> getData(@PathVariable String name) throws InterruptedException {
        boolean shouldFail = ThreadLocalRandom.current().nextInt(100) < chaosRegistry.getFailureRatePercent(name);
        if (shouldFail) {
            if (ThreadLocalRandom.current().nextBoolean()) {
                Thread.sleep(3000);
            }
            return ResponseEntity.status(500).body(name + " upstream error");
        }
        return ResponseEntity.ok(name + " data: " + Instant.now());
    }

    @PostMapping("/{name}/chaos")
    public ResponseEntity<Void> setFailureRate(@PathVariable String name, @RequestParam int failureRate) {
        chaosRegistry.setFailureRatePercent(name, failureRate);
        return ResponseEntity.ok().build();
    }
}
