package com.pctheone.money_flow.services;

import com.pctheone.money_flow.entities.OwnerEntity;
import com.pctheone.money_flow.enums.OnboardingStepEnum;
import com.pctheone.money_flow.repositories.OwnerRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    @Value("${jwt.secret}")
    String jwtSecret;

    @Value("${jwt.expiration}")
    long jwtExpiration;

    PasswordEncoder passwordEncoder;

    OwnerRepository ownerRepository;

    //---- Same zone as the Clock bean (system default); replaced in tests.
    Clock clock = Clock.systemDefaultZone();

    public AuthService(PasswordEncoder passwordEncoder, OwnerRepository ownerRepository) {
        this.passwordEncoder = passwordEncoder;
        this.ownerRepository = ownerRepository;
    }

    public String encodePassword(String pass){
        return passwordEncoder.encode(pass);
    }

    public Optional<Integer> authenticate(String email, String pass){
        Optional<OwnerEntity> owner = ownerRepository.findByEmail(email);
        Optional<OwnerEntity> ownerValidated = owner.filter(ownerEntity -> passwordEncoder.matches(pass, ownerEntity.getPasswordHash()));
        return ownerValidated.map(OwnerEntity::getOwnerId);
    }

    public Optional<Integer> register(String name, String email, String pass){
        OwnerEntity owner = ownerRepository.save(new OwnerEntity(null, name, email, null, this.encodePassword(pass), null, null, null, OnboardingStepEnum.LANGUAGE, null, null));
        return Optional.of(owner.getOwnerId());
    }

    public String generateToken(Integer ownerId){

        byte[] byteKey = Decoders.BASE64.decode(jwtSecret);
        Date date = Date.from(clock.instant());
        String token = Jwts.builder()
                .subject(ownerId.toString())
                .issuedAt(date)
                .expiration(new Date(date.getTime() + jwtExpiration))
                .signWith(Keys.hmacShaKeyFor(byteKey))
                .compact();

        return token;

    }

    public Optional<Integer> validateToken(String token){
        try {
            SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(this.jwtSecret));
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Integer ownerId = Integer.valueOf(claims.getSubject());
            //---- PK lookup per request: a password reset ends every session issued before it.
            //---- Refresh tokens from #24 must also be checked/revoked once they exist.
            Optional<OwnerEntity> owner = ownerRepository.findById(ownerId);
            if (owner.isEmpty() || issuedBeforePasswordChange(claims.getIssuedAt(), owner.get().getPasswordChangedAt()))
                return Optional.empty();
            return Optional.of(ownerId);
        } catch (JwtException | IllegalArgumentException e){
            return Optional.empty();
        }
    }

    //---- iat has second precision and password_changed_at is truncated to seconds: a token issued in the same
    //---- second as the reset (the login right after it) is accepted.
    private boolean issuedBeforePasswordChange(Date issuedAt, LocalDateTime passwordChangedAt) {
        if (passwordChangedAt == null)
            return false;
        if (issuedAt == null)
            return true;
        long changedAtSeconds = passwordChangedAt.atZone(clock.getZone()).toEpochSecond();
        return issuedAt.toInstant().getEpochSecond() < changedAtSeconds;
    }
}
