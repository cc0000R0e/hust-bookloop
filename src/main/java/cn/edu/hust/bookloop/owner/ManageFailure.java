package cn.edu.hust.bookloop.owner;

import org.springframework.http.HttpStatus;

public class ManageFailure extends RuntimeException {
    private final HttpStatus status;

    public ManageFailure(String message) {
        this(HttpStatus.CONFLICT, message);
    }

    public ManageFailure(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
