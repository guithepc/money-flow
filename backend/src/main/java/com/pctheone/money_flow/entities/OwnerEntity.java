package com.pctheone.money_flow.entities;

import com.pctheone.money_flow.enums.OnboardingStepEnum;
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

    //---- "pt" / "en". Null for owners that never went through onboarding.
    @Column(length = 2)
    private String language;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "onboarding_step", nullable = false, columnDefinition = "onboarding_step")
    private OnboardingStepEnum onboardingStep = OnboardingStepEnum.LANGUAGE;

    //---- JSON of OnboardingAccountDraftDTO while an account is being created during onboarding.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "onboarding_draft", columnDefinition = "jsonb")
    private String onboardingDraft;
}
