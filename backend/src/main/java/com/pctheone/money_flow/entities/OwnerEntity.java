package com.pctheone.money_flow.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "owner")
public class OwnerEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "owner_id")
    private Integer ownerId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column(name = "telegram_chat_id")
    private Long telegramChatId;

    @Column(name = "password_hash", columnDefinition = "CHAR(60)", nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String passwordHash;

    @Column(name = "invite_expiration")
    private LocalDateTime inviteExpireDate;

    @Column(name = "invite_uuid")
    @JdbcTypeCode(SqlTypes.UUID)
    private UUID inviteUuid;
}
