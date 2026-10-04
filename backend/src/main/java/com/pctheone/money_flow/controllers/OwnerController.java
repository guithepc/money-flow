package com.pctheone.money_flow.controllers;

import com.pctheone.money_flow.dto.TelegramInviteResponseDTO;
import com.pctheone.money_flow.services.OwnerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/owner")
public class OwnerController {

    private static final Logger log = LoggerFactory.getLogger(OwnerController.class);

    @Autowired
    OwnerService ownerService;

    @PostMapping("/telegram-invite")
    public ResponseEntity<TelegramInviteResponseDTO> telegramInvite(){
        Integer ownerId = (Integer) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        log.info("Telegram invite requested by owner {}", ownerId);
        String link = ownerService.generateTelegramInvite(ownerId);
        return ResponseEntity.ok(new TelegramInviteResponseDTO(link));
    }
}
