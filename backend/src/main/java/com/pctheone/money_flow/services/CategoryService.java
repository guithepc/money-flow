package com.pctheone.money_flow.services;

import com.pctheone.money_flow.dto.CategoryCreationResultDTO;
import com.pctheone.money_flow.dto.CategoryDTO;
import com.pctheone.money_flow.entities.CategoryEntity;
import com.pctheone.money_flow.enums.CategoryTypeEnum;
import com.pctheone.money_flow.enums.LanguageEnum;

import java.util.List;
import java.util.Optional;

public interface CategoryService {
    List<CategoryDTO> listAllCategories(Integer ownerId);
    CategoryCreationResultDTO addCategory(String categoryDescription, CategoryTypeEnum type, Integer ownerId);
    Optional<CategoryEntity> findByDescription(String categoryDescription, Integer ownerId);
    void seedBaseCategories(Integer ownerId, LanguageEnum language);

    //---- Base categories only for an owner with none yet: a web sign-up seeded in Portuguese doesn't get the English
    //---- set on top when the bot onboarding later picks English. True when it seeded.
    boolean seedBaseCategoriesIfNone(Integer ownerId, LanguageEnum language);
}
