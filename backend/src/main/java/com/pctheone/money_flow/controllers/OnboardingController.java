package com.pctheone.money_flow.controllers;

import com.pctheone.money_flow.services.WebOnboardingService;
import com.pctheone.money_flow.services.WebOnboardingService.Status;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

//---- PWA onboarding: language -> accounts (POST /api/accounts) -> first transaction (/api/capture) -> done.
@RestController
@RequestMapping("/api/onboarding")
public class OnboardingController {

    public record LanguageRequest(String language) {}

    @Autowired
    WebOnboardingService webOnboardingService;

    @GetMapping
    public ResponseEntity<Status> status() {
        return ResponseEntity.ok(webOnboardingService.status(ownerId()));
    }

    @PostMapping("/language")
    public ResponseEntity<Status> chooseLanguage(@RequestBody LanguageRequest request) {
        return ResponseEntity.ok(webOnboardingService.chooseLanguage(ownerId(), request == null ? null : request.language()));
    }

    @PostMapping("/accounts-done")
    public ResponseEntity<Status> accountsDone() {
        return ResponseEntity.ok(webOnboardingService.accountsDone(ownerId()));
    }

    @PostMapping("/finish")
    public ResponseEntity<Status> finish() {
        return ResponseEntity.ok(webOnboardingService.finish(ownerId()));
    }

    //---- Settings: switch the app language at any time (the onboarding step is untouched).
    @PutMapping("/language")
    public ResponseEntity<Status> changeLanguage(@RequestBody LanguageRequest request) {
        return ResponseEntity.ok(webOnboardingService.changeLanguage(ownerId(), request == null ? null : request.language()));
    }

    private static Integer ownerId() {
        return (Integer) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
