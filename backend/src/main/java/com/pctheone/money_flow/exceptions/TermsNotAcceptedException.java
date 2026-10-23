package com.pctheone.money_flow.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

//---- Sign-up or acceptance without the current terms version.
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class TermsNotAcceptedException extends RuntimeException {
    public TermsNotAcceptedException(String message) {
        super(message);
    }
}
