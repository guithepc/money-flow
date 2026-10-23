package com.pctheone.money_flow.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PasswordPolicyTest {

    @Test
    void acceptsEightCharactersUpToSeventyTwoBytes() {
        Assertions.assertTrue(PasswordPolicy.isValid("12345678"));
        Assertions.assertTrue(PasswordPolicy.isValid("a".repeat(72)));
        Assertions.assertTrue(PasswordPolicy.isValid("ç".repeat(36)));
    }

    @Test
    void rejectsShortBlankOrTooLongPasswords() {
        Assertions.assertFalse(PasswordPolicy.isValid(null));
        Assertions.assertFalse(PasswordPolicy.isValid(""));
        Assertions.assertFalse(PasswordPolicy.isValid("         "));
        Assertions.assertFalse(PasswordPolicy.isValid("1234567"));
        Assertions.assertFalse(PasswordPolicy.isValid("a".repeat(73)));
        //---- 37 characters but 74 bytes in UTF-8.
        Assertions.assertFalse(PasswordPolicy.isValid("ç".repeat(37)));
    }
}
