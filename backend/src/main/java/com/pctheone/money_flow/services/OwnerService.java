package com.pctheone.money_flow.services;

import java.util.Optional;
import java.util.UUID;

public interface OwnerService {
    Optional<Integer> registerOwner(String name, String email, String pass);

    String generateTelegramInvite(Integer ownerId);

    boolean linkTelegramChat(UUID inviteUuid, Long chatId);
}
