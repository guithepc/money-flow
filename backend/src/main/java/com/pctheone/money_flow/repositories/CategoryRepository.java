package com.pctheone.money_flow.repositories;

import com.pctheone.money_flow.entities.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<CategoryEntity, Integer> {
    Optional<CategoryEntity> findByOwnerOwnerIdAndDescription(Integer ownerId, String description);

    List<CategoryEntity> findByOwnerOwnerIdOrderByCategoryIdAsc(Integer ownerId);

    boolean existsByCategoryIdAndOwnerOwnerId(Integer categoryId, Integer ownerId);

    Optional<CategoryEntity> findByCategoryIdAndOwnerOwnerId(Integer categoryId, Integer ownerId);

    //---- Idempotent: an existing (owner_id, description) is left untouched. Returns 1 when inserted, 0 otherwise.
    @Modifying
    @Query(value = "INSERT INTO category (owner_id, description, type) VALUES (:ownerId, :description, CAST(:type AS category_type)) " +
            "ON CONFLICT (owner_id, description) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("ownerId") Integer ownerId, @Param("description") String description, @Param("type") String type);
}
