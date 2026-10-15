package com.pctheone.money_flow.controllers;

import com.pctheone.money_flow.dto.CategoryDTO;
import com.pctheone.money_flow.services.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    @Autowired
    CategoryService categoryService;

    @GetMapping
    ResponseEntity<List<CategoryDTO>> categoryList(){
        Integer ownerId = (Integer) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(categoryService.listAllCategories(ownerId));
    }
}
