package com.community.iam_service.exception;

import org.springframework.http.HttpStatus;

public class AppException extends RuntimeException {

    private final HttpStatus status;

    public AppException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static class EmailAlreadyExistsException extends AppException {
        public EmailAlreadyExistsException(String email) {
            super("Email already registered: " + email, HttpStatus.CONFLICT);
        }
    }

    public static class InvalidCredentialsException extends AppException {
        public InvalidCredentialsException() {
            super("Invalid email or password", HttpStatus.UNAUTHORIZED);
        }
    }

    public static class UserNotFoundException extends AppException {
        public UserNotFoundException(String identifier) {
            super("User not found: " + identifier, HttpStatus.NOT_FOUND);
        }
    }

    public static class InvalidTokenException extends AppException {
        public InvalidTokenException(String reason) {
            super("Invalid token: " + reason, HttpStatus.UNAUTHORIZED);
        }
    }

    public static class AccountSuspendedException extends AppException {
        public AccountSuspendedException() {
            super("Your account has been suspended. Contact support.", HttpStatus.FORBIDDEN);
        }
    }

    public static class WrongPasswordException extends AppException {
        public WrongPasswordException() {
            super("Current password is incorrect", HttpStatus.UNAUTHORIZED);
        }
    }

    public static class AccountLockedException extends AppException {
        public AccountLockedException(long minutesLeft) {
            super("Account is locked due to too many failed attempts. Try again in "
                    + minutesLeft + " minutes", HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    public static class UsernameAlreadyTakenException extends AppException {
        public UsernameAlreadyTakenException(String username) {
            super("Username already taken: " + username, HttpStatus.CONFLICT);
        }
    }

    public static class TwoFactorRequiredException extends AppException {
        public TwoFactorRequiredException() {
            super("Two-factor authentication code required", HttpStatus.UNAUTHORIZED);
        }
    }
}