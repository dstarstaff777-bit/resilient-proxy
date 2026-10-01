package org.example.proxy;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;


@RestController
@RequestMapping("/proxy")
@RequiredArgsConstructor
public class ProxyController {

    private final ProxyService proxyService;

    @GetMapping("/orders")
    public String getOrders() {
        return proxyService.fetchOrders();
    }

    @GetMapping("/notifications")
    public String getNotifications() {
        return proxyService.fetchNotifications();
    }
}