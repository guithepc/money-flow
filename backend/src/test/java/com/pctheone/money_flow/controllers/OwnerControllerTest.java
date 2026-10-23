package com.pctheone.money_flow.controllers;

import com.pctheone.money_flow.dto.DeleteOwnerRequestDTO;
import com.pctheone.money_flow.exceptions.WrongPasswordException;
import com.pctheone.money_flow.services.OwnerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

public class OwnerControllerTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deleteUsesTheOwnerFromTheJwt() {
        OwnerService ownerService = Mockito.mock(OwnerService.class);
        OwnerController controller = new OwnerController();
        controller.ownerService = ownerService;
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(7, null, List.of()));

        Assertions.assertEquals(HttpStatus.NO_CONTENT, controller.delete(new DeleteOwnerRequestDTO("secret123")).getStatusCode());
        Mockito.verify(ownerService).deleteOwner(7, "secret123");
    }

    //---- The refresh_token rows are gone by cascade; the browser cookie is cleared by the same response.
    @Test
    void deleteClearsTheRefreshCookie() {
        OwnerController controller = new OwnerController();
        controller.ownerService = Mockito.mock(OwnerService.class);
        controller.cookieSecure = true;
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(7, null, List.of()));

        ResponseEntity<Void> response = controller.delete(new DeleteOwnerRequestDTO("secret123"));

        String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        Assertions.assertNotNull(cookie);
        Assertions.assertTrue(cookie.startsWith(AuthController.REFRESH_COOKIE + "=;"), cookie);
        Assertions.assertTrue(cookie.contains("Max-Age=0"), cookie);
        Assertions.assertTrue(cookie.contains("Path=" + AuthController.REFRESH_COOKIE_PATH), cookie);
        Assertions.assertTrue(cookie.contains("HttpOnly"), cookie);
        Assertions.assertTrue(cookie.contains("Secure"), cookie);
    }

    //---- Wrongly typed password: nothing deleted, so the session (and its cookie) stays.
    @Test
    void wrongPasswordDoesNotClearTheCookie() {
        OwnerService ownerService = Mockito.mock(OwnerService.class);
        Mockito.doThrow(new WrongPasswordException("Wrong password")).when(ownerService).deleteOwner(7, "typo");
        OwnerController controller = new OwnerController();
        controller.ownerService = ownerService;
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(7, null, List.of()));

        Assertions.assertThrows(WrongPasswordException.class, () -> controller.delete(new DeleteOwnerRequestDTO("typo")));
    }

    //---- Not 401: the frontend logs out on any 401, and a typo in the password must not end the session.
    @Test
    void wrongPasswordIsForbiddenNotUnauthorized() {
        Assertions.assertEquals(HttpStatus.FORBIDDEN, WrongPasswordException.class.getAnnotation(ResponseStatus.class).value());
    }
}
