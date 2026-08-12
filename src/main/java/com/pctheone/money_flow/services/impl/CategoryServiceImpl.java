package com.pctheone.money_flow.services.impl;

import com.pctheone.money_flow.dto.CategoryDTO;
import com.pctheone.money_flow.entities.CategoryEntity;
import com.pctheone.money_flow.repositories.CategoryRepository;
import com.pctheone.money_flow.services.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    CategoryRepository categoryRepository;

    @Override
    public List<CategoryDTO> listAllCategories() {

        List<CategoryEntity> categoryEntities = categoryRepository.findAll();

        return categoryEntities.stream().map(c -> new CategoryDTO(c.getDescription(), c.getCategoryId())).toList();
    }
}
