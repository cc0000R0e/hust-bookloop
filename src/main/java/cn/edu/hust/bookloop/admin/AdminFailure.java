package cn.edu.hust.bookloop.admin;

import org.springframework.http.HttpStatus;

public class AdminFailure extends RuntimeException {
    private final HttpStatus status;

    public AdminFailure(String message) {
        this(HttpStatus.CONFLICT, message);
    }

    public AdminFailure(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
