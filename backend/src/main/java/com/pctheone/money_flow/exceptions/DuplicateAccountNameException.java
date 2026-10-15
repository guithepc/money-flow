package com.pctheone.money_flow.exceptions;

//---- The owner already has an account with this name (case-insensitive, unique index on account).
public class DuplicateAccountNameException extends RuntimeException {

    private final String name;

    public DuplicateAccountNameException(String name) {
        super("Account name already exists: " + name);
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
