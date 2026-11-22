package com.pctheone.money_flow.services;

import com.pctheone.money_flow.dto.OwnerDataExportDTO;
import com.pctheone.money_flow.dto.OwnerProfileDTO;

import java.util.Optional;
import java.util.UUID;

public interface OwnerService {
    //---- Also seeds the base categories (Portuguese, the dashboard's language), like the bot onboarding does.
    Optional<Integer> registerOwner(String name, String email, String pass, String acceptedTermsVersion);

    //---- On login: an owner with no categories yet (web sign-up before they were seeded) gets the base ones,
    //---- in the bot language they chose or Portuguese.
    void ensureBaseCategories(Integer ownerId);

    OwnerProfileDTO getProfile(Integer ownerId);

    String generateTelegramInvite(Integer ownerId);

    boolean linkTelegramChat(UUID inviteUuid, Long chatId);

    void acceptTerms(Integer ownerId, String termsVersion);

    void deleteOwner(Integer ownerId, String password);

    OwnerDataExportDTO exportData(Integer ownerId);
}
