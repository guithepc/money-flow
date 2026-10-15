package com.pctheone.money_flow.controllers;

import com.pctheone.money_flow.dto.AccountCreateRequestDTO;
import com.pctheone.money_flow.dto.AccountDTO;
import com.pctheone.money_flow.exceptions.DuplicateAccountNameException;
import com.pctheone.money_flow.services.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    @Autowired
    AccountService accountService;

    @GetMapping
    ResponseEntity<List<AccountDTO>> accountList(){
        Integer ownerId = (Integer) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(accountService.listAllAccounts(ownerId));
    }

    @PostMapping
    ResponseEntity<AccountDTO> createAccount(@RequestBody AccountCreateRequestDTO request){
        Integer ownerId = (Integer) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (request.getDescription() == null || request.getDescription().isBlank() || request.getBalance() == null || request.getCurrency() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "description, balance and currency are required");
        }
        AccountDTO account;
        try {
            account = accountService.addAccount(request.getDescription().trim(), request.getBalance(), request.getCurrency(), ownerId);
        } catch (DuplicateAccountNameException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this name already exists");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(account);
    }

}
