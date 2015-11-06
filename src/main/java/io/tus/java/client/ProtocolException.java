package io.tus.java.client;

/**
 * This exception is thrown if the server sends a request with an unexpected status code or
 * missing/invalid headers.
 */
public class ProtocolException extends Exception {
    private int statusCode = 0;

    public ProtocolException(String message) {
        super(message);
    }

    public ProtocolException(String message, int statusCode) {
        this(message);
        this.statusCode = statusCode;
    }

    /**
     * Get the HTTP status code for the request that created this exception.
     * @return - the HTTP status code associated with this exception.
     */
    public int getStatusCode() {
        return this.statusCode;
    }
}
