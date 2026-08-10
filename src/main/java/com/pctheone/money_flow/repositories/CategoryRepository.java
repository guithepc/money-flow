package com.pctheone.money_flow.repositories;

import com.pctheone.money_flow.entities.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<CategoryEntity, Integer> {
    CategoryEntity findByDescription(String categoryDescription);
}
