package com.pctheone.money_flow.services.impl;

import com.pctheone.money_flow.dto.CategoryCreationResultDTO;
import com.pctheone.money_flow.dto.CategoryDTO;
import com.pctheone.money_flow.entities.CategoryEntity;
import com.pctheone.money_flow.enums.CategoryTypeEnum;
import com.pctheone.money_flow.enums.LanguageEnum;
import com.pctheone.money_flow.repositories.CategoryRepository;
import com.pctheone.money_flow.repositories.OwnerRepository;
import com.pctheone.money_flow.services.CategoryService;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CategoryServiceImpl implements CategoryService {

    private static Logger log = LoggerFactory.getLogger(CategoryServiceImpl.class);

    private record BaseCategory(String pt, String en, CategoryTypeEnum type) {
    }

    //---- Created for every new user when they pick a language during onboarding.
    static final List<BaseCategory> BASE_CATEGORIES = List.of(
            new BaseCategory("Mercado", "Groceries", CategoryTypeEnum.EXPENSE),
            new BaseCategory("Restaurantes", "Eating out", CategoryTypeEnum.EXPENSE),
            new BaseCategory("Transporte", "Transport", CategoryTypeEnum.EXPENSE),
            new BaseCategory("Casa", "Home", CategoryTypeEnum.EXPENSE),
            new BaseCategory("Salário", "Salary", CategoryTypeEnum.INCOME),
            new BaseCategory("Renda extra", "Side income", CategoryTypeEnum.INCOME)
    );

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    OwnerRepository ownerRepository;

    @Override
    public List<CategoryDTO> listAllCategories(Integer ownerId) {

        List<CategoryEntity> categoryEntities = categoryRepository.findByOwnerOwnerIdOrderByCategoryIdAsc(ownerId);

        return categoryEntities.stream().map(c -> new CategoryDTO(c.getDescription(), c.getCategoryId(), c.getType())).toList();
    }

    @Override
    @Transactional
    //---- INSERT ... ON CONFLICT DO NOTHING: a double click or a concurrent request never fails nor duplicates,
    //---- and only the call that actually inserted gets created = true.
    public CategoryCreationResultDTO addCategory(String categoryDescription, CategoryTypeEnum type, Integer ownerId) {
        String categoryDescriptionFormatted = formatDescription(categoryDescription.trim());
        boolean created = categoryRepository.insertIfAbsent(ownerId, categoryDescriptionFormatted, type.name()) == 1;
        CategoryEntity category = categoryRepository.findByOwnerOwnerIdAndDescription(ownerId, categoryDescriptionFormatted)
                .orElseThrow(() -> new IllegalStateException("Category missing right after insert: " + categoryDescriptionFormatted));
        if (created)
            log.info("Category {} ({}) created for owner {}", categoryDescriptionFormatted, type, ownerId);
        else
            log.info("Category {} already exists for owner {} with type {}", categoryDescriptionFormatted, ownerId, category.getType());
        return new CategoryCreationResultDTO(category, created);
    }

    @Override
    public Optional<CategoryEntity> findByDescription(String categoryDescription, Integer ownerId) {
        return categoryRepository.findByOwnerOwnerIdAndDescription(ownerId, formatDescription(categoryDescription));
    }

    //---- Idempotent (ON CONFLICT DO NOTHING): running it twice, or with a category the user already has, never duplicates.
    @Override
    @Transactional
    public void seedBaseCategories(Integer ownerId, LanguageEnum language) {
        int inserted = 0;
        for (BaseCategory category : BASE_CATEGORIES) {
            String description = language == LanguageEnum.PT ? category.pt() : category.en();
            inserted += categoryRepository.insertIfAbsent(ownerId, description, category.type().name());
        }
        log.info("Seeded {} base categories ({}) for owner {}", inserted, language.code(), ownerId);
    }

    private String formatDescription(String categoryDescription) {
        char firstChar = categoryDescription.charAt(0);
        return String.valueOf(firstChar).toUpperCase() + categoryDescription.substring(1);
    }

}
