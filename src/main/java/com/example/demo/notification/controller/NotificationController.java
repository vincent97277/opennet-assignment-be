package com.example.demo.notification.controller;

import com.example.demo.notification.dto.CreateNotificationRequest;
import com.example.demo.notification.dto.NotificationResponse;
import com.example.demo.notification.dto.RecentNotificationResponse;
import com.example.demo.notification.dto.UpdateNotificationRequest;
import com.example.demo.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationResponse create(@Valid @RequestBody CreateNotificationRequest request) {
        return notificationService.create(request);
    }

    @GetMapping("/{id}")
    public NotificationResponse getById(@PathVariable Long id) {
        return notificationService.getById(id);
    }

    @GetMapping("/recent")
    public List<RecentNotificationResponse> getRecent() {
        return notificationService.getRecent();
    }

    @PutMapping("/{id}")
    public NotificationResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateNotificationRequest request
    ) {
        return notificationService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        notificationService.delete(id);
    }
}
