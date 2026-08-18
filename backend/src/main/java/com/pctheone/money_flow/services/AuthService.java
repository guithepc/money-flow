package com.pctheone.money_flow.services;

import com.pctheone.money_flow.entities.OwnerEntity;
import com.pctheone.money_flow.repositories.OwnerRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

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
}
