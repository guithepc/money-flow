package com.pctheone.money_flow.services.telegram;

import com.pctheone.money_flow.dto.TelegramBotCommandDTO;

import java.util.ArrayList;
import java.util.List;

//---- Single source for the command menu (setMyCommands) and the /ajuda | /help text.
public final class TelegramBotCommands {

    private TelegramBotCommands() {
    }

    private record Entry(String command, String usage, String pt, String en) {
    }

    private static final List<Entry> ENTRIES = List.of(
            new Entry("gasto", "/gasto <categoria> <descrição> <valor> <id_conta>",
                    "Registrar um gasto", "Register an expense"),
            new Entry("entrada", "/entrada <categoria> <descrição> <valor> <id_conta>",
                    "Registrar uma entrada", "Register an income"),
            new Entry("contas", "/contas",
                    "Listar suas contas", "List your accounts"),
            new Entry("nova_conta", "/nova_conta <nome> <saldo> [BRL|EUR|USD]",
                    "Criar uma conta", "Create an account"),
            new Entry("categorias", "/categorias",
                    "Listar suas categorias", "List your categories"),
            new Entry("nova_categoria", "/nova_categoria <nome> [gasto|entrada]",
                    "Criar uma categoria", "Create a category"),
            new Entry("transacoes", "/transacoes",
                    "Transações dos últimos 7 dias", "Transactions from the last 7 days"),
            new Entry("deletar_transacao", "/deletar_transacao <id>",
                    "Apagar uma transação", "Delete a transaction")
    );

    public static List<TelegramBotCommandDTO> portuguese() {
        List<TelegramBotCommandDTO> commands = new ArrayList<>(ENTRIES.stream()
                .map(entry -> new TelegramBotCommandDTO(entry.command(), entry.pt()))
                .toList());
        commands.add(new TelegramBotCommandDTO("ajuda", "Mostrar os comandos disponíveis"));
        return commands;
    }

    public static List<TelegramBotCommandDTO> english() {
        List<TelegramBotCommandDTO> commands = new ArrayList<>(ENTRIES.stream()
                .map(entry -> new TelegramBotCommandDTO(entry.command(), entry.en()))
                .toList());
        commands.add(new TelegramBotCommandDTO("help", "Show available commands"));
        return commands;
    }

    //---- languageCode comes from the Telegram user (e.g. "pt-br", "en"). Anything not Portuguese gets English.
    public static String helpText(String languageCode) {
        boolean portuguese = languageCode != null && languageCode.toLowerCase().startsWith("pt");
        StringBuilder text = new StringBuilder(portuguese ? "🤖 Comandos disponíveis:\n\n" : "🤖 Available commands:\n\n");
        for (Entry entry : ENTRIES) {
            text.append(entry.usage())
                    .append("\n   ")
                    .append(portuguese ? entry.pt() : entry.en())
                    .append("\n");
        }
        text.append(portuguese
                ? "\n🎙️ Você também pode mandar um áudio descrevendo o gasto ou a entrada."
                : "\n🎙️ You can also send a voice message describing the expense or income.");
        return text.toString();
    }
}
