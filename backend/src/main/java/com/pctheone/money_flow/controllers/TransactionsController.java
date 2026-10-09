package com.pctheone.money_flow.controllers;

import com.pctheone.money_flow.dto.*;
import com.pctheone.money_flow.services.TransactionsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;


@RestController
@RequestMapping("/api/transactions")
public class TransactionsController {

    private static final Logger log = LoggerFactory.getLogger(TransactionsController.class);

    @Autowired
    TransactionsService transactionsService;

    private Integer getOwnerId() {
        return (Integer) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    @GetMapping("/total-spent-by-category")
    public ResponseEntity<TotalAmountDTO> totalSpentByCategory(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate, @RequestParam Integer categoryId){
        Integer ownerId = getOwnerId();
        log.info("Total spent by category requested: startDate={}, endDate={}, categoryId={}, ownerId={}", startDate, endDate, categoryId, ownerId);
        BigDecimal result = transactionsService.totalSpentByCategoryAndTime(startDate, endDate, categoryId, ownerId);
        log.info("Total spent by category processed - Amount: {}", result);
        TotalAmountDTO totalDto = new TotalAmountDTO(result);

        return ResponseEntity.ok(totalDto);
    }

    @GetMapping("/total-recurring-by-time")
    public ResponseEntity<TotalAmountDTO> totalRecurringByTime(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate, @RequestParam(required = false) Integer accountId){
        Integer ownerId = getOwnerId();
        log.info("Total recurring by time requested: startDate={}, endDate={}, ownerId={}", startDate, endDate, ownerId);
        BigDecimal result = transactionsService.totalRecurringByTime(startDate, endDate, accountId, ownerId);
        log.info("Total recurring by time processed - Amount: {}", result);
        TotalAmountDTO totalDto = new TotalAmountDTO(result);

        return ResponseEntity.ok(totalDto);
    }

    @GetMapping("/total-income-by-time")
    public ResponseEntity<TotalAmountDTO> totalIncomeByTime(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate, @RequestParam(required = false) Integer accountId){
        Integer ownerId = getOwnerId();
        log.info("Total income by time requested: startDate={}, endDate={}, ownerId={}", startDate, endDate, ownerId);
        BigDecimal result = transactionsService.totalIncomeByTime(startDate, endDate, accountId, ownerId);
        log.info("Total income processed - Amount: {}", result);
        TotalAmountDTO totalDto = new TotalAmountDTO(result);

        return ResponseEntity.ok(totalDto);
    }

    @GetMapping("/total-spent-by-time")
    public ResponseEntity<TotalAmountDTO> totalSpentByTime(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate, @RequestParam(required = false) Integer accountId){
        Integer ownerId = getOwnerId();
        log.info("Total Spent by time requested: startDate={}, endDate={}, ownerId={}", startDate, endDate, ownerId);
        BigDecimal result = transactionsService.totalSpentByTime(startDate, endDate, accountId, ownerId);
        log.info("Total Spent processed - Amount: {}", result);
        TotalAmountDTO totalDto = new TotalAmountDTO(result);

        return ResponseEntity.ok(totalDto);
    }

    @GetMapping("/total-spent-ranked-by-category")
    public ResponseEntity<List<AmountAndCategoryDTO>> totalSpentByCategoryRanked(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate, @RequestParam(required = false) Integer accountId){
        Integer ownerId = getOwnerId();
        log.info("Total spent ranked by category requested: startDate={}, endDate={}, ownerId={}", startDate, endDate, ownerId);
        List<AmountAndCategoryDTO> result = transactionsService.totalSpentInMonthByCategoryDesc(startDate, endDate, accountId, ownerId);
        log.info("Total spent ranked by category - Total spent ranked by category {}", result);

        return ResponseEntity.ok(result);
    }

    @GetMapping
    public ResponseEntity<List<TransactionDTO>> listAllTransactions(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate, @RequestParam(required = false) Integer accountId){
        Integer ownerId = getOwnerId();
        log.info("All transactions requested by ownerId={}", ownerId);
        List<TransactionDTO> result = transactionsService.listAllTransactions(startDate, endDate, accountId, ownerId);
        log.info("All transactions list processed successfully");
        return ResponseEntity.ok(result);
    }

    @GetMapping("/income-expense-monthly")
    public ResponseEntity<List<MonthlyIncomeExpenseDTO>> monthlyIncomeExpense(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate, @RequestParam(required = false) Integer accountId){
        Integer ownerId = getOwnerId();
        log.info("Monthly income expense requested: startDate={}, endDate={}, accountId={}, ownerId={}", startDate, endDate, accountId, ownerId);
        List<MonthlyIncomeExpenseDTO> result = transactionsService.monthlyIncomeExpense(startDate, endDate, accountId, ownerId);
        log.info("Monthly income expense responded");
        return ResponseEntity.ok(result);
    }

    @GetMapping("/history-balance")
    public ResponseEntity<List<DailyBalanceDTO>> dailyBalance(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate, @RequestParam(required = false) Integer accountId){
        Integer ownerId = getOwnerId();
        log.info("Daily balance requested: startDate={}, endDate={}, accountId={}, ownerId={}", startDate, endDate, accountId, ownerId);
        List<DailyBalanceDTO> result = transactionsService.dailyBalance(startDate, endDate, accountId, ownerId);
        log.info("Daily balance responded.");
        return ResponseEntity.ok(result);
    }

}
