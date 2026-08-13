package com.pctheone.money_flow.services;

import com.pctheone.money_flow.dto.CategoryDTO;
import org.springframework.stereotype.Service;

import java.util.List;

public interface CategoryService {
    List<CategoryDTO> listAllCategories();
    String addCategory(String categoryDescription);
}
