package com.diseaseprediction.service;

public class RedFlagResult {

    private final boolean emergency;
    private final String message;

    private RedFlagResult(boolean emergency, String message) {
        this.emergency = emergency;
        this.message = message;
    }

    public static RedFlagResult emergency(String message) {
        return new RedFlagResult(true, message);
    }

    public static RedFlagResult none() {
        return new RedFlagResult(false, null);
    }

    public boolean isEmergency() {
        return emergency;
    }

    public String getMessage() {
        return message;
    }
}
