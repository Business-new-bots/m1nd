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
import com.example.m1nd.service.I18nService;

@Service
public class MainMenuService {

    private final AssistantPromptContextService assistantPromptContextService;
    private final I18nService i18nService;

    public MainMenuService(AssistantPromptContextService assistantPromptContextService, I18nService i18nService) {
        this.assistantPromptContextService = assistantPromptContextService;
        this.i18nService = i18nService;
    }

    public ReplyKeyboardMarkup createMainReplyKeyboard(String languageCode) {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);

        List<KeyboardRow> rows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton(i18nService.get(languageCode, "menu.reply.menu")));
        rows.add(row);

        keyboardMarkup.setKeyboard(rows);
        return keyboardMarkup;
    }

    public InlineKeyboardMarkup createMainMenuInlineKeyboard(String languageCode) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(button(i18nService.get(languageCode, "menu.main.business"), "main_business_ai_assistant"));

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(button(i18nService.get(languageCode, "menu.main.financial"), "main_financial_ai_assistant"));

        List<InlineKeyboardButton> row3 = new ArrayList<>();
        row3.add(button(i18nService.get(languageCode, "menu.main.thinking"), "main_thinking_ai_assistant"));

        List<InlineKeyboardButton> row4 = new ArrayList<>();
        row4.add(button(i18nService.get(languageCode, "menu.main.habits"), "main_habits_tracker"));

        keyboard.add(row1);
        keyboard.add(row2);
        keyboard.add(row3);
        keyboard.add(row4);

        markup.setKeyboard(keyboard);
        return markup;
    }

    public SendMessage buildStartMainMenuMessage(Long chatId, String languageCode) {
        SendMessage menuMessage = new SendMessage();
        menuMessage.setChatId(chatId.toString());
        menuMessage.setText(i18nService.get(languageCode, "menu.main.title"));
        menuMessage.setReplyMarkup(createMainReplyKeyboard(languageCode));
        return menuMessage;
    }

    public SendMessage buildMainMenuMessage(Long chatId, String languageCode) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(i18nService.get(languageCode, "menu.main.choose_assistant"));
        message.setReplyMarkup(createMainMenuInlineKeyboard(languageCode));
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
        String languageCode = callbackQuery.getFrom().getLanguageCode();

        if ("main_business_ai_assistant".equals(data)) {
            assistantPromptContextService.setAssistant(userId, "business");
            assistantPromptContextService.clearMode(userId);
            SendMessage message = simpleMessage(chatId, i18nService.get(languageCode, "menu.assistant.prompt", assistantTitle("business", languageCode)));
            message.setReplyMarkup(createCommunicationOptionsKeyboard("business", languageCode));
            return Mono.just(MainMenuResult.single(message, "✅"));
        }

        if ("main_financial_ai_assistant".equals(data)) {
            assistantPromptContextService.setAssistant(userId, "financial");
            assistantPromptContextService.clearMode(userId);
            SendMessage message = simpleMessage(chatId, i18nService.get(languageCode, "menu.assistant.prompt", assistantTitle("financial", languageCode)));
            message.setReplyMarkup(createCommunicationOptionsKeyboard("financial", languageCode));
            return Mono.just(MainMenuResult.single(message, "✅"));
        }

        if ("main_thinking_ai_assistant".equals(data)) {
            assistantPromptContextService.setAssistant(userId, "thinking");
            assistantPromptContextService.clearMode(userId);
            SendMessage message = simpleMessage(chatId, i18nService.get(languageCode, "menu.assistant.prompt", assistantTitle("thinking", languageCode)));
            message.setReplyMarkup(createCommunicationOptionsKeyboard("thinking", languageCode));
            return Mono.just(MainMenuResult.single(message, "✅"));
        }

        if ("main_menu_back".equals(data)) {
            assistantPromptContextService.clear(userId);
            return Mono.just(MainMenuResult.single(buildMainMenuMessage(chatId, languageCode), "✅"));
        }

        if (data.startsWith("assistant_choice:")) {
            String[] parts = data.split(":");
            if (parts.length == 3) {
                String assistant = assistantTitle(parts[1], languageCode);
                String modeCode = parts[2];
                String mode = modeTitle(modeCode, languageCode);
                // store original codes (not titles)
                assistantPromptContextService.setAssistant(userId, parts[1]);
                assistantPromptContextService.setMode(userId, modeCode);
                
                String text;
                if ("text".equals(modeCode)) {
                    text = i18nService.get(languageCode, "menu.mode.text.selected", assistant, mode);
                } else if ("question".equals(modeCode)) {
                    text = i18nService.get(languageCode, "menu.mode.question.selected", assistant);
                } else if ("meeting".equals(modeCode)) {
                    text = i18nService.get(languageCode, "menu.mode.meeting.selected", assistant);
                } else {
                    text = i18nService.get(languageCode, "menu.mode.default.selected", assistant, mode);
                }
                return Mono.just(MainMenuResult.single(
                    simpleMessage(chatId, text),
                    "✅"
                ));
            }
        }

        return Mono.just(MainMenuResult.single(
            simpleMessage(chatId, i18nService.get(languageCode, "common.in_development")),
            i18nService.get(languageCode, "common.in_development")
        ));
    }

    private InlineKeyboardMarkup createCommunicationOptionsKeyboard(String assistantCode, String languageCode) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        String textAssistantButton = switch (assistantCode) {
            case "business" -> i18nService.get(languageCode, "menu.option.text.business");
            case "financial" -> i18nService.get(languageCode, "menu.option.text.financial");
            case "thinking" -> i18nService.get(languageCode, "menu.option.text.thinking");
            default -> i18nService.get(languageCode, "menu.option.text.default");
        };

        keyboard.add(List.of(button(textAssistantButton, "assistant_choice:" + assistantCode + ":text")));
        keyboard.add(List.of(button(i18nService.get(languageCode, "menu.option.question"), "assistant_choice:" + assistantCode + ":question")));
        keyboard.add(List.of(button(i18nService.get(languageCode, "menu.option.meeting"), "assistant_choice:" + assistantCode + ":meeting")));
        keyboard.add(List.of(button(i18nService.get(languageCode, "menu.option.back"), "main_menu_back")));

        markup.setKeyboard(keyboard);
        return markup;
    }

    private String assistantTitle(String code, String languageCode) {
        return switch (code) {
            case "business" -> i18nService.get(languageCode, "menu.main.business");
            case "financial" -> i18nService.get(languageCode, "menu.main.financial");
            case "thinking" -> i18nService.get(languageCode, "menu.main.thinking");
            default -> i18nService.get(languageCode, "menu.assistant.default");
        };
    }

    private String modeTitle(String code, String languageCode) {
        return switch (code) {
            case "text" -> i18nService.get(languageCode, "menu.mode.text");
            case "question" -> i18nService.get(languageCode, "menu.mode.question");
            case "meeting" -> i18nService.get(languageCode, "menu.mode.meeting");
            default -> i18nService.get(languageCode, "menu.mode.unknown");
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
