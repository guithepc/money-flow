package com.pctheone.money_flow.services;

import com.pctheone.money_flow.entities.OwnerEntity;
import com.pctheone.money_flow.entities.PasswordResetTokenEntity;
import com.pctheone.money_flow.enums.OnboardingStepEnum;
import com.pctheone.money_flow.repositories.OwnerRepository;
import com.pctheone.money_flow.repositories.PasswordResetTokenRepository;
import com.pctheone.money_flow.services.telegram.BotMessages;
import com.pctheone.money_flow.services.telegram.TelegramReplySender;
import com.pctheone.money_flow.utils.MutableClock;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

//---- PasswordResetService wired without a database: repositories backed by in-memory maps, a controllable
//---- clock and a direct executor, so the "async" part runs before requestReset returns.
public class PasswordResetTestFixture {

    public static final String FRONTEND_URL = "https://moneyflow.test";

    //---- Starts at the real time: JWTs built with this clock are still checked for expiry against the real time.
    public final MutableClock clock = new MutableClock(Instant.now().truncatedTo(ChronoUnit.SECONDS));
    public final OwnerRepository ownerRepository = Mockito.mock(OwnerRepository.class);
    public final PasswordResetTokenRepository tokenRepository = Mockito.mock(PasswordResetTokenRepository.class);
    public final TelegramReplySender replySender = Mockito.mock(TelegramReplySender.class);
    public final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    public final BotMessages messages = new BotMessages();
    public final PasswordResetRateLimiter rateLimiter = new PasswordResetRateLimiter();
    public final PasswordResetService service = new PasswordResetService();

    //---- token_hash -> row, i.e. the password_reset_token table.
    public final Map<String, PasswordResetTokenEntity> tokens = new ConcurrentHashMap<>();
    public final List<OwnerEntity> owners = new ArrayList<>();
    private final AtomicInteger nextTokenId = new AtomicInteger(1);

    public PasswordResetTestFixture() {
        rateLimiter.clock = clock;

        service.frontendUrl = FRONTEND_URL;
        service.ownerRepository = ownerRepository;
        service.tokenRepository = tokenRepository;
        service.rateLimiter = rateLimiter;
        service.passwordEncoder = passwordEncoder;
        service.replySender = replySender;
        service.messages = messages;
        service.clock = clock;
        service.transactionTemplate = new TransactionTemplate(Mockito.mock(PlatformTransactionManager.class));
        service.executor = Runnable::run;

        Mockito.when(tokenRepository.save(ArgumentMatchers.any())).thenAnswer(invocation -> store(invocation.getArgument(0)));
        Mockito.when(tokenRepository.saveAndFlush(ArgumentMatchers.any())).thenAnswer(invocation -> store(invocation.getArgument(0)));
        Mockito.when(tokenRepository.findByTokenHashForUpdate(ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(tokens.get(invocation.<String>getArgument(0))));
        Mockito.when(tokenRepository.deleteUnusedByOwnerId(ArgumentMatchers.anyInt())).thenAnswer(invocation -> {
            Integer ownerId = invocation.getArgument(0);
            int before = tokens.size();
            tokens.values().removeIf(token -> token.getOwnerId().equals(ownerId) && token.getUsedAt() == null);
            return before - tokens.size();
        });

        Mockito.when(ownerRepository.findAllByEmailIgnoreCase(ArgumentMatchers.anyString())).thenAnswer(invocation -> {
            String email = invocation.getArgument(0);
            return owners.stream().filter(owner -> owner.getEmail().equalsIgnoreCase(email)).toList();
        });
        Mockito.when(ownerRepository.findByEmail(ArgumentMatchers.anyString())).thenAnswer(invocation ->
                owners.stream().filter(owner -> owner.getEmail().equals(invocation.getArgument(0))).findFirst());
        Mockito.when(ownerRepository.findByIdForUpdate(ArgumentMatchers.anyInt())).thenAnswer(invocation -> findOwner(invocation.getArgument(0)));
        Mockito.when(ownerRepository.findById(ArgumentMatchers.anyInt())).thenAnswer(invocation -> findOwner(invocation.getArgument(0)));
        Mockito.when(ownerRepository.save(ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    public OwnerEntity addOwner(int ownerId, String email, Long chatId, String language) {
        OwnerEntity owner = new OwnerEntity();
        owner.setOwnerId(ownerId);
        owner.setName("Owner " + ownerId);
        owner.setEmail(email);
        owner.setTelegramChatId(chatId);
        owner.setLanguage(language);
        owner.setPasswordHash(passwordEncoder.encode("old-password"));
        owner.setOnboardingStep(OnboardingStepEnum.DONE);
        owners.add(owner);
        return owner;
    }

    //---- Raw token from the last link sent to the chat.
    public String lastTokenSentTo(Long chatId) {
        ArgumentCaptor<String> texts = ArgumentCaptor.forClass(String.class);
        Mockito.verify(replySender, Mockito.atLeastOnce()).send(ArgumentMatchers.eq(chatId), texts.capture());
        String marker = "/reset-password?token=";
        String link = texts.getAllValues().reversed().stream().filter(text -> text.contains(marker)).findFirst()
                .orElseThrow(() -> new AssertionError("No reset link sent to " + chatId));
        String token = link.substring(link.indexOf(marker) + marker.length());
        return token.split("\\s")[0];
    }

    private Optional<OwnerEntity> findOwner(Integer ownerId) {
        return owners.stream().filter(owner -> owner.getOwnerId().equals(ownerId)).findFirst();
    }

    private PasswordResetTokenEntity store(PasswordResetTokenEntity token) {
        if (token.getId() == null)
            token.setId(nextTokenId.getAndIncrement());
        tokens.put(token.getTokenHash(), token);
        return token;
    }
}
