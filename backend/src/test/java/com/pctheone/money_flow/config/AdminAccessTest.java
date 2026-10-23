package com.pctheone.money_flow.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AdminAccessTest {

    @Test
    void parsesTheListAndTrims() {
        AdminAccess access = new AdminAccess("1, 4 ,7");
        Assertions.assertTrue(access.isAdmin(1));
        Assertions.assertTrue(access.isAdmin(4));
        Assertions.assertTrue(access.isAdmin(7));
        Assertions.assertFalse(access.isAdmin(2));
    }

    @Test
    void emptyConfigMeansNoAdmins() {
        AdminAccess access = new AdminAccess("");
        Assertions.assertFalse(access.isAdmin(1));
        Assertions.assertFalse(access.isAdmin(null));
    }

    @Test
    void nullOwnerIsNeverAdmin() {
        Assertions.assertFalse(new AdminAccess("1").isAdmin(null));
    }

    @Test
    void requireThrowsForNonAdmin() {
        AdminAccess access = new AdminAccess("1");
        Assertions.assertDoesNotThrow(() -> access.require(1));
        ResponseStatusException e = Assertions.assertThrows(ResponseStatusException.class, () -> access.require(2));
        Assertions.assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
    }
}
