package dev.sg.portfolio.service;

public class OpenAiRealtimeException extends RuntimeException {

    private final int statusCode;

    public OpenAiRealtimeException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
