package com.pctheone.money_flow.services;

import com.pctheone.money_flow.dto.OwnerProfileDTO;

import java.util.Optional;
import java.util.UUID;

public interface OwnerService {
    Optional<Integer> registerOwner(String name, String email, String pass);

    OwnerProfileDTO getProfile(Integer ownerId);

    String generateTelegramInvite(Integer ownerId);

    boolean linkTelegramChat(UUID inviteUuid, Long chatId);
}
