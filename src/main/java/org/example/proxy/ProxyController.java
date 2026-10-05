package org.example.proxy;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;


@RestController
@RequestMapping("/proxy")
@RequiredArgsConstructor
public class ProxyController {

    private final ProxyService proxyService;

    @GetMapping("/orders")
    public String getOrders() {
        MDC.put("requestId", UUID.randomUUID().toString().substring(0, 8));
        try {
            return proxyService.fetchOrders();
        } finally {
            MDC.remove("requestId");
        }
    }

    @GetMapping("/notifications")
    public String getNotifications() {
        MDC.put("requestId", UUID.randomUUID().toString().substring(0, 8));
        try {
            return proxyService.fetchNotifications();
        } finally {
            MDC.remove("requestId");
        }
    }
}