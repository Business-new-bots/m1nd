package com.example.m1nd.bot;

import com.example.m1nd.model.FactTopic;
import com.example.m1nd.service.FactTopicService;
import com.example.m1nd.service.LLMService;
import com.example.m1nd.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MainMenuService {

    private static final Logger log = LoggerFactory.getLogger(MainMenuService.class);

    private final LLMService llmService;
    private final FactTopicService factTopicService;
    private final TaskService taskService;

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

        // 🧠 Факты | 🎯 Задания дня | 🧩 Загадки и тесты
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(button("🧠 Факты", "main_facts"));
        row1.add(button("🎯 Задания дня", "main_daily_tasks"));
        row1.add(button("🧩 Загадки и тесты", "main_puzzles_tests"));

        // 💡 Идеи и инсайты | 🚀 Мотивация
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(button("💡 Идеи и инсайты", "main_ideas_insights"));
        row2.add(button("🚀 Мотивация", "main_motivation"));

        // 🎮 Игры | 📊 Мой прогресс
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        row3.add(button("🎮 Игры", "main_games"));
        row3.add(button("📊 Мой прогресс", "main_progress"));

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
        message.setText("Выбери раздел:");
        message.setReplyMarkup(createMainMenuInlineKeyboard());
        return message;
    }

    public boolean canHandleCallback(String data) {
        return data != null && (data.startsWith("main_") || data.startsWith("facts_") || data.startsWith("fact_") || data.startsWith("task_"));
    }

    public Mono<MainMenuResult> handleCallback(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Long userId = callbackQuery.getFrom().getId();

        // Главное меню "Факты" — показываем выбор тем
        if ("main_facts".equals(data)) {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("🧠 Факты\n\nВыбери тему, про которую хочешь узнать факт:");
            message.setReplyMarkup(createFactsTopicsKeyboard());
            return Mono.just(MainMenuResult.single(message, "✅"));
        }

        // Возврат в главное меню из заданий
        if ("main_menu_back".equals(data)) {
            SendMessage message = buildMainMenuMessage(chatId);
            return Mono.just(MainMenuResult.single(message, "✅"));
        }

        // Задания дня
        if ("main_daily_tasks".equals(data) || "task_next".equals(data)) {
            return buildTaskMessage(chatId, userId)
                .map(msg -> MainMenuResult.single(msg, "✅"))
                .onErrorResume(error -> {
                    log.error("Ошибка при получении задания", error);
                    SendMessage errorMessage = new SendMessage();
                    errorMessage.setChatId(chatId.toString());
                    errorMessage.setText("Не удалось получить задание. Попробуйте ещё раз позже.");
                    return Mono.just(MainMenuResult.single(errorMessage, "❌ Ошибка"));
                });
        }

        // Остальные пункты главного меню пока заглушки
        if (data.startsWith("main_")) {
            String responseText;
            switch (data) {
                case "main_daily_tasks":
                    responseText = "🎯 Задания дня\n\nЗдесь будут короткие задания на день. Раздел скоро заработает.";
                    break;
                case "main_puzzles_tests":
                    responseText = "🧩 Загадки и тесты\n\nЗдесь будут упражнения для ума. Раздел скоро заработает.";
                    break;
                case "main_ideas_insights":
                    responseText = "💡 Идеи и инсайты\n\nЗдесь будут идеи для роста и инсайты. Раздел скоро заработает.";
                    break;
                case "main_motivation":
                    responseText = "🚀 Мотивация\n\nЗдесь будет мотивационный контент. Раздел скоро заработает.";
                    break;
                case "main_games":
                    responseText = "🎮 Игры\n\nЗдесь будут игровые механики и форматы. Раздел скоро заработает.";
                    break;
                case "main_progress":
                    responseText = "📊 Мой прогресс\n\nЗдесь появится личный прогресс и статистика. Раздел скоро заработает.";
                    break;
                default:
                    responseText = "Этот раздел пока в разработке.";
            }

            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(responseText);
            return Mono.just(MainMenuResult.single(message, "✅"));
        }

        // Обработка выбора темы фактов
        if (data.startsWith("facts_")) {
            String category = data.substring("facts_".length());
            return buildFactMessage(chatId, userId, category)
                .map(msg -> MainMenuResult.single(msg, "✅"))
                .onErrorResume(error -> {
                    log.error("Ошибка при запросе факта у LLM", error);
                    SendMessage errorMessage = new SendMessage();
                    errorMessage.setChatId(chatId.toString());
                    errorMessage.setText("Не удалось получить факт. Попробуйте ещё раз позже.");
                    return Mono.just(MainMenuResult.single(errorMessage, "❌ Ошибка"));
                });
        }

        // Следующий факт по той же теме
        if (data.startsWith("fact_next:")) {
            String category = data.substring("fact_next:".length());
            return buildFactMessage(chatId, userId, category)
                .map(msg -> MainMenuResult.single(msg, "✅"))
                .onErrorResume(error -> {
                    log.error("Ошибка при запросе следующего факта у LLM", error);
                    SendMessage errorMessage = new SendMessage();
                    errorMessage.setChatId(chatId.toString());
                    errorMessage.setText("Не удалось получить факт. Попробуйте ещё раз позже.");
                    return Mono.just(MainMenuResult.single(errorMessage, "❌ Ошибка"));
                });
        }

        // Возврат в меню фактов
        if ("fact_menu".equals(data)) {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("Выбери тему факта:");
            message.setReplyMarkup(createFactsTopicsKeyboard());
            return Mono.just(MainMenuResult.single(message, "✅"));
        }

        // Если что-то не распознали
        SendMessage unknown = new SendMessage();
        unknown.setChatId(chatId.toString());
        unknown.setText("Этот раздел пока в разработке.");
        return Mono.just(MainMenuResult.single(unknown, "Этот раздел пока в разработке."));
    }

    private InlineKeyboardMarkup createFactsTopicsKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        List<FactTopic> topics = factTopicService.findAll();
        List<InlineKeyboardButton> currentRow = new ArrayList<>();

        for (FactTopic topic : topics) {
            String title = topic.getTitle();
            String code = topic.getCode();
            currentRow.add(button(title, "facts_" + code));

            // Раскладываем по 2 кнопки в ряд, как раньше
            if (currentRow.size() == 2) {
                keyboard.add(currentRow);
                currentRow = new ArrayList<>();
            }
        }

        if (!currentRow.isEmpty()) {
            keyboard.add(currentRow);
        }

        markup.setKeyboard(keyboard);
        return markup;
    }

    private Mono<SendMessage> buildFactMessage(Long chatId, Long userId, String category) {
        // category соответствует code в таблице fact_topics
        Optional<FactTopic> topicOpt = factTopicService.findByCode(category);

        String prompt;
        String nextCategory;

        if (topicOpt.isPresent()) {
            FactTopic topic = topicOpt.get();
            prompt = topic.getPrompt();
            nextCategory = topic.getCode();
        } else {
            log.warn("Не найдена тема фактов с кодом {}, используем дефолтный промпт", category);
            prompt = "Дай один интересный и достоверный факт. Кратко: 1–3 предложения.";
            nextCategory = category != null ? category : "default";
        }

        return llmService.getAnswer(prompt, userId)
            .map(answer -> {
                SendMessage factMessage = new SendMessage();
                factMessage.setChatId(chatId.toString());
                factMessage.setText(answer);

                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

                List<InlineKeyboardButton> row1 = new ArrayList<>();
                InlineKeyboardButton nextButton = new InlineKeyboardButton();
                nextButton.setText("➡️ Следующий факт");
                nextButton.setCallbackData("fact_next:" + nextCategory);
                row1.add(nextButton);

                InlineKeyboardButton menuButton = new InlineKeyboardButton();
                menuButton.setText("📋 Случайный факт по теме");
                menuButton.setCallbackData("fact_menu");
                row1.add(menuButton);

                keyboard.add(row1);
                markup.setKeyboard(keyboard);
                factMessage.setReplyMarkup(markup);

                return factMessage;
            });
    }

    private Mono<SendMessage> buildTaskMessage(Long chatId, Long userId) {
        return Mono.fromSupplier(() -> taskService.findRandom())
            .map(opt -> {
                SendMessage msg = new SendMessage();
                msg.setChatId(chatId.toString());

                if (opt.isEmpty()) {
                    msg.setText("Пока заданий нет. Администраторы могут добавить новые задания в редакторе меню.");
                    return msg;
                }

                String text = opt.get().getText();
                msg.setText("🎯 Задание дня:\n\n" + text);

                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

                List<InlineKeyboardButton> row1 = new ArrayList<>();
                InlineKeyboardButton nextButton = new InlineKeyboardButton();
                nextButton.setText("➡️ Следующее задание");
                nextButton.setCallbackData("task_next");
                row1.add(nextButton);

                InlineKeyboardButton menuButton = new InlineKeyboardButton();
                menuButton.setText("📋 Главное меню");
                menuButton.setCallbackData("main_menu_back");
                row1.add(menuButton);

                keyboard.add(row1);
                markup.setKeyboard(keyboard);
                msg.setReplyMarkup(markup);

                return msg;
            });
    }

    private InlineKeyboardButton button(String text, String data) {
        InlineKeyboardButton b = new InlineKeyboardButton();
        b.setText(text);
        b.setCallbackData(data);
        return b;
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

