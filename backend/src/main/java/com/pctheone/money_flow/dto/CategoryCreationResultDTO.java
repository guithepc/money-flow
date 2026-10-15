package com.pctheone.money_flow.dto;

import com.pctheone.money_flow.entities.CategoryEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;

//---- Outcome of CategoryService.addCategory: the category with that name, and whether this call created it.
//---- When created is false the existing category is returned, possibly with a different type.
@Getter
@AllArgsConstructor
public class CategoryCreationResultDTO {
    private CategoryEntity category;
    private boolean created;
}
