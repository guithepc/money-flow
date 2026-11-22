package com.pctheone.money_flow.services;

import com.pctheone.money_flow.dto.AccountDTO;
import com.pctheone.money_flow.dto.CategoryDTO;
import com.pctheone.money_flow.dto.OpenAITransactionDTO;
import com.pctheone.money_flow.dto.TransactionRegistrationResultDTO;
import com.pctheone.money_flow.entities.CategoryEntity;
import com.pctheone.money_flow.enums.CategoryTypeEnum;
import com.pctheone.money_flow.enums.OperationTypeEnum;
import com.pctheone.money_flow.services.telegram.OpenAITextClient;
import com.pctheone.money_flow.utils.Json;
import com.pctheone.money_flow.utils.MoneyFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

//---- A sentence ("gastei 25 no supermercado") -> registered transaction, or why not. Shared by the Telegram bot
//---- and the PWA: each channel turns the outcome into its own reply.
@Service
public class TransactionCaptureService {

    private static final Logger log = LoggerFactory.getLogger(TransactionCaptureService.class);

    public sealed interface Outcome {}

    public record Registered(OperationTypeEnum operationType, TransactionRegistrationResultDTO result) implements Outcome {}

    public record NotUnderstood() implements Outcome {}

    //---- Nothing in the owner's list covers the item: offer to create it.
    public record CategoryNotFound(String suggestedName, CategoryTypeEnum type) implements Outcome {}

    //---- The parser picked an income category for an expense (or the reverse).
    public record CategoryTypeMismatch(String category, CategoryTypeEnum categoryType) implements Outcome {}

    public record NoAccount() implements Outcome {}

    @Value("classpath:prompts/voice-parser.txt")
    Resource voiceParserPrompt;

    @Autowired
    OpenAITextClient openAITextClient;

    @Autowired
    CategoryService categoryService;

    @Autowired
    AccountService accountService;

    @Autowired
    TransactionsService transactionsService;

    public Outcome capture(String sentence, Integer ownerId) throws IOException {
        String template = new String(voiceParserPrompt.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String prompt = String.format(template, listCategories(ownerId, CategoryTypeEnum.EXPENSE), listCategories(ownerId, CategoryTypeEnum.INCOME), listAccounts(ownerId));
        String response = openAITextClient.complete(prompt, sentence).orElse("{\"error\": \"NOT_UNDERSTOOD\"}");
        return register(response, ownerId);
    }

    //---- GPT returns the transaction (accountId null when unsaid), {"error": "CATEGORY_NOT_FOUND", ...} or {"error": "NOT_UNDERSTOOD"}.
    //---- A category outside the owner's list is offered for creation, whichever way GPT phrased it.
    public Outcome register(String response, Integer ownerId) {
        //---- Never log the response itself: it carries descriptions and amounts.
        log.info("GPT parser response received ({} chars) for owner {}.", response == null ? 0 : response.length(), ownerId);

        JsonNode node = Json.MAPPER.readTree(response);
        if (node.hasNonNull("error")) {
            String category = node.hasNonNull("category") ? node.get("category").asString().trim() : "";
            if ("CATEGORY_NOT_FOUND".equals(node.get("error").asString()) && !category.isEmpty())
                return new CategoryNotFound(capitalize(category), operationTypeOf(node));
            log.info("GPT could not parse the message.");
            return new NotUnderstood();
        }

        OpenAITransactionDTO transactionDTO = Json.MAPPER.treeToValue(node, OpenAITransactionDTO.class);
        log.info("Parsed transaction - operation: {}, accountId: {}, owner: {}",
                transactionDTO.getOperationType(), transactionDTO.getAccountId(), ownerId);

        OperationTypeEnum operationType = transactionDTO.getOperationType();
        if (operationType != OperationTypeEnum.EXPENSE && operationType != OperationTypeEnum.INCOME)
            return new NotUnderstood();
        CategoryTypeEnum expectedType = operationType == OperationTypeEnum.INCOME ? CategoryTypeEnum.INCOME : CategoryTypeEnum.EXPENSE;

        //---- Refuse instead of guessing: a missing, zero or "25.90" amount would register the wrong value.
        if (MoneyFormat.parseBrazilian(transactionDTO.getAmount()).filter(amount -> amount.signum() > 0).isEmpty())
            return new NotUnderstood();

        String categoryName = transactionDTO.getCategory() == null ? "" : transactionDTO.getCategory().trim();
        if (categoryName.isEmpty())
            return new NotUnderstood();
        Optional<CategoryEntity> category = categoryService.findByDescription(categoryName, ownerId);
        if (category.isEmpty())
            return new CategoryNotFound(capitalize(categoryName), expectedType);
        if (category.get().getType() != expectedType)
            return new CategoryTypeMismatch(category.get().getDescription(), category.get().getType());

        Optional<Integer> accountId = transactionDTO.getAccountId() != null
                ? Optional.of(transactionDTO.getAccountId())
                : accountService.defaultAccountId(ownerId);
        if (accountId.isEmpty())
            return new NoAccount();

        TransactionRegistrationResultDTO result = operationType == OperationTypeEnum.EXPENSE
                ? transactionsService.registerExpense(ownerId, category.get().getDescription(), accountId.get(), transactionDTO.getAmount(), transactionDTO.getDescription())
                : transactionsService.registerIncome(ownerId, category.get().getDescription(), accountId.get(), transactionDTO.getAmount(), transactionDTO.getDescription());
        log.info("Parsed {} registered successfully", operationType);
        return new Registered(operationType, result);
    }

    private static CategoryTypeEnum operationTypeOf(JsonNode node) {
        return node.hasNonNull("operationType") && "INCOME".equals(node.get("operationType").asString())
                ? CategoryTypeEnum.INCOME : CategoryTypeEnum.EXPENSE;
    }

    private static String capitalize(String name) {
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    //---- One list per type: a single tagged list let "Outras entradas" (income) catch expenses with no category.
    private String listCategories(Integer ownerId, CategoryTypeEnum type) {
        StringBuilder categoryListText = new StringBuilder();
        for (CategoryDTO categoryDTO : categoryService.listAllCategories(ownerId))
            if (categoryDTO.getType() == type)
                categoryListText.append("- ").append(categoryDTO.getCategoryDescription()).append("\n");
        return categoryListText.isEmpty() ? "(nenhuma)\n" : categoryListText.toString();
    }

    private String listAccounts(Integer ownerId) {
        StringBuilder accountListText = new StringBuilder();
        for (AccountDTO accountDTO : accountService.listAllAccounts(ownerId))
            accountListText.append("ID: ").append(accountDTO.getAccountId())
                    .append(" - ").append(accountDTO.getDescription())
                    .append(" - ").append(accountDTO.getAmount())
                    .append("\n");
        return accountListText.toString();
    }
}
