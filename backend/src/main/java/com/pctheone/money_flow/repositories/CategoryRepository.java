package com.pctheone.money_flow.repositories;

import com.pctheone.money_flow.entities.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<CategoryEntity, Integer> {
    CategoryEntity findByDescription(String categoryDescription);

    @Modifying
    @Query("INSERT INTO CategoryEntity (description) values (:description)")
    void createCategory(@Param("description") String description);
}
