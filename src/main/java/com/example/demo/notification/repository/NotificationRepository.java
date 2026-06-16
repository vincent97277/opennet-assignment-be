package com.example.demo.notification.repository;

import com.example.demo.notification.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop10ByOrderByCreatedAtDescIdDesc();
}
