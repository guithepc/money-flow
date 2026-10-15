package com.pctheone.money_flow.services.impl;

import com.pctheone.money_flow.dto.CategoryDTO;
import com.pctheone.money_flow.entities.CategoryEntity;
import com.pctheone.money_flow.enums.CategoryTypeEnum;
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
    public String addCategory(String categoryDescription, CategoryTypeEnum type, Integer ownerId) {
        String categoryDescriptionFormatted = formatDescription(categoryDescription);
        if (categoryRepository.findByOwnerOwnerIdAndDescription(ownerId, categoryDescriptionFormatted).isPresent()){
            log.info("Category already exists.");
            return "Category already exists.";
        }
        CategoryEntity categoryEntity = new CategoryEntity();
        categoryEntity.setDescription(categoryDescriptionFormatted);
        categoryEntity.setType(type);
        categoryEntity.setOwner(ownerRepository.getReferenceById(ownerId));
        categoryRepository.save(categoryEntity);
        log.info("Category " + categoryDescriptionFormatted + " created!");
        return "Category " + categoryDescriptionFormatted + " created!";
    }

    @Override
    public Optional<CategoryEntity> findByDescription(String categoryDescription, Integer ownerId) {
        return categoryRepository.findByOwnerOwnerIdAndDescription(ownerId, formatDescription(categoryDescription));
    }

    private String formatDescription(String categoryDescription) {
        char firstChar = categoryDescription.charAt(0);
        return String.valueOf(firstChar).toUpperCase() + categoryDescription.substring(1);
    }

}
