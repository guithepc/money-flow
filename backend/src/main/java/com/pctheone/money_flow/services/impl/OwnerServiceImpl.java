package com.pctheone.money_flow.services.impl;

import com.pctheone.money_flow.entities.OwnerEntity;
import com.pctheone.money_flow.exceptions.InvalidCredentialsException;
import com.pctheone.money_flow.repositories.OwnerRepository;
import com.pctheone.money_flow.services.AuthService;
import com.pctheone.money_flow.services.OwnerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class OwnerServiceImpl implements OwnerService {

    @Autowired
    OwnerRepository ownerRepository;

    @Autowired
    AuthService authService;

    @Override
    public Optional<Integer> registerOwner(String name, String email, String pass) {
        Optional<OwnerEntity> owner = ownerRepository.findByEmail(email);
        if (owner.isPresent()){
            throw new InvalidCredentialsException("Email already registered");
        }

        return authService.register(name, email, pass);
    }
}
