package com.pctheone.money_flow.services.impl;

import com.pctheone.money_flow.entities.OwnerEntity;
import com.pctheone.money_flow.exceptions.InvalidCredentialsException;
import com.pctheone.money_flow.repositories.OwnerRepository;
import com.pctheone.money_flow.services.AuthService;
import com.pctheone.money_flow.services.OwnerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class OwnerServiceImpl implements OwnerService {

    private static final Logger log = LoggerFactory.getLogger(OwnerServiceImpl.class);

    @Value("${telegram.bot-username:}")
    private String telegramBotUsername;

    @Autowired
    OwnerRepository ownerRepository;

    @Autowired
    AuthService authService;

    @Override
    public Optional<Integer> registerOwner(String name, String email, String pass) {
        Optional<OwnerEntity> owner = ownerRepository.findByEmail(email);
        if (owner.isPresent()){
            throw new InvalidCredentialsException("Email already registered");
        }

        return authService.register(name, email, pass);
    }

    @Override
    public String generateTelegramInvite(Integer ownerId) {
        OwnerEntity owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> new InvalidCredentialsException("Owner not found"));

        UUID inviteUuid = UUID.randomUUID();
        owner.setInviteUuid(inviteUuid);
        owner.setInviteExpireDate(LocalDateTime.now().plusMinutes(1));
        ownerRepository.save(owner);

        log.info("Telegram invite generated for owner {}", ownerId);
        return "https://t.me/" + telegramBotUsername + "?start=" + inviteUuid;
    }

    @Override
    public boolean linkTelegramChat(UUID inviteUuid, Long chatId) {
        Optional<OwnerEntity> ownerOpt = ownerRepository.findByInviteUuid(inviteUuid);
        if (ownerOpt.isEmpty()) {
            log.info("Telegram invite not found for uuid {}", inviteUuid);
            return false;
        }

        OwnerEntity owner = ownerOpt.get();
        if (owner.getInviteExpireDate() == null || owner.getInviteExpireDate().isBefore(LocalDateTime.now())) {
            log.info("Telegram invite expired for owner {}", owner.getOwnerId());
            return false;
        }

        owner.setTelegramChatId(chatId);
        owner.setInviteUuid(null);
        owner.setInviteExpireDate(null);
        ownerRepository.save(owner);

        log.info("Telegram chat {} linked to owner {}", chatId, owner.getOwnerId());
        return true;
    }
}
