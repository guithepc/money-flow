package com.pctheone.money_flow.services;

import com.pctheone.money_flow.dto.OnboardingAccountDraftDTO;
import com.pctheone.money_flow.entities.OwnerEntity;
import com.pctheone.money_flow.utils.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//---- owner.onboarding_draft <-> OnboardingAccountDraftDTO.
final class OnboardingDrafts {

    private static final Logger log = LoggerFactory.getLogger(OnboardingDrafts.class);

    private OnboardingDrafts() {
    }

    //---- An empty draft when there is none (or it can't be read).
    static OnboardingAccountDraftDTO read(OwnerEntity owner) {
        if (owner.getOnboardingDraft() == null)
            return new OnboardingAccountDraftDTO();
        try {
            return Json.MAPPER.readValue(owner.getOnboardingDraft(), OnboardingAccountDraftDTO.class);
        } catch (Exception e) {
            log.error("Discarding unreadable onboarding draft of owner {}: {}", owner.getOwnerId(), e.getMessage());
            return new OnboardingAccountDraftDTO();
        }
    }

    static void write(OwnerEntity owner, OnboardingAccountDraftDTO draft) {
        owner.setOnboardingDraft(Json.MAPPER.writeValueAsString(draft));
    }
}
