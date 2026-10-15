package com.pctheone.money_flow.controllers;

import com.pctheone.money_flow.services.TransactionsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Proves the controller ALWAYS derives ownerId from the JWT principal in the
 * SecurityContext and propagates it to the service - never from the request.
 */
class TransactionsControllerIsolationTest {

    private static final Integer PRINCIPAL = 42;
    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final LocalDate END = LocalDate.of(2026, 1, 31);

    private TransactionsService service;
    private TransactionsController controller;

    @BeforeEach
    void setUp() {
        service = mock(TransactionsService.class);
        controller = new TransactionsController();
        controller.transactionsService = service;
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(PRINCIPAL, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listAllTransactions_usesPrincipalAsOwnerId() {
        ArgumentCaptor<Integer> owner = ArgumentCaptor.forClass(Integer.class);
        controller.listAllTransactions(START, END, null);
        verify(service).listAllTransactions(eq(START), eq(END), isNull(), owner.capture());
        assertThat(owner.getValue()).isEqualTo(PRINCIPAL);
    }

    @Test
    void listAllTransactions_withAccountId_stillUsesPrincipal() {
        ArgumentCaptor<Integer> owner = ArgumentCaptor.forClass(Integer.class);
        controller.listAllTransactions(START, END, 999);
        verify(service).listAllTransactions(eq(START), eq(END), eq(999), owner.capture());
        assertThat(owner.getValue()).isEqualTo(PRINCIPAL);
    }

    @Test
    void totalSpentByTime_usesPrincipalAsOwnerId() {
        ArgumentCaptor<Integer> owner = ArgumentCaptor.forClass(Integer.class);
        controller.totalSpentByTime(START, END, null);
        verify(service).totalSpentByTime(eq(START), eq(END), isNull(), owner.capture());
        assertThat(owner.getValue()).isEqualTo(PRINCIPAL);
    }

    @Test
    void totalIncomeByTime_usesPrincipalAsOwnerId() {
        ArgumentCaptor<Integer> owner = ArgumentCaptor.forClass(Integer.class);
        controller.totalIncomeByTime(START, END, null);
        verify(service).totalIncomeByTime(eq(START), eq(END), isNull(), owner.capture());
        assertThat(owner.getValue()).isEqualTo(PRINCIPAL);
    }

    @Test
    void totalRecurringByTime_usesPrincipalAsOwnerId() {
        ArgumentCaptor<Integer> owner = ArgumentCaptor.forClass(Integer.class);
        controller.totalRecurringByTime(START, END, null);
        verify(service).totalRecurringByTime(eq(START), eq(END), isNull(), owner.capture());
        assertThat(owner.getValue()).isEqualTo(PRINCIPAL);
    }

    @Test
    void totalSpentByCategory_usesPrincipalAsOwnerId() {
        ArgumentCaptor<Integer> owner = ArgumentCaptor.forClass(Integer.class);
        controller.totalSpentByCategory(START, END, 5);
        verify(service).totalSpentByCategoryAndTime(eq(START), eq(END), eq(5), owner.capture());
        assertThat(owner.getValue()).isEqualTo(PRINCIPAL);
    }

    @Test
    void totalSpentByCategoryRanked_usesPrincipalAsOwnerId() {
        ArgumentCaptor<Integer> owner = ArgumentCaptor.forClass(Integer.class);
        controller.totalSpentByCategoryRanked(START, END, null);
        verify(service).totalSpentInMonthByCategoryDesc(eq(START), eq(END), isNull(), owner.capture());
        assertThat(owner.getValue()).isEqualTo(PRINCIPAL);
    }

    @Test
    void monthlyIncomeExpense_usesPrincipalAsOwnerId() {
        ArgumentCaptor<Integer> owner = ArgumentCaptor.forClass(Integer.class);
        controller.monthlyIncomeExpense(START, END, null);
        verify(service).monthlyIncomeExpense(eq(START), eq(END), isNull(), owner.capture());
        assertThat(owner.getValue()).isEqualTo(PRINCIPAL);
    }

    @Test
    void dailyBalance_usesPrincipalAsOwnerId() {
        ArgumentCaptor<Integer> owner = ArgumentCaptor.forClass(Integer.class);
        controller.dailyBalance(START, END, null);
        verify(service).dailyBalance(eq(START), eq(END), isNull(), owner.capture());
        assertThat(owner.getValue()).isEqualTo(PRINCIPAL);
    }
}
