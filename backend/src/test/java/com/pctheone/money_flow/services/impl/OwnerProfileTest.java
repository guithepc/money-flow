package com.pctheone.money_flow.services.impl;

import com.pctheone.money_flow.config.AdminAccess;
import com.pctheone.money_flow.dto.OwnerProfileDTO;
import com.pctheone.money_flow.entities.OwnerEntity;
import com.pctheone.money_flow.exceptions.InvalidCredentialsException;
import com.pctheone.money_flow.repositories.OwnerRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

public class OwnerProfileTest {

    OwnerRepository ownerRepository = Mockito.mock(OwnerRepository.class);

    @Test
    void profileReportsLinkedTelegram(){
        OwnerProfileDTO profile = serviceWith(owner(123L)).getProfile(1);
        Assertions.assertEquals("Gui", profile.getName());
        Assertions.assertEquals("gui@test.com", profile.getEmail());
        Assertions.assertTrue(profile.isTelegramLinked());
    }

    @Test
    void profileReportsUnlinkedTelegram(){
        Assertions.assertFalse(serviceWith(owner(null)).getProfile(1).isTelegramLinked());
    }

    @Test
    void missingOwnerThrows(){
        Mockito.when(ownerRepository.findById(1)).thenReturn(Optional.empty());
        OwnerServiceImpl service = new OwnerServiceImpl();
        service.ownerRepository = ownerRepository;
        service.adminAccess = new AdminAccess("");
        Assertions.assertThrows(InvalidCredentialsException.class, () -> service.getProfile(1));
    }

    @Test
    void profileReportsAdminFromEnv(){
        Assertions.assertFalse(serviceWith(owner(1L)).getProfile(1).isAdmin());
        Assertions.assertTrue(serviceWith(owner(1L), new AdminAccess("1")).getProfile(1).isAdmin());
    }

    private OwnerServiceImpl serviceWith(OwnerEntity owner){
        return serviceWith(owner, new AdminAccess(""));
    }

    private OwnerServiceImpl serviceWith(OwnerEntity owner, AdminAccess adminAccess){
        Mockito.when(ownerRepository.findById(1)).thenReturn(Optional.of(owner));
        OwnerServiceImpl service = new OwnerServiceImpl();
        service.ownerRepository = ownerRepository;
        service.adminAccess = adminAccess;
        return service;
    }

    private OwnerEntity owner(Long chatId){
        OwnerEntity owner = new OwnerEntity();
        owner.setOwnerId(1);
        owner.setName("Gui");
        owner.setEmail("gui@test.com");
        owner.setTelegramChatId(chatId);
        return owner;
    }
}
