package com.pctheone.money_flow.exceptions;

//---- Invalid input from a Telegram user. The message is sent back to the user as-is.
public class TelegramUserInputException extends RuntimeException {
    public TelegramUserInputException(String message) {
        super(message);
    }
}
