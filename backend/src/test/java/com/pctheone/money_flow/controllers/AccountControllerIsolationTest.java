package com.pctheone.money_flow.controllers;

import com.pctheone.money_flow.services.AccountService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Proves AccountController derives ownerId from the JWT principal and passes it
 * to the service, so a client can only ever list its own accounts.
 */
class AccountControllerIsolationTest {

    private static final Integer PRINCIPAL = 42;

    private AccountService service;
    private AccountController controller;

    @BeforeEach
    void setUp() {
        service = mock(AccountService.class);
        controller = new AccountController();
        controller.accountService = service;
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(PRINCIPAL, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void accountList_usesPrincipalAsOwnerId() {
        ArgumentCaptor<Integer> owner = ArgumentCaptor.forClass(Integer.class);
        controller.accountList();
        verify(service).listAllAccounts(owner.capture());
        assertThat(owner.getValue()).isEqualTo(PRINCIPAL);
    }
}
