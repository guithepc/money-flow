package com.pctheone.money_flow.controllers;

import com.pctheone.money_flow.dto.AccountCreateRequestDTO;
import com.pctheone.money_flow.enums.CurrencyEnum;
import com.pctheone.money_flow.exceptions.DuplicateAccountNameException;
import com.pctheone.money_flow.services.AccountService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

public class AccountControllerTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void duplicateAccountNameReturnsConflict() {
        AccountService accountService = Mockito.mock(AccountService.class);
        Mockito.when(accountService.addAccount(ArgumentMatchers.eq("Nubank"), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.anyInt()))
                .thenThrow(new DuplicateAccountNameException("Nubank"));
        AccountController controller = new AccountController();
        controller.accountService = accountService;
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(7, null, List.of()));

        AccountCreateRequestDTO request = new AccountCreateRequestDTO(" Nubank ", BigDecimal.TEN, CurrencyEnum.BRL);

        ResponseStatusException e = Assertions.assertThrows(ResponseStatusException.class, () -> controller.createAccount(request));
        Assertions.assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
    }
}
