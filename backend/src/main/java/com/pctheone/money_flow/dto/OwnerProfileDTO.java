package com.pctheone.money_flow.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OwnerProfileDTO {
    private String name;
    private String email;
    private boolean telegramLinked;
}
