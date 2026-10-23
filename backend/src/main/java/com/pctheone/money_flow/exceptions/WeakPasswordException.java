package com.pctheone.money_flow.exceptions;

//---- Password does not follow PasswordPolicy. AuthController answers 400 {"error": "WEAK_PASSWORD"}.
public class WeakPasswordException extends RuntimeException {
    public static final String CODE = "WEAK_PASSWORD";

    public WeakPasswordException() {
        super("Password does not meet the password rules");
    }
}
