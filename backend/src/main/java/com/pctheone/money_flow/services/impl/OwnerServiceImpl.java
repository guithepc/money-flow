package com.pctheone.money_flow.services.impl;

import com.pctheone.money_flow.dto.OwnerDataExportDTO;
import com.pctheone.money_flow.dto.OwnerProfileDTO;
import com.pctheone.money_flow.entities.OwnerEntity;
import com.pctheone.money_flow.entities.TransactionsEntity;
import com.pctheone.money_flow.enums.LanguageEnum;
import com.pctheone.money_flow.exceptions.InvalidCredentialsException;
import com.pctheone.money_flow.exceptions.TermsNotAcceptedException;
import com.pctheone.money_flow.exceptions.WrongPasswordException;
import com.pctheone.money_flow.repositories.AccountRepository;
import com.pctheone.money_flow.repositories.CategoryRepository;
import com.pctheone.money_flow.repositories.OwnerRepository;
import com.pctheone.money_flow.repositories.RecurringPaymentRepository;
import com.pctheone.money_flow.repositories.TransactionsRepository;
import com.pctheone.money_flow.services.AuthService;
import com.pctheone.money_flow.services.OwnerService;
import com.pctheone.money_flow.services.telegram.BotMessages;
import com.pctheone.money_flow.services.telegram.TelegramReplySender;
import com.pctheone.money_flow.utils.LegalTerms;
import com.pctheone.money_flow.utils.PasswordPolicy;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OwnerServiceImpl implements OwnerService {

    private static final Logger log = LoggerFactory.getLogger(OwnerServiceImpl.class);

    //---- Long enough to switch to the phone and open Telegram.
    static final int INVITE_TTL_MINUTES = 15;

    @Value("${telegram.bot-username:}")
    private String telegramBotUsername;

    @Autowired
    OwnerRepository ownerRepository;

    @Autowired
    AuthService authService;

    @Autowired
    com.pctheone.money_flow.config.AdminAccess adminAccess;

    @Autowired
    TransactionsRepository transactionsRepository;

    @Autowired
    RecurringPaymentRepository recurringPaymentRepository;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    TelegramReplySender replySender;

    @Autowired
    BotMessages messages;

    @Override
    public Optional<Integer> registerOwner(String name, String email, String pass, String acceptedTermsVersion) {
        requireCurrentTerms(acceptedTermsVersion);
        PasswordPolicy.check(pass);
        Optional<OwnerEntity> owner = ownerRepository.findByEmail(email);
        if (owner.isPresent()){
            throw new InvalidCredentialsException("Email already registered");
        }

        return authService.register(name, email, pass, acceptedTermsVersion);
    }

    @Override
    public OwnerProfileDTO getProfile(Integer ownerId) {
        OwnerEntity owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> new InvalidCredentialsException("Owner not found"));
        return new OwnerProfileDTO(owner.getName(), owner.getEmail(), owner.getTelegramChatId() != null,
                LegalTerms.isCurrent(owner.getTermsVersion()), adminAccess.isAdmin(ownerId));
    }

    //---- Owners created before #28 (or before a new terms version) accept from the dashboard on their next login.
    @Override
    @Transactional
    public void acceptTerms(Integer ownerId, String termsVersion) {
        requireCurrentTerms(termsVersion);
        OwnerEntity owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> new InvalidCredentialsException("Owner not found"));
        owner.setTermsAcceptedAt(LocalDateTime.now());
        owner.setTermsVersion(termsVersion);
        ownerRepository.save(owner);
        log.info("Owner {} accepted terms version {}", ownerId, termsVersion);
    }

    //---- Deletes everything of the owner in one transaction. Order follows the foreign keys:
    //---- transactions -> recurring payments -> accounts / categories -> owner. refresh_token, voice_usage and
    //---- password_reset_token reference owner with ON DELETE CASCADE and go with the owner row.
    //---- telegram_processed_update holds only update ids (no owner link, no content) and expires by itself after 7 days.
    @Override
    @Transactional
    public void deleteOwner(Integer ownerId, String password) {
        //---- Row lock first: a bot update of this owner, or a row referencing it, waits for the deletion to finish.
        OwnerEntity owner = ownerRepository.findByIdForUpdate(ownerId)
                .orElseThrow(() -> new InvalidCredentialsException("Owner not found"));
        if (!authService.passwordMatches(owner, password)) {
            log.info("Account deletion rejected for owner {}: wrong password", ownerId);
            throw new WrongPasswordException("Wrong password");
        }

        //---- Farewell queued while the chat is still linked. TelegramReplySender delivers it only after the commit,
        //---- so nobody is told their data is gone if the deletion rolls back.
        if (owner.getTelegramChatId() != null) {
            LanguageEnum language = LanguageEnum.resolve(owner.getLanguage(), null);
            replySender.send(owner.getTelegramChatId(), messages.get(language, "account.deletedFarewell"));
        }

        int transactions = transactionsRepository.deleteAllByOwnerId(ownerId);
        int recurringPayments = recurringPaymentRepository.deleteAllByOwnerId(ownerId);
        int accounts = accountRepository.deleteAllByOwnerId(ownerId);
        int categories = categoryRepository.deleteAllByOwnerId(ownerId);
        ownerRepository.deleteByOwnerId(ownerId);

        log.info("Owner {} deleted: {} transactions, {} recurring payments, {} accounts, {} categories",
                ownerId, transactions, recurringPayments, accounts, categories);
    }

    @Override
    @Transactional
    public OwnerDataExportDTO exportData(Integer ownerId) {
        OwnerEntity owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> new InvalidCredentialsException("Owner not found"));

        OwnerDataExportDTO.Profile profile = new OwnerDataExportDTO.Profile(owner.getName(), owner.getEmail(),
                owner.getTelegramChatId(), owner.getLanguage(),
                owner.getOnboardingStep() != null ? owner.getOnboardingStep().name() : null,
                owner.getTermsAcceptedAt(), owner.getTermsVersion());

        List<OwnerDataExportDTO.Account> accounts = accountRepository.findByOwnerOwnerIdOrderByAccountIdAsc(ownerId).stream()
                .map(account -> new OwnerDataExportDTO.Account(account.getAccountId(), account.getDescription(), account.getCurrency(), account.getBalance()))
                .toList();

        List<OwnerDataExportDTO.Category> categories = categoryRepository.findByOwnerOwnerIdOrderByCategoryIdAsc(ownerId).stream()
                .map(category -> new OwnerDataExportDTO.Category(category.getCategoryId(), category.getDescription(), category.getType()))
                .toList();

        List<OwnerDataExportDTO.Transaction> transactions = transactionsRepository.findByOwnerOwnerIdOrderByTimestampAscTransactionsIdAsc(ownerId).stream()
                .map(OwnerServiceImpl::toExport)
                .toList();

        List<OwnerDataExportDTO.RecurringPayment> recurringPayments = recurringPaymentRepository.findByOwnerOwnerIdOrderByRecurringPaymentIdAsc(ownerId).stream()
                .map(recurring -> new OwnerDataExportDTO.RecurringPayment(recurring.getRecurringPaymentId(), recurring.getDescription(),
                        recurring.getAmount(), recurring.getFrequency(), Boolean.TRUE.equals(recurring.getIsActive()),
                        recurring.getRecurringStartDate(), recurring.getRecurringEndDate(),
                        recurring.getAccount().getAccountId(), recurring.getCategory().getCategoryId()))
                .toList();

        log.info("Data export generated for owner {}", ownerId);
        return new OwnerDataExportDTO(LocalDateTime.now(), profile, accounts, categories, transactions, recurringPayments);
    }

    private static OwnerDataExportDTO.Transaction toExport(TransactionsEntity transaction) {
        return new OwnerDataExportDTO.Transaction(transaction.getTransactionsId(), transaction.getTimestamp(),
                transaction.getOperationType(), transaction.getDescription(), transaction.getAmount(),
                transaction.getAccount().getCurrency(), transaction.getAccount().getAccountId(), transaction.getAccount().getDescription(),
                transaction.getCategory().getCategoryId(), transaction.getCategory().getDescription(),
                transaction.getRecurringPayment() != null ? transaction.getRecurringPayment().getRecurringPaymentId() : null);
    }

    private static void requireCurrentTerms(String termsVersion) {
        if (termsVersion == null || termsVersion.isBlank())
            throw new TermsNotAcceptedException("Terms of use and privacy policy must be accepted");
        if (!LegalTerms.isCurrent(termsVersion))
            throw new TermsNotAcceptedException("Outdated terms version: " + termsVersion);
    }

    @Override
    public String generateTelegramInvite(Integer ownerId) {
        OwnerEntity owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> new InvalidCredentialsException("Owner not found"));

        UUID inviteUuid = UUID.randomUUID();
        owner.setInviteUuid(inviteUuid);
        owner.setInviteExpireDate(LocalDateTime.now().plusMinutes(INVITE_TTL_MINUTES));
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
