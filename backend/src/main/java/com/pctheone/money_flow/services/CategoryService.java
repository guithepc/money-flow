package com.pctheone.money_flow.services;

import com.pctheone.money_flow.dto.CategoryDTO;
import com.pctheone.money_flow.entities.CategoryEntity;
import com.pctheone.money_flow.enums.CategoryTypeEnum;

import java.util.List;
import java.util.Optional;

public interface CategoryService {
    List<CategoryDTO> listAllCategories(Integer ownerId);
    String addCategory(String categoryDescription, CategoryTypeEnum type, Integer ownerId);
    Optional<CategoryEntity> findByDescription(String categoryDescription, Integer ownerId);
}
