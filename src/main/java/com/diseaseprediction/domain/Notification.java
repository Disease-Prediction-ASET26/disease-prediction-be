package com.diseaseprediction.domain;

import java.time.LocalDateTime;
import java.util.UUID;

public class Notification {

    private UUID notificationId;
    private String subject;
    private String message;
    private LocalDateTime sentAt;
    private boolean isRead = false;

    public Notification() {}

    public Notification(String subject, String message) {
        this.subject = subject;
        this.message = message;
    }

    public void send() {}
}
