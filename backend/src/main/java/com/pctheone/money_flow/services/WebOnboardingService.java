package com.pctheone.money_flow.services;

import com.pctheone.money_flow.dto.CategoryDTO;
import com.pctheone.money_flow.entities.OwnerEntity;
import com.pctheone.money_flow.enums.CategoryTypeEnum;
import com.pctheone.money_flow.enums.LanguageEnum;
import com.pctheone.money_flow.enums.OnboardingStepEnum;
import com.pctheone.money_flow.repositories.AccountRepository;
import com.pctheone.money_flow.repositories.OwnerRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

//---- The PWA's onboarding, on the same owner state as the bot's (onboarding_step, language), so it resumes where the
//---- person stopped in either channel. Same owner row lock as OnboardingStateMachine: bot and PWA never interleave.
//---- The PWA has four steps: LANGUAGE -> ACCOUNTS -> FIRST_TRANSACTION -> DONE.
@Service
public class WebOnboardingService {

    private static final Logger log = LoggerFactory.getLogger(WebOnboardingService.class);

    public enum Step { LANGUAGE, ACCOUNTS, FIRST_TRANSACTION, DONE }

    public record Category(String name, CategoryTypeEnum type) {}

    public record Status(Step step, String language, int accounts, List<Category> categories) {}

    @Autowired
    OwnerRepository ownerRepository;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    CategoryService categoryService;

    public Status status(Integer ownerId) {
        OwnerEntity owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Owner not found"));
        return status(owner);
    }

    //---- First time: seeds the base categories in that language (only if the owner has none) and moves to accounts.
    //---- Later (back button, or after the bot already passed this step) it only changes the language.
    @Transactional
    public Status chooseLanguage(Integer ownerId, String code) {
        LanguageEnum language = parseLanguage(code);
        OwnerEntity owner = lockOwner(ownerId);
        owner.setLanguage(language.code());
        categoryService.seedBaseCategoriesIfNone(ownerId, language);
        if (owner.getOnboardingStep() == OnboardingStepEnum.LANGUAGE) {
            owner.setOnboardingStep(OnboardingStepEnum.ACCOUNT);
            owner.setOnboardingDraft(null);
        }
        ownerRepository.save(owner);
        log.info("Owner {} chose language {} on the web", ownerId, language.code());
        return status(owner);
    }

    //---- "Continue" after the accounts: needs at least one, then the first transaction closes the onboarding.
    @Transactional
    public Status accountsDone(Integer ownerId) {
        OwnerEntity owner = lockOwner(ownerId);
        requireAccount(ownerId);
        if (step(owner.getOnboardingStep()) == Step.ACCOUNTS || step(owner.getOnboardingStep()) == Step.LANGUAGE) {
            owner.setOnboardingStep(OnboardingStepEnum.TUTORIAL);
            owner.setOnboardingDraft(null);
            ownerRepository.save(owner);
        }
        return status(owner);
    }

    //---- "Skip" on the first-transaction screen.
    @Transactional
    public Status finish(Integer ownerId) {
        OwnerEntity owner = lockOwner(ownerId);
        requireAccount(ownerId);
        complete(owner);
        return status(owner);
    }

    //---- A transaction registered from the PWA finishes the onboarding when the owner was on that step (bot: same rule).
    @Transactional
    public void transactionRegistered(Integer ownerId) {
        OwnerEntity owner = lockOwner(ownerId);
        if (owner.getOnboardingStep() == OnboardingStepEnum.TUTORIAL)
            complete(owner);
    }

    //---- Settings: language only, the onboarding step stays as it is.
    @Transactional
    public Status changeLanguage(Integer ownerId, String code) {
        LanguageEnum language = parseLanguage(code);
        OwnerEntity owner = lockOwner(ownerId);
        owner.setLanguage(language.code());
        ownerRepository.save(owner);
        return status(owner);
    }

    static Step step(OnboardingStepEnum step) {
        return switch (step) {
            case LANGUAGE -> Step.LANGUAGE;
            case ACCOUNT, ACCOUNT_CONFIRM, MORE_ACCOUNTS -> Step.ACCOUNTS;
            case TUTORIAL -> Step.FIRST_TRANSACTION;
            case DONE -> Step.DONE;
        };
    }

    private void complete(OwnerEntity owner) {
        if (owner.getOnboardingStep() == OnboardingStepEnum.DONE)
            return;
        owner.setOnboardingStep(OnboardingStepEnum.DONE);
        owner.setOnboardingDraft(null);
        ownerRepository.save(owner);
        log.info("Onboarding finished on the web for owner {}", owner.getOwnerId());
    }

    private void requireAccount(Integer ownerId) {
        if (accountRepository.findByOwnerOwnerIdOrderByAccountIdAsc(ownerId).isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Create an account first");
    }

    private Status status(OwnerEntity owner) {
        Integer ownerId = owner.getOwnerId();
        List<Category> categories = categoryService.listAllCategories(ownerId).stream()
                .map((CategoryDTO c) -> new Category(c.getCategoryDescription(), c.getType()))
                .toList();
        return new Status(step(owner.getOnboardingStep()), owner.getLanguage(),
                accountRepository.findByOwnerOwnerIdOrderByAccountIdAsc(ownerId).size(), categories);
    }

    private static LanguageEnum parseLanguage(String code) {
        LanguageEnum language = LanguageEnum.fromCode(code);
        if (language == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Language must be pt or en");
        return language;
    }

    private OwnerEntity lockOwner(Integer ownerId) {
        return ownerRepository.findByIdForUpdate(ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Owner not found"));
    }
}
