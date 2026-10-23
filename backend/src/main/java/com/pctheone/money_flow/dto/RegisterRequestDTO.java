package com.pctheone.money_flow.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RegisterRequestDTO {
    private String name;
    private String email;
    private String password;
    //---- Version of the terms the user agreed to on the sign-up form. Null when the box was not checked.
    private String acceptedTermsVersion;
}
