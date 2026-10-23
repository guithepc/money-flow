package com.pctheone.money_flow.exceptions;

import lombok.Getter;

//---- Rejected reset token. AuthController answers 400 {"error": "<reason>"}.
@Getter
public class PasswordResetException extends RuntimeException {

    public enum Reason {
        TOKEN_INVALID,
        TOKEN_EXPIRED,
        TOKEN_USED
    }

    private final Reason reason;

    public PasswordResetException(Reason reason) {
        super("Password reset rejected: " + reason);
        this.reason = reason;
    }
}
