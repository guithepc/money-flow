package com.pctheone.money_flow.services.impl;

import com.pctheone.money_flow.dto.CategoryDTO;
import com.pctheone.money_flow.entities.CategoryEntity;
import com.pctheone.money_flow.repositories.CategoryRepository;
import com.pctheone.money_flow.services.CategoryService;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryServiceImpl implements CategoryService {

    private static Logger log = LoggerFactory.getLogger(CategoryServiceImpl.class);

    @Autowired
    CategoryRepository categoryRepository;

    @Override
    public List<CategoryDTO> listAllCategories() {

        List<CategoryEntity> categoryEntities = categoryRepository.findAll();

        return categoryEntities.stream().map(c -> new CategoryDTO(c.getDescription(), c.getCategoryId())).toList();
    }

    @Override
    @Transactional
    public String addCategory(String categoryDescription) {
        char firstChar = categoryDescription.charAt(0);
        String categoryDescriptionFormatted = String.valueOf(firstChar).toUpperCase() + categoryDescription.substring(1);
        CategoryEntity categoryEntity = categoryRepository.findByDescription(categoryDescriptionFormatted);
        if (categoryEntity != null){
            log.info("Category already exists.");
            return "Category already exists.";
        }
        categoryRepository.createCategory(categoryDescriptionFormatted);
        log.info("Category " + categoryDescriptionFormatted + " created!");
        return "Category " + categoryDescriptionFormatted + " created!";
    }


}
