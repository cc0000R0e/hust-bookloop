package cn.edu.hust.bookloop.book;

import org.springframework.http.HttpStatus;

public class BookAccessException extends RuntimeException {
    private final HttpStatus status;
    public BookAccessException(HttpStatus status) { this.status = status; }
    public HttpStatus status() { return status; }
}
