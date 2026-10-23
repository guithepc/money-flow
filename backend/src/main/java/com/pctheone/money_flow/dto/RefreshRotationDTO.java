package com.pctheone.money_flow.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

//---- Result of a successful refresh: whose session it is and the new raw refresh token for the cookie.
@Getter
@AllArgsConstructor
public class RefreshRotationDTO {
    private Integer ownerId;
    private String refreshToken;
}
