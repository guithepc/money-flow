package com.pctheone.money_flow.controllers;

import com.pctheone.money_flow.dto.AccountDTO;
import com.pctheone.money_flow.services.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    @Autowired
    AccountService accountService;

    @GetMapping
    ResponseEntity<List<AccountDTO>> accountList(){
        return ResponseEntity.ok(accountService.listAllAccounts());
    }

}
