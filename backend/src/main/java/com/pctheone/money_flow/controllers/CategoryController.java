package com.pctheone.money_flow.controllers;

import com.pctheone.money_flow.dto.CategoryCreationResultDTO;
import com.pctheone.money_flow.dto.CategoryDTO;
import com.pctheone.money_flow.entities.CategoryEntity;
import com.pctheone.money_flow.enums.CategoryTypeEnum;
import com.pctheone.money_flow.services.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    static final int MAX_NAME_LENGTH = 50;

    public record CategoryCreateRequest(String description, CategoryTypeEnum type) {}

    @Autowired
    CategoryService categoryService;

    @GetMapping
    ResponseEntity<List<CategoryDTO>> categoryList(){
        return ResponseEntity.ok(categoryService.listAllCategories(ownerId()));
    }

    //---- The PWA's "create Saúde and register": 201 when created, 200 when it already exists with the same type,
    //---- 409 when the name belongs to a category of the other type.
    @PostMapping
    ResponseEntity<CategoryDTO> createCategory(@RequestBody CategoryCreateRequest request){
        String description = request == null || request.description() == null ? "" : request.description().trim();
        if (description.isEmpty() || description.length() > MAX_NAME_LENGTH || request.type() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Description (1-" + MAX_NAME_LENGTH + " characters) and type are required");

        CategoryCreationResultDTO result = categoryService.addCategory(description, request.type(), ownerId());
        CategoryEntity category = result.getCategory();
        if (category.getType() != request.type())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A category with this name already exists with the other type");
        return ResponseEntity.status(result.isCreated() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(new CategoryDTO(category.getDescription(), category.getCategoryId(), category.getType()));
    }

    private static Integer ownerId() {
        return (Integer) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
