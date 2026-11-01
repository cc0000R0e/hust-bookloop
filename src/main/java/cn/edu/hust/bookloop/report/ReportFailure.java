package cn.edu.hust.bookloop.report;

import org.springframework.http.HttpStatus;

public class ReportFailure extends RuntimeException {
    private final HttpStatus status;

    public ReportFailure(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
