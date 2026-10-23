package com.pctheone.money_flow.repositories;

import com.pctheone.money_flow.entities.RecurringPaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RecurringPaymentRepository extends JpaRepository<RecurringPaymentEntity, Integer> {

    List<RecurringPaymentEntity> findByOwnerOwnerIdOrderByRecurringPaymentIdAsc(Integer ownerId);

    //---- Account deletion: runs after the owner's transactions (they reference recurring_payment).
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM recurring_payment WHERE owner_id = :ownerId", nativeQuery = true)
    int deleteAllByOwnerId(@Param("ownerId") Integer ownerId);
}
