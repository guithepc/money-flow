package com.pctheone.money_flow.services;

import com.pctheone.money_flow.entities.OwnerEntity;
import com.pctheone.money_flow.repositories.OwnerRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Optional;

@Service
public class AuthService {

    @Value("${jwt.secret}")
    String jwtSecret;

    @Value("${jwt.expiration}")
    long jwtExpiration;

    PasswordEncoder passwordEncoder;

    OwnerRepository ownerRepository;

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
    public String generateToken(Integer ownerId){

        byte[] byteKey = Decoders.BASE64.decode(jwtSecret);
        Date date = new Date();
        String token = Jwts.builder()
                .subject(ownerId.toString())
                .issuedAt(date)
                .expiration(new Date(date.getTime() + jwtExpiration))
                .signWith(Keys.hmacShaKeyFor(byteKey))
                .compact();

        return token;

    }
}
