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

import com.pctheone.money_flow.utils.MutableClock;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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

        OwnerEntity ownerEntity = new OwnerEntity();
        ownerEntity.setOwnerId(1);
        ownerEntity.setName("G");
        ownerEntity.setEmail("g@gmail.com");
        ownerEntity.setPasswordHash(hash);
        Mockito.when(ownerRepository.findByEmail(ArgumentMatchers.any())).thenReturn(Optional.of(ownerEntity));

        AuthService service = new AuthService(new BCryptPasswordEncoder(), ownerRepository);
        Optional<Integer> result = service.authenticate("theone@gmail.com", "coxinha123");

        Assertions.assertEquals(Optional.of(1), result);

    }

    @Test
    void authenticateWithWrongPassword(){
        String hash = new BCryptPasswordEncoder().encode("coxinha123");

        OwnerEntity ownerEntity = new OwnerEntity();
        ownerEntity.setOwnerId(1);
        ownerEntity.setName("G");
        ownerEntity.setEmail("g@gmail.com");
        ownerEntity.setPasswordHash(hash);
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

    //---- Session revocation (#26): JWTs issued before password_changed_at are rejected.

    @Test
    void tokenIssuedBeforePasswordChangeIsRejectedAndAfterIsAccepted() {
        MutableClock clock = new MutableClock(Instant.now().truncatedTo(ChronoUnit.SECONDS).plusMillis(250));
        AuthService service = jwtService(clock);
        OwnerEntity owner = new OwnerEntity();
        owner.setOwnerId(1);
        Mockito.when(ownerRepository.findById(1)).thenReturn(Optional.of(owner));

        String before = service.generateToken(1);
        Assertions.assertEquals(Optional.of(1), service.validateToken(before));

        clock.advance(Duration.ofSeconds(1));
        owner.setPasswordChangedAt(LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS));
        clock.advance(Duration.ofMillis(500));
        String sameSecond = service.generateToken(1);
        clock.advance(Duration.ofSeconds(1));
        String after = service.generateToken(1);

        Assertions.assertEquals(Optional.empty(), service.validateToken(before));
        Assertions.assertEquals(Optional.of(1), service.validateToken(sameSecond));
        Assertions.assertEquals(Optional.of(1), service.validateToken(after));
    }

    @Test
    void tokenOfDeletedOwnerOrGarbageIsRejected() {
        AuthService service = jwtService(Clock.systemUTC());
        Mockito.when(ownerRepository.findById(ArgumentMatchers.any())).thenReturn(Optional.empty());

        Assertions.assertEquals(Optional.empty(), service.validateToken(service.generateToken(1)));
        Assertions.assertEquals(Optional.empty(), service.validateToken("not-a-jwt"));
        Assertions.assertEquals(Optional.empty(), service.validateToken(""));
    }

    private AuthService jwtService(Clock clock) {
        AuthService service = new AuthService(new BCryptPasswordEncoder(), ownerRepository);
        service.jwtExpiration = 3600000L;
        service.jwtSecret = "tmpGFRC21ovxZhKhbd11YOC3Kw8YDyg5KcBb1dlMY0c=";
        service.clock = clock;
        return service;
    }
}
