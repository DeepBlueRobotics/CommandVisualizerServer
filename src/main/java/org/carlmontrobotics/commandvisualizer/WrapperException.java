package org.carlmontrobotics.commandvisualizer;

public class WrapperException extends RuntimeException {

    public WrapperException(String message) {
        super(message);
    }

    public WrapperException(String message, Throwable cause) {
        super(message, cause);
    }

    public WrapperException(Throwable cause) {
        super(cause);
    }

    public WrapperException() {
        super();
    }

}
