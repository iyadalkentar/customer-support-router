package com.ikdev.customersupportrouter.chatservice.controller;

import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.ikdev.customersupportrouter.chatservice.dto.ErrorResponse;
import com.ikdev.customersupportrouter.chatservice.exception.ConversationClosedException;
import com.ikdev.customersupportrouter.chatservice.exception.ConversationNotFoundException;
import com.ikdev.customersupportrouter.chatservice.exception.TicketNotFoundException;
import com.ikdev.customersupportrouter.chatservice.service.MessageMetrics;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageMetrics messageMetrics;

    public GlobalExceptionHandler(MessageMetrics messageMetrics) {
        this.messageMetrics = messageMetrics;
    }

    @ExceptionHandler(ConversationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ConversationNotFoundException ex, HttpServletRequest request) {
        // Thrown by both POST /messages (ingest) and GET /conversations/{id}/messages
        // (read-back) — only the former counts against the ingest metric.
        if (HttpMethod.POST.matches(request.getMethod()) && "/messages".equals(request.getRequestURI())) {
            messageMetrics.recordIngest("rejected");
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(TicketNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTicketNotFound(TicketNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(ConversationClosedException.class)
    public ResponseEntity<ErrorResponse> handleClosed(ConversationClosedException ex) {
        messageMetrics.recordIngest("rejected");
        return ResponseEntity.status(HttpStatus.CONFLICT) // 409 - conversation exists but can't accept new messages
                .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        messageMetrics.recordIngest("rejected");
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(new ErrorResponse(message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedBody(HttpMessageNotReadableException ex) {
        // Spring maps this to 400 by default; without this handler the catch-all
        // below would swallow it and report a malformed request as a 500.
        messageMetrics.recordIngest("rejected");
        return ResponseEntity.badRequest().body(new ErrorResponse("Malformed request body"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoRoute(NoResourceFoundException ex) {
        // A request to a path with no controller mapping (e.g. a bogus URL or a
        // disabled actuator endpoint) — a routine client-side 404, not a server bug.
        // Without this handler the catch-all below reported it as a 500 and counted
        // it against the ingest error metric, even for requests that never touched
        // ingest logic at all.
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("No such endpoint"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("Invalid value for parameter '" + ex.getName() + "': " + ex.getValue()));
    }

    /**
     * Catch-all for anything not covered by a specific handler above — an
     * unexpected/server-side failure, as opposed to the client-caused rejections
     * handled elsewhere in this class. Logged at ERROR (with stack trace) since,
     * unlike a routine 404/409/400, this represents a bug or outage that needs
     * investigating; the client only gets a generic message, never internal detail.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception processing request", ex);
        messageMetrics.recordIngest("error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("An unexpected error occurred"));
    }
}