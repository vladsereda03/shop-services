package shop.payment.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;

@RestControllerAdvice
public class PaymentExceptionHandler {

  // propagate order/cart-service client errors as-is (the duplicate-callback 400 is
  // swallowed earlier in PaymentCallbackService and never reaches this advice);
  // upstream answers are already RFC 7807 problem+json, so the relay stays consistent
  @ExceptionHandler(HttpClientErrorException.class)
  public ResponseEntity<String> handleUpstreamClientError(HttpClientErrorException e) {
    return ResponseEntity.status(e.getStatusCode())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(e.getResponseBodyAsString());
  }
}
