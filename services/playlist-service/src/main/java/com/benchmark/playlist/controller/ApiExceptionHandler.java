package com.benchmark.playlist.controller;

import com.benchmark.playlist.service.PlaylistNotFoundException;
import com.benchmark.playlist.service.PlaylistOperationException;
import com.benchmark.playlist.service.PlaylistTrackNotFoundException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({PlaylistNotFoundException.class, PlaylistTrackNotFoundException.class})
    ResponseEntity<ApiError> handleNotFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(404, "playlist_not_found", List.of(ex.getMessage())));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class,
            PlaylistOperationException.class
    })
    ResponseEntity<ApiError> handleBadRequest(Exception ex) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "validation_failed", List.of(ex.getMessage())));
    }
}
