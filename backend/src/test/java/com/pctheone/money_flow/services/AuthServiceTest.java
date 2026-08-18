package com.pctheone.money_flow.services;

import com.pctheone.money_flow.entities.OwnerEntity;
import com.pctheone.money_flow.repositories.OwnerRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

public class AuthServiceTest {
    Logger log = LoggerFactory.getLogger(AuthServiceTest.class);

    OwnerRepository ownerRepository = Mockito.mock(OwnerRepository.class);
    @Test
    void encryptString(){
        AuthService service = new AuthService(new BCryptPasswordEncoder(), ownerRepository);
        String pass = "Senha-bem-safada-5";
        String hashPass = service.encodePassword(pass);
        log.info(hashPass);

        Assertions.assertNotNull(hashPass);
        Assertions.assertEquals(60, hashPass.length());
    }

    @Test
    void authenticateSuccessfully(){
        String hash = new BCryptPasswordEncoder().encode("coxinha123");

        OwnerEntity ownerEntity = new OwnerEntity(1, "G", "g@gmail.com", hash);
        Mockito.when(ownerRepository.findByEmail(ArgumentMatchers.any())).thenReturn(Optional.of(ownerEntity));

        AuthService service = new AuthService(new BCryptPasswordEncoder(), ownerRepository);
        Optional<Integer> result = service.authenticate("theone@gmail.com", "coxinha123");

        Assertions.assertEquals(Optional.of(1), result);

    }
}
