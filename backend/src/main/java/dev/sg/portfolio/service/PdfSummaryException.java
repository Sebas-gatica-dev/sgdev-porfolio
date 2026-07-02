package dev.sg.portfolio.service;

public class PdfSummaryException extends RuntimeException {

    private final int statusCode;

    public PdfSummaryException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
