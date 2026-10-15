package com.pctheone.money_flow.dto;

import com.pctheone.money_flow.enums.CategoryTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class CategoryDTO {
    private String categoryDescription;
    private int categoryId;
    private CategoryTypeEnum type;
}
