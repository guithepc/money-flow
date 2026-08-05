package com.pctheone.money_flow.repositories;

import com.pctheone.money_flow.entities.RecurringPaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecurringPaymentRepository extends JpaRepository<RecurringPaymentEntity, Integer> {
}
