package com.diseaseprediction.domain;

import java.time.LocalDateTime;
import java.util.UUID;

public class Notification {

    private UUID notificationId;
    private String subject;
    private String message;
    private LocalDateTime sentAt;
    private boolean isRead = false;

    public void send() {
    }
}
