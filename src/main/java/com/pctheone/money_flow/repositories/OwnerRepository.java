package com.pctheone.money_flow.repositories;

import com.pctheone.money_flow.entities.OwnerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OwnerRepository extends JpaRepository<OwnerEntity, Integer> {
}
