package com.pctheone.money_flow.services;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class BCryptServiceTest {
    Logger log = LoggerFactory.getLogger(BCryptServiceTest.class);

    @Test
    void encryptString(){
        BCryptService service = new BCryptService(new BCryptPasswordEncoder());
        String pass = "Senha-bem-safada-5";
        String hashPass = service.encodePassword(pass);
        log.info(hashPass);

        Assertions.assertNotNull(hashPass);
        Assertions.assertEquals(60, hashPass.length());
    }
}
