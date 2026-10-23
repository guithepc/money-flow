package com.pctheone.money_flow.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

//---- Wrong current password on a sensitive action of a logged-in owner (e.g. account deletion).
//---- 403, not 401: the session is valid, and the frontend logs out on any 401.
@ResponseStatus(HttpStatus.FORBIDDEN)
public class WrongPasswordException extends RuntimeException {
    public WrongPasswordException(String message) {
        super(message);
    }
}
