package com.pctheone.money_flow.exceptions;

//---- More than PasswordResetRateLimiter.MAX_REQUESTS reset requests for the same email in the window. Answered with 429.
public class PasswordResetRateLimitedException extends RuntimeException {
    public PasswordResetRateLimitedException() {
        super("Too many password reset requests");
    }
}
