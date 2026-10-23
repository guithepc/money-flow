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

//---- One refresh token of a browser session. Only the SHA-256 of the raw value is persisted.
@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "refresh_token")
public class RefreshTokenEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refresh_token_id")
    private Long refreshTokenId;

    @Column(name = "owner_id", nullable = false)
    private Integer ownerId;

    //---- Every token rotated from the same login shares the family; reuse revokes all of them.
    @Column(name = "family_id", nullable = false)
    @JdbcTypeCode(SqlTypes.UUID)
    private UUID familyId;

    @Column(name = "token_hash", columnDefinition = "CHAR(64)", nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    //---- Set when the token is rotated, logged out or its family is revoked.
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "user_agent")
    private String userAgent;
}
