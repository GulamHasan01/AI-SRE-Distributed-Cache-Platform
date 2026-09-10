package com.cache.exception;

import com.cache.dto.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import com.cache.cluster.exception.NodeNotFoundException;
import com.cache.cluster.exception.NodeAlreadyExistsException;
import com.cache.cluster.exception.NodeCommunicationException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CacheKeyNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleKeyNotFound(CacheKeyNotFoundException ex) {
        log.warn("Cache miss: key='{}' not found", ex.getKey());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure("Key not found", ex.getMessage()));
    }

    @ExceptionHandler(CacheCapacityExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleCapacityExceeded(CacheCapacityExceededException ex) {
        log.warn("Cache capacity exceeded: max={}, current={}", ex.getMaxCapacity(), ex.getCurrentSize());
        return ResponseEntity
                .status(HttpStatus.INSUFFICIENT_STORAGE)
                .body(ApiResponse.failure("Cache is full", ex.getMessage()));
    }

    @ExceptionHandler(NodeNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNodeNotFound(NodeNotFoundException ex) {
        log.warn("Cluster: node not found: id='{}'", ex.getNodeId());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure("Node not found", ex.getMessage()));
    }

    @ExceptionHandler(NodeAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Void>> handleNodeAlreadyExists(NodeAlreadyExistsException ex) {
        log.warn("Cluster: duplicate node registration: id='{}'", ex.getNodeId());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.failure("Node already registered", ex.getMessage()));
    }

    @ExceptionHandler(NodeCommunicationException.class)
    public ResponseEntity<ApiResponse<Void>> handleNodeCommunication(NodeCommunicationException ex) {
        log.error("Cluster: communication error with node '{}' at '{}': {}",
                ex.getTargetNodeId(), ex.getTargetUrl(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.failure("Bad Gateway", ex.getMessage()));
    }
    @ExceptionHandler(com.cache.cluster.exception.ReplicationQuorumException.class)
    public ResponseEntity<ApiResponse<Void>> handleReplicationQuorum(com.cache.cluster.exception.ReplicationQuorumException ex) {
        log.error("Cluster: replication quorum failed: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.GATEWAY_TIMEOUT)
                .body(ApiResponse.failure("Gateway Timeout", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));

        log.warn("Validation failed: {}", errors);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure("Validation failed", errors));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String error = String.format("Parameter '%s' has invalid value '%s'", ex.getName(), ex.getValue());
        log.warn("Type mismatch: {}", error);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure("Invalid parameter", error));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Request body is missing or malformed: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure("Malformed request body", "Required request body is missing or invalid"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(
                        "An unexpected error occurred",
                        "Please contact support if the problem persists"
                ));
    }
}
