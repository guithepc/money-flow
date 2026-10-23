package com.pctheone.money_flow.utils;

import com.pctheone.money_flow.exceptions.WeakPasswordException;

import java.nio.charset.StandardCharsets;

//---- Password rules shared by registration and password reset.
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    //---- bcrypt only uses the first 72 bytes; longer passwords would be silently truncated.
    public static final int MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    public static boolean isValid(String password) {
        if (password == null || password.isBlank())
            return false;
        return password.codePointCount(0, password.length()) >= MIN_LENGTH
                && password.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
    }

    public static void check(String password) {
        if (!isValid(password))
            throw new WeakPasswordException();
    }
}
