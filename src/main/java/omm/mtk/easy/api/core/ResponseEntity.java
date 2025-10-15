package omm.mtk.easy.api.core;

import java.util.HashMap;
import java.util.Map;

public class ResponseEntity<T> {
    private final T body;
    private final int status;
    private final Map<String, String> headers;
    
    public ResponseEntity(T body, int status) {
        this.body = body;
        this.status = status;
        this.headers = new HashMap<>();
    }
    
    public ResponseEntity(T body) {
        this(body, 200);
    }
    
    public static <T> ResponseEntity<T> ok(T body) {
        return new ResponseEntity<>(body, 200);
    }
    
    public static <T> ResponseEntity<T> created(T body) {
        return new ResponseEntity<>(body, 201);
    }
    
    public static <T> ResponseEntity<T> noContent() {
        return new ResponseEntity<>(null, 204);
    }
    
    public static <T> ResponseEntity<T> badRequest() {
        return new ResponseEntity<>(null, 400);
    }
    
    public static <T> ResponseEntity<T> badRequest(T body) {
        return new ResponseEntity<>(body, 400);
    }
    
    public static <T> ResponseEntity<T> notFound() {
        return new ResponseEntity<>(null, 404);
    }
    
    public static <T> ResponseEntity<T> notFound(T body) {
        return new ResponseEntity<>(body, 404);
    }
    
    public static <T> ResponseEntity<T> status(int status) {
        return new ResponseEntity<>(null, status);
    }
    
    public static <T> ResponseEntity<T> status(int status, T body) {
        return new ResponseEntity<>(body, status);
    }
    
    public ResponseEntity<T> header(String name, String value) {
        this.headers.put(name, value);
        return this;
    }
    
    public ResponseEntity<T> contentType(String contentType) {
        return header("Content-Type", contentType);
    }
    
    public ResponseEntity<T> location(String location) {
        return header("Location", location);
    }
    
    public T getBody() { return body; }
    public int getStatus() { return status; }
    public Map<String, String> getHeaders() { return headers; }
}