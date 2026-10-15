package com.pctheone.money_flow.exceptions;

//---- Invalid input from a Telegram user. The message key (messages/bot_*.properties) is replied in the user's language.
public class TelegramUserInputException extends RuntimeException {

    private final String messageKey;
    private final Object[] args;

    public TelegramUserInputException(String messageKey, Object... args) {
        super(messageKey);
        this.messageKey = messageKey;
        this.args = args;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public Object[] getArgs() {
        return args;
    }
}
