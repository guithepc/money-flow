package com.pctheone.money_flow.entities;

import com.pctheone.money_flow.enums.FrequencyEnum;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "recurring_payment")
public class RecurringPaymentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recurring_payment_id")
    private Integer recurringPaymentId;

    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    private OwnerEntity owner;

    @ManyToOne
    @JoinColumn(name = "account_id", nullable = false)
    private AccountEntity account;

    @ManyToOne
    @JoinColumn(name = "category_id", nullable = false)
    private CategoryEntity category;

    @Column(nullable = false)
    private String description;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "recurring_start_date", nullable = false)
    private LocalDate recurringStartDate;

    @Column(name = "recurring_end_date")
    private LocalDate recurringEndDate;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FrequencyEnum frequency;

}
