package com.pctheone.money_flow.repositories;

import com.pctheone.money_flow.entities.CategoryEntity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class TransactionRepositoryTest {

    @Autowired
    TransactionsRepository transactionsRepository;

    @Test
    public void testUnexistentCategory(){

        CategoryEntity categoryEntity = new CategoryEntity();
        categoryEntity.setCategoryId(9999);
        categoryEntity.setDescription("Random");
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 31);

        BigDecimal result = transactionsRepository.totalSpentByCategoryByTime(startDate, endDate, categoryEntity);

        Assertions.assertTrue(result.compareTo(BigDecimal.ZERO) == 0) ;
    }

    @Test
    public void testExistentCategory(){

        CategoryEntity categoryEntity = new CategoryEntity();
        categoryEntity.setCategoryId(4);
        categoryEntity.setDescription("Random");
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 31);

        BigDecimal result = transactionsRepository.totalSpentByCategoryByTime(startDate, endDate, categoryEntity);

        Assertions.assertTrue(result.compareTo(BigDecimal.ZERO) > 0) ;
    }
}