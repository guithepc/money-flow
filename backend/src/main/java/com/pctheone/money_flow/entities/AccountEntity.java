package com.pctheone.money_flow.entities;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import com.pctheone.money_flow.enums.CurrencyEnum;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

@Entity
@Table(name = "account")
@Getter
@Setter
public class AccountEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    private Integer accountId;

    //---- 1, 2, 3... per owner, filled by a database trigger (V13). What the bot shows and accepts; the PK stays internal.
    @Generated(event = EventType.INSERT)
    @Column(name = "display_number", insertable = false, updatable = false)
    private Integer displayNumber;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "currency")
    private CurrencyEnum currency;

    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    private OwnerEntity owner;
}
