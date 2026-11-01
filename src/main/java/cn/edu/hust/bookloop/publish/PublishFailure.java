package cn.edu.hust.bookloop.publish;

public class PublishFailure extends RuntimeException {
    public PublishFailure(String message) { super(message); }
    public PublishFailure(String message, Throwable cause) { super(message, cause); }
}
