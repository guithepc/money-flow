package com.pctheone.money_flow.services;

import com.pctheone.money_flow.entities.OwnerEntity;
import com.pctheone.money_flow.enums.CurrencyEnum;
import com.pctheone.money_flow.enums.OnboardingStepEnum;
import com.pctheone.money_flow.exceptions.DuplicateAccountNameException;
import com.pctheone.money_flow.repositories.OwnerRepository;
import com.pctheone.money_flow.services.impl.AccountServiceImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.UUID;

//---- Unique index account_owner_lower_description_key (V6).
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AccountServiceImpl.class)
public class AccountNameUniquenessTest {

    @Autowired
    OwnerRepository ownerRepository;

    @Autowired
    AccountService accountService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    Integer ownerA;
    Integer ownerB;

    @BeforeEach
    void setUp() {
        ownerA = createOwner("a");
        ownerB = createOwner("b");
    }

    @Test
    void sameNameIgnoringCaseIsRejected() {
        accountService.addAccount("Nubank", new BigDecimal("10"), CurrencyEnum.BRL, ownerA);

        DuplicateAccountNameException e = Assertions.assertThrows(DuplicateAccountNameException.class,
                () -> accountService.addAccount("nubank", new BigDecimal("20"), CurrencyEnum.BRL, ownerA));
        Assertions.assertEquals("nubank", e.getName());
    }

    @Test
    void differentOwnersCanUseTheSameName() {
        accountService.addAccount("Nubank", new BigDecimal("10"), CurrencyEnum.BRL, ownerA);

        Assertions.assertDoesNotThrow(() -> accountService.addAccount("NUBANK", new BigDecimal("10"), CurrencyEnum.BRL, ownerB));
    }

    @Test
    void databaseRejectsDuplicatesThatSkipTheService() {
        jdbcTemplate.update("INSERT INTO account (owner_id, description, balance) VALUES (?, 'Wise', 0)", ownerA);

        DataIntegrityViolationException e = Assertions.assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("INSERT INTO account (owner_id, description, balance) VALUES (?, 'WISE', 0)", ownerA));
        Assertions.assertTrue(e.getMostSpecificCause().getMessage().contains("account_owner_lower_description_key"), e.getMessage());
    }

    private Integer createOwner(String prefix) {
        String email = prefix + "-" + UUID.randomUUID() + "@uniqueness.test";
        return ownerRepository.save(new OwnerEntity(null, prefix, email, null, "x".repeat(60), null, null, null, OnboardingStepEnum.DONE, null)).getOwnerId();
    }
}
