package com.pctheone.money_flow.controllers;

import com.pctheone.money_flow.services.TransactionsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/transactions")
public class TransactionsController {

    @Autowired
    TransactionsService transactionsService;

    @GetMapping("/total-spent-by-category")
    public ResponseEntity<BigDecimal> totalSpentByCategory(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate, @RequestParam Integer categoryId){
        return ResponseEntity.ok(transactionsService.totalSpentByCategoryAndTime(startDate, endDate,categoryId));

    }
}
