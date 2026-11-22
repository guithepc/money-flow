package com.pctheone.money_flow.entities;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import com.pctheone.money_flow.enums.OperationTypeEnum;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Entity
@Table(name = "transactions")
public class TransactionsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transactions_id")
    private Integer transactionsId;

    //---- 1, 2, 3... per owner, filled by a database trigger (V13). What the bot shows and accepts; the PK stays internal.
    @Generated(event = EventType.INSERT)
    @Column(name = "display_number", insertable = false, updatable = false)
    private Integer displayNumber;

    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    private OwnerEntity owner;

    @ManyToOne
    @JoinColumn(name = "account_id", nullable = false)
    private AccountEntity account;

    @Setter
    @ManyToOne
    @JoinColumn(name = "category_id", nullable = false)
    private CategoryEntity category;

    @Setter
    @Column(nullable = false)
    private String description;

    @ManyToOne
    @JoinColumn(name = "recurring_payment_id")
    private RecurringPaymentEntity recurringPayment;

    @Column(nullable = false)
    private LocalDate timestamp;

    @Setter
    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "operation")
    private OperationTypeEnum operationType;

}
