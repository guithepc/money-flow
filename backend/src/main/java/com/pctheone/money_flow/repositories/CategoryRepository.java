package com.pctheone.money_flow.repositories;

import com.pctheone.money_flow.entities.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<CategoryEntity, Integer> {
    Optional<CategoryEntity> findByOwnerOwnerIdAndDescription(Integer ownerId, String description);

    List<CategoryEntity> findByOwnerOwnerIdOrderByCategoryIdAsc(Integer ownerId);

    boolean existsByCategoryIdAndOwnerOwnerId(Integer categoryId, Integer ownerId);
}
