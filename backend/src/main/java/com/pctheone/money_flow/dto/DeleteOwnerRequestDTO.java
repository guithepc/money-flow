package com.pctheone.money_flow.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

//---- Body of DELETE /api/owner. The owner comes from the JWT, never from the request.
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class DeleteOwnerRequestDTO {
    private String password;
}
