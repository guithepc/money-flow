package com.pctheone.money_flow.controllers;

import com.pctheone.money_flow.dto.TotalAmountDTO;
import com.pctheone.money_flow.services.TransactionsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/transactions")
public class TransactionsController {

    private static final Logger log = LoggerFactory.getLogger(TransactionsController.class);

    @Autowired
    TransactionsService transactionsService;

    @GetMapping("/total-spent-by-category")
    public ResponseEntity<TotalAmountDTO> totalSpentByCategory(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate, @RequestParam Integer categoryId){

        log.info("Total spent by category requested: startDate={}, endDate={}, categoryId={}", startDate, endDate, categoryId);
        BigDecimal result = transactionsService.totalSpentByCategoryAndTime(startDate, endDate,categoryId);
        log.info("Total spent by category processed - Amount: {}", result);
        TotalAmountDTO totalDto = new TotalAmountDTO(result);

        return ResponseEntity.ok(totalDto);
    }

    @GetMapping("/total-recurring-by-time")
    public ResponseEntity<TotalAmountDTO> totalRecurringByTime(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate){

        log.info("Total recurring by time requested: startDate={}, endDate={}", startDate, endDate);
        BigDecimal result = transactionsService.totalRecurringByTime(startDate, endDate);
        log.info("Total recurring by time processed - Amount: {}", result);
        TotalAmountDTO totalDto = new TotalAmountDTO(result);

        return ResponseEntity.ok(totalDto);
    }

    @GetMapping("/total-income-by-time")
    public ResponseEntity<TotalAmountDTO> totalIncomeByTime(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate){

        log.info("Total income by time requested: startDate={}, endDate={}", startDate, endDate);
        BigDecimal result = transactionsService.totalIncomeByTime(startDate, endDate);
        log.info("Total income processed - Amount: {}", result);
        TotalAmountDTO totalDto = new TotalAmountDTO(result);

        return ResponseEntity.ok(totalDto);
    }
}
