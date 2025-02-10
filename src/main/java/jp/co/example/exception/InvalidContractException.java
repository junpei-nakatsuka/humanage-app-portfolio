package jp.co.example.exception;

public class InvalidContractException extends RuntimeException {

    public InvalidContractException(String message) {
        super(message);
    }

    public InvalidContractException(String message, Throwable cause) {
        super(message, cause);
    }
}
