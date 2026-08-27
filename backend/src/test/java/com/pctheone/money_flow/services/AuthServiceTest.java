package com.pctheone.money_flow.services;

import com.pctheone.money_flow.entities.OwnerEntity;
import com.pctheone.money_flow.repositories.OwnerRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import javax.crypto.SecretKey;
import java.util.Optional;

public class AuthServiceTest {
    Logger log = LoggerFactory.getLogger(AuthServiceTest.class);

    OwnerRepository ownerRepository = Mockito.mock(OwnerRepository.class);
    @Test
    void encryptString(){
        AuthService service = new AuthService(new BCryptPasswordEncoder(), ownerRepository);
        String pass = "coxinha123";
        String hashPass = service.encodePassword(pass);
        log.info(hashPass);

        Assertions.assertNotNull(hashPass);
        Assertions.assertEquals(60, hashPass.length());
    }

    @Test
    void authenticateSuccessfully(){
        String hash = new BCryptPasswordEncoder().encode("coxinha123");

        OwnerEntity ownerEntity = new OwnerEntity(1, "G", "g@gmail.com", null, hash);
        Mockito.when(ownerRepository.findByEmail(ArgumentMatchers.any())).thenReturn(Optional.of(ownerEntity));

        AuthService service = new AuthService(new BCryptPasswordEncoder(), ownerRepository);
        Optional<Integer> result = service.authenticate("theone@gmail.com", "coxinha123");

        Assertions.assertEquals(Optional.of(1), result);

    }

    @Test
    void authenticateWithWrongPassword(){
        String hash = new BCryptPasswordEncoder().encode("coxinha123");

        OwnerEntity ownerEntity = new OwnerEntity(1, "G", "g@gmail.com", null, hash);
        Mockito.when(ownerRepository.findByEmail(ArgumentMatchers.any())).thenReturn(Optional.of(ownerEntity));

        AuthService service = new AuthService(new BCryptPasswordEncoder(), ownerRepository);
        Optional<Integer> result = service.authenticate("theone@gmail.com", "otherpassword123");

        Assertions.assertEquals(Optional.empty(), result);

    }


    @Test
    void authenticateWithUnknownEmail(){
        Mockito.when(ownerRepository.findByEmail(ArgumentMatchers.any())).thenReturn(Optional.empty());

        AuthService service = new AuthService(new BCryptPasswordEncoder(), ownerRepository);
        Optional<Integer> result = service.authenticate("theone@gmail.com", "otherpassword123");

        Assertions.assertEquals(Optional.empty(), result);

    }

    @Test
    void decodeToken(){

        Integer ownerId = 1;
        AuthService service = new AuthService(new BCryptPasswordEncoder(), ownerRepository);
        service.jwtExpiration = 3600000L;
        service.jwtSecret = "tmpGFRC21ovxZhKhbd11YOC3Kw8YDyg5KcBb1dlMY0c=";

        String token = service.generateToken(ownerId);

        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(service.jwtSecret));

        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String subject = claims.getSubject();

        Assertions.assertEquals(ownerId.toString(), subject);


    }
}
