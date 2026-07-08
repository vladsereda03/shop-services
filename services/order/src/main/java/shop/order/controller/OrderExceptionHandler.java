package shop.order.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;

@RestControllerAdvice
public class OrderExceptionHandler {

    // propagate cart-service client errors as-is
    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<String> handleUpstreamClientError(HttpClientErrorException e) {
        return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
    }
}
