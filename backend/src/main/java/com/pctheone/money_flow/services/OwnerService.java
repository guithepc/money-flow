package com.pctheone.money_flow.services;

import com.pctheone.money_flow.entities.OwnerEntity;

import java.util.Optional;

public interface OwnerService {
    Optional<Integer> registerOwner(String name, String email, String pass);
}
