package com.pctheone.money_flow.entities;

import jakarta.persistence.*;

@Entity
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
}
