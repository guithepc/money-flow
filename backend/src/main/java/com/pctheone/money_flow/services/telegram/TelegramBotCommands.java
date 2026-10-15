package com.pctheone.money_flow.services.telegram;

import com.pctheone.money_flow.dto.TelegramBotCommandDTO;

import java.util.ArrayList;
import java.util.List;

//---- Single source for the command menu (setMyCommands) and the /ajuda | /help text.
public final class TelegramBotCommands {

    private TelegramBotCommands() {
    }

    //---- examplePt / exampleEn are null for commands without arguments.
    private record Entry(String command, String pt, String en, String examplePt, String exampleEn) {
    }

    private static final List<Entry> ENTRIES = List.of(
            new Entry("gasto", "Registrar um gasto", "Register an expense",
                    "/gasto mercado carne 25,90 1", "/gasto groceries meat 25,90 1"),
            new Entry("entrada", "Registrar uma entrada", "Register an income",
                    "/entrada salario outubro 5.000,00 1", "/entrada salary october 5.000,00 1"),
            new Entry("contas", "Listar suas contas com saldo e ID", "List your accounts with balance and ID",
                    null, null),
            new Entry("nova_conta", "Criar uma conta (moeda BRL, EUR ou USD)", "Create an account (currency BRL, EUR or USD)",
                    "/nova_conta nubank 1.000,00 BRL", "/nova_conta revolut 1.000,00 EUR"),
            new Entry("categorias", "Listar suas categorias", "List your categories",
                    null, null),
            new Entry("nova_categoria", "Criar uma categoria de gasto ou entrada", "Create an expense or income category",
                    "/nova_categoria academia gasto", "/nova_categoria gym expense"),
            new Entry("transacoes", "Transações dos últimos 7 dias", "Transactions from the last 7 days",
                    null, null),
            new Entry("deletar_transacao", "Apagar uma transação pelo ID", "Delete a transaction by ID",
                    "/deletar_transacao 42", "/deletar_transacao 42")
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

    //---- languageCode is the resolved language ("pt" / "en") or a Telegram code ("pt-br"). Anything not Portuguese gets English.
    public static String helpText(String languageCode) {
        boolean portuguese = languageCode != null && languageCode.toLowerCase().startsWith("pt");
        StringBuilder text = new StringBuilder(portuguese ? "🤖 Comandos disponíveis\n\n" : "🤖 Available commands\n\n");
        for (Entry entry : ENTRIES) {
            text.append('/').append(entry.command())
                    .append(" — ")
                    .append(portuguese ? entry.pt() : entry.en())
                    .append('\n');
            String example = portuguese ? entry.examplePt() : entry.exampleEn();
            if (example != null)
                text.append(portuguese ? "   Ex.: " : "   e.g. ").append(example).append('\n');
        }
        text.append(portuguese
                ? "/cancelar — Sair da configuração inicial\n/ajuda — Mostrar esta lista\n"
                : "/cancelar — Leave the initial setup\n/help — Show this list\n");
        text.append(portuguese
                ? "\n🎙️ Você também pode mandar um áudio: \"gastei 30 no mercado na Nubank\"."
                + "\n💡 Valores com vírgula nos centavos: 25,90 ou 1.250,90. O ID da conta aparece em /contas."
                : "\n🎙️ You can also send a voice message: \"spent 30 on groceries at Nubank\"."
                + "\n💡 Amounts use a comma for cents: 25,90 or 1.250,90. The account ID is shown in /contas.");
        return text.toString();
    }
}
