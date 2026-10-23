package com.pctheone.money_flow.controllers;

import com.pctheone.money_flow.dto.DeleteOwnerRequestDTO;
import com.pctheone.money_flow.dto.OwnerDataExportDTO;
import com.pctheone.money_flow.dto.OwnerProfileDTO;
import com.pctheone.money_flow.dto.TelegramInviteResponseDTO;
import com.pctheone.money_flow.dto.TermsAcceptanceRequestDTO;
import com.pctheone.money_flow.services.OwnerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/owner")
public class OwnerController {

    private static final Logger log = LoggerFactory.getLogger(OwnerController.class);

    @Autowired
    OwnerService ownerService;

    //---- Same flag as AuthController: the cleared refresh cookie must match the attributes it was set with.
    @Value("${app.auth.cookie-secure:true}")
    boolean cookieSecure;

    @GetMapping("/me")
    public ResponseEntity<OwnerProfileDTO> me(){
        Integer ownerId = (Integer) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(ownerService.getProfile(ownerId));
    }

    @PostMapping("/telegram-invite")
    public ResponseEntity<TelegramInviteResponseDTO> telegramInvite(){
        Integer ownerId = (Integer) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        log.info("Telegram invite requested by owner {}", ownerId);
        String link = ownerService.generateTelegramInvite(ownerId);
        return ResponseEntity.ok(new TelegramInviteResponseDTO(link));
    }

    @PostMapping("/terms-acceptance")
    public ResponseEntity<Void> acceptTerms(@RequestBody TermsAcceptanceRequestDTO request){
        Integer ownerId = (Integer) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        ownerService.acceptTerms(ownerId, request.getTermsVersion());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/export")
    public ResponseEntity<OwnerDataExportDTO> export(){
        Integer ownerId = (Integer) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        log.info("Data export requested by owner {}", ownerId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"money-flow-export.json\"")
                .body(ownerService.exportData(ownerId));
    }

    //---- Deletes the owner taken from the JWT and all their data. Confirmed with the current password.
    //---- refresh_token rows go with the owner (ON DELETE CASCADE); the response also clears the refresh cookie
    //---- so the browser does not keep a dead session.
    @DeleteMapping
    public ResponseEntity<Void> delete(@RequestBody DeleteOwnerRequestDTO request){
        Integer ownerId = (Integer) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        log.info("Account deletion requested by owner {}", ownerId);
        ownerService.deleteOwner(ownerId, request.getPassword());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, AuthController.clearRefreshCookie(cookieSecure).toString())
                .build();
    }
}
