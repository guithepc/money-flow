package com.pctheone.money_flow.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OwnerProfileDTO {
    private String name;
    private String email;
    private boolean telegramLinked;
    //---- False until the owner accepts the current terms version; the dashboard asks for it before anything else.
    private boolean termsAccepted;
    //---- True for owners in app.admin-owner-ids; the dashboard shows the Admin (volumetry) tab only to them.
    private boolean admin;
}
