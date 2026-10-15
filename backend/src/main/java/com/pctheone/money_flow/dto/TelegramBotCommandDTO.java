package com.pctheone.money_flow.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TelegramBotCommandDTO {
    private String command;
    private String description;
}
