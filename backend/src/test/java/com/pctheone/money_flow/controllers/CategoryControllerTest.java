package com.pctheone.money_flow.controllers;

import com.pctheone.money_flow.dto.CategoryCreationResultDTO;
import com.pctheone.money_flow.entities.CategoryEntity;
import com.pctheone.money_flow.enums.CategoryTypeEnum;
import com.pctheone.money_flow.services.CategoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class CategoryControllerTest {

    private static final int OWNER_ID = 7;

    CategoryService categoryService;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        categoryService = Mockito.mock(CategoryService.class);
        CategoryController controller = new CategoryController();
        controller.categoryService = categoryService;
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(OWNER_ID, null, List.of()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void newCategoryIsCreated() throws Exception {
        Mockito.when(categoryService.addCategory("Saúde", CategoryTypeEnum.EXPENSE, OWNER_ID)).thenReturn(result(31, "Saúde", CategoryTypeEnum.EXPENSE, true));

        mockMvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON).content("{\"description\": \" Saúde \", \"type\": \"EXPENSE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryId").value(31))
                .andExpect(jsonPath("$.categoryDescription").value("Saúde"))
                .andExpect(jsonPath("$.type").value("EXPENSE"));
    }

    @Test
    void existingCategoryOfTheSameTypeIsReturned() throws Exception {
        Mockito.when(categoryService.addCategory("Saúde", CategoryTypeEnum.EXPENSE, OWNER_ID)).thenReturn(result(31, "Saúde", CategoryTypeEnum.EXPENSE, false));

        mockMvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON).content("{\"description\": \"Saúde\", \"type\": \"EXPENSE\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void nameTakenByTheOtherTypeIsAConflict() throws Exception {
        Mockito.when(categoryService.addCategory("Bônus", CategoryTypeEnum.EXPENSE, OWNER_ID)).thenReturn(result(9, "Bônus", CategoryTypeEnum.INCOME, false));

        mockMvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON).content("{\"description\": \"Bônus\", \"type\": \"EXPENSE\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void blankLongOrTypelessNamesAreRejected() throws Exception {
        for (String body : List.of("{\"description\": \" \", \"type\": \"EXPENSE\"}", "{\"description\": \"Saúde\"}",
                "{\"description\": \"" + "a".repeat(51) + "\", \"type\": \"EXPENSE\"}"))
            mockMvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());

        Mockito.verifyNoInteractions(categoryService);
    }

    private static CategoryCreationResultDTO result(int id, String description, CategoryTypeEnum type, boolean created) {
        CategoryEntity category = new CategoryEntity();
        category.setCategoryId(id);
        category.setDescription(description);
        category.setType(type);
        return new CategoryCreationResultDTO(category, created);
    }
}
