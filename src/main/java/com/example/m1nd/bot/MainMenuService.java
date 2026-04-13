package com.example.m1nd.bot;

import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

import com.example.m1nd.service.AssistantPromptContextService;

@Service
public class MainMenuService {

    private final AssistantPromptContextService assistantPromptContextService;

    public MainMenuService(AssistantPromptContextService assistantPromptContextService) {
        this.assistantPromptContextService = assistantPromptContextService;
    }

    public ReplyKeyboardMarkup createMainReplyKeyboard() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);

        List<KeyboardRow> rows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton("📋 Меню"));
        rows.add(row);

        keyboardMarkup.setKeyboard(rows);
        return keyboardMarkup;
    }

    public InlineKeyboardMarkup createMainMenuInlineKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(button("Получить информацию от бизнес-ассистента (финансового и мыслителя ассистента)", "main_business_ai_assistant"));

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(button("Получить информацию от бизнес-ассистента (финансового и мыслителя ассистента)", "main_financial_ai_assistant"));

        List<InlineKeyboardButton> row3 = new ArrayList<>();
        row3.add(button("ИИ ассистент по мышлению", "main_thinking_ai_assistant"));

        keyboard.add(row1);
        keyboard.add(row2);
        keyboard.add(row3);

        markup.setKeyboard(keyboard);
        return markup;
    }

    public SendMessage buildStartMainMenuMessage(Long chatId) {
        SendMessage menuMessage = new SendMessage();
        menuMessage.setChatId(chatId.toString());
        menuMessage.setText("Главное меню");
        menuMessage.setReplyMarkup(createMainReplyKeyboard());
        return menuMessage;
    }

    public SendMessage buildMainMenuMessage(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Выберите ассистента:");
        message.setReplyMarkup(createMainMenuInlineKeyboard());
        return message;
    }

    public boolean canHandleCallback(String data) {
        return data != null && (
            "main_business_ai_assistant".equals(data)
            || "main_financial_ai_assistant".equals(data)
            || "main_thinking_ai_assistant".equals(data)
            || "main_menu_back".equals(data)
            || data.startsWith("assistant_choice:")
        );
    }

    public Mono<MainMenuResult> handleCallback(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        Long userId = callbackQuery.getFrom().getId();
        Long chatId = callbackQuery.getMessage().getChatId();

        if ("main_business_ai_assistant".equals(data)) {
            assistantPromptContextService.setAssistant(userId, "business");
            assistantPromptContextService.clearMode(userId);
            SendMessage message = simpleMessage(chatId, "Получить информацию от бизнес-ассистента (финансового и мыслителя ассистента)\n\nВыбрать вариант общения:");
            message.setReplyMarkup(createCommunicationOptionsKeyboard("business"));
            return Mono.just(MainMenuResult.single(message, "✅"));
        }

        if ("main_financial_ai_assistant".equals(data)) {
            assistantPromptContextService.setAssistant(userId, "financial");
            assistantPromptContextService.clearMode(userId);
            SendMessage message = simpleMessage(chatId, "Получить информацию от бизнес-ассистента (финансового и мыслителя ассистента)\n\nВыбрать вариант общения:");
            message.setReplyMarkup(createCommunicationOptionsKeyboard("financial"));
            return Mono.just(MainMenuResult.single(message, "✅"));
        }

        if ("main_thinking_ai_assistant".equals(data)) {
            assistantPromptContextService.setAssistant(userId, "thinking");
            assistantPromptContextService.clearMode(userId);
            SendMessage message = simpleMessage(chatId, "Получить информацию от бизнес-ассистента (финансового и мыслителя ассистента)\n\nВыбрать вариант общения:");
            message.setReplyMarkup(createCommunicationOptionsKeyboard("thinking"));
            return Mono.just(MainMenuResult.single(message, "✅"));
        }

        if ("main_menu_back".equals(data)) {
            assistantPromptContextService.clear(userId);
            return Mono.just(MainMenuResult.single(buildMainMenuMessage(chatId), "✅"));
        }

        if (data.startsWith("assistant_choice:")) {
            String[] parts = data.split(":");
            if (parts.length == 3) {
                String assistant = assistantTitle(parts[1]);
                String modeCode = parts[2];
                String mode = modeTitle(modeCode);
                // store original codes (not titles)
                assistantPromptContextService.setAssistant(userId, parts[1]);
                assistantPromptContextService.setMode(userId, modeCode);
                
                String text;
                if ("text".equals(modeCode)) {
                    text = assistant + "\n\nВы выбрали: " + mode + "\n\nОтправьте Ваш запрос:";
                } else if ("question".equals(modeCode)) {
                    text = assistant + "\n\nПолучить рекомендацию от основателя.";
                } else if ("meeting".equals(modeCode)) {
                    text = assistant + "\n\nОнлайн-встреча с основателем.";
                } else {
                    text = assistant + "\n\nВы выбрали: " + mode + "\n\nТеперь задайте ваш вопрос.";
                }
                return Mono.just(MainMenuResult.single(
                    simpleMessage(chatId, text),
                    "✅"
                ));
            }
        }

        return Mono.just(MainMenuResult.single(
            simpleMessage(chatId, "Этот раздел пока в разработке."),
            "Этот раздел пока в разработке."
        ));
    }

    private InlineKeyboardMarkup createCommunicationOptionsKeyboard(String assistantCode) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        keyboard.add(List.of(button("1️⃣ Текстовый помощник", "assistant_choice:" + assistantCode + ":text")));
        keyboard.add(List.of(button("2️⃣ Получить рекомендацию от основателя", "assistant_choice:" + assistantCode + ":question")));
        keyboard.add(List.of(button("3️⃣ Онлайн-встреча с основателем", "assistant_choice:" + assistantCode + ":meeting")));
        keyboard.add(List.of(button("◀️ Назад", "main_menu_back")));

        markup.setKeyboard(keyboard);
        return markup;
    }

    private String assistantTitle(String code) {
        return switch (code) {
            case "business" -> "Получить информацию от бизнес-ассистента (финансового и мыслителя ассистента)";
            case "financial" -> "Получить информацию от бизнес-ассистента (финансового и мыслителя ассистента)";
            case "thinking" -> "Получить информацию от бизнес-ассистента (финансового и мыслителя ассистента)";
            default -> "ИИ-ассистент";
        };
    }

    private String modeTitle(String code) {
        return switch (code) {
            case "text" -> "Текстовый помощник";
            case "question" -> "Получить рекомендацию от основателя";
            case "meeting" -> "Онлайн-встреча с основателем";
            default -> "Неизвестный вариант";
        };
    }

    public GameAnswerResult handleGameAnswer(Long chatId, Long userId, String messageText) {
        return GameAnswerResult.notHandled();
    }

    public PuzzleAnswerResult handlePuzzleAnswer(Long chatId, Long userId, String messageText) {
        return PuzzleAnswerResult.notHandled();
    }

    private SendMessage simpleMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        return message;
    }

    private InlineKeyboardButton button(String text, String data) {
        InlineKeyboardButton b = new InlineKeyboardButton();
        b.setText(text);
        b.setCallbackData(data);
        return b;
    }

    public static class GameAnswerResult {
        private final boolean handled;
        private final List<SendMessage> messages;

        private GameAnswerResult(boolean handled, List<SendMessage> messages) {
            this.handled = handled;
            this.messages = messages;
        }

        public static GameAnswerResult handled(List<SendMessage> messages) {
            return new GameAnswerResult(true, messages);
        }

        public static GameAnswerResult notHandled() {
            return new GameAnswerResult(false, List.of());
        }

        public boolean isHandled() {
            return handled;
        }

        public List<SendMessage> getMessages() {
            return messages;
        }
    }

    public static class PuzzleAnswerResult {
        private final boolean handled;
        private final List<SendMessage> messages;

        private PuzzleAnswerResult(boolean handled, List<SendMessage> messages) {
            this.handled = handled;
            this.messages = messages;
        }

        public static PuzzleAnswerResult handled(List<SendMessage> messages) {
            return new PuzzleAnswerResult(true, messages);
        }

        public static PuzzleAnswerResult notHandled() {
            return new PuzzleAnswerResult(false, List.of());
        }

        public boolean isHandled() {
            return handled;
        }

        public List<SendMessage> getMessages() {
            return messages;
        }
    }

    public static class MainMenuResult {
        private final List<SendMessage> messages;
        private final String callbackAnswer;

        public MainMenuResult(List<SendMessage> messages, String callbackAnswer) {
            this.messages = messages;
            this.callbackAnswer = callbackAnswer;
        }

        public static MainMenuResult single(SendMessage message, String callbackAnswer) {
            List<SendMessage> list = new ArrayList<>();
            list.add(message);
            return new MainMenuResult(list, callbackAnswer);
        }

        public List<SendMessage> getMessages() {
            return messages;
        }

        public String getCallbackAnswer() {
            return callbackAnswer;
        }
    }
}
