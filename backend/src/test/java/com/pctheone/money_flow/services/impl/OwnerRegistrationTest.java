package com.pctheone.money_flow.services.impl;

import com.pctheone.money_flow.exceptions.WeakPasswordException;
import com.pctheone.money_flow.repositories.OwnerRepository;
import com.pctheone.money_flow.services.AuthService;
import com.pctheone.money_flow.utils.LegalTerms;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.util.Optional;

public class OwnerRegistrationTest {

    OwnerRepository ownerRepository = Mockito.mock(OwnerRepository.class);
    AuthService authService = Mockito.mock(AuthService.class);

    @Test
    void weakPasswordIsRejectedBeforeAnythingIsSaved() {
        OwnerServiceImpl service = service();

        for (String weak : new String[]{null, "", "        ", "1234567", "a".repeat(73)})
            Assertions.assertThrows(WeakPasswordException.class, () -> service.registerOwner("G", "g@example.com", weak, LegalTerms.CURRENT_VERSION), String.valueOf(weak));

        Mockito.verifyNoInteractions(authService);
    }

    @Test
    void validPasswordRegisters() {
        Mockito.when(ownerRepository.findByEmail("g@example.com")).thenReturn(Optional.empty());
        Mockito.when(authService.register(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Optional.of(1));

        Assertions.assertEquals(Optional.of(1), service().registerOwner("G", "g@example.com", "12345678", LegalTerms.CURRENT_VERSION));
    }

    private OwnerServiceImpl service() {
        OwnerServiceImpl service = new OwnerServiceImpl();
        service.ownerRepository = ownerRepository;
        service.authService = authService;
        return service;
    }
}
