package com.pctheone.money_flow.services;

import com.pctheone.money_flow.dto.RefreshRotationDTO;

import java.util.Optional;

//---- Long-lived browser sessions (#24). The raw token only ever lives in the HttpOnly cookie.
public interface RefreshTokenService {

    //---- Starts a new session (new family) and returns the raw refresh token.
    String issue(Integer ownerId, String userAgent);

    //---- Rotates a valid token. Empty when unknown, expired or revoked; reusing a rotated token revokes its family.
    Optional<RefreshRotationDTO> rotate(String rawToken, String userAgent);

    //---- Logout: revokes the session (family) the token belongs to. Unknown tokens are ignored.
    void revokeSession(String rawToken);

    //---- Ends every session of the owner (e.g. after a password reset). Returns how many tokens were revoked.
    int revokeAllSessions(Integer ownerId);
}
