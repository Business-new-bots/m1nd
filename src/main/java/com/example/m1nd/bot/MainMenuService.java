package com.example.m1nd.bot;

import com.example.m1nd.model.FactTopic;
import com.example.m1nd.model.Game;
import com.example.m1nd.model.IdeaTopic;
import com.example.m1nd.model.MotivationTopic;
import com.example.m1nd.service.FactTopicService;
import com.example.m1nd.service.GameService;
import com.example.m1nd.service.IdeaTopicService;
import com.example.m1nd.service.LLMService;
import com.example.m1nd.service.MotivationTopicService;
import com.example.m1nd.service.TaskService;
import com.example.m1nd.service.UserProgressService;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class MainMenuService {

    private static final Logger log = LoggerFactory.getLogger(MainMenuService.class);

    private final LLMService llmService;
    private final FactTopicService factTopicService;
    private final IdeaTopicService ideaTopicService;
    private final MotivationTopicService motivationTopicService;
    private final GameService gameService;
    private final TaskService taskService;
    private final UserProgressService userProgressService;

    private enum PuzzleCategory {
        RIDDLE,
        LOGIC,
        PERSONALITY,
        IQ
    }

    private static class PuzzleSession {
        private PuzzleCategory category;
        private String question;
        private String answer;
        private String explanation;
        private int iqIndex;
        private int iqTotal;
    }

    private final Map<Long, PuzzleSession> puzzleSessions = new ConcurrentHashMap<>();

    /** Сессия игры: угадай число или викторина */
    private static class GameSession {
        private String gameCode;
        private Integer secretNumber;
        private String messageToUser;
        private int quizIndex;
        private int quizTotal = 5;
        private String question;
        private String answer;
        private String explanation;
        private String options;
    }
    private final Map<Long, GameSession> gameSessions = new ConcurrentHashMap<>();

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

        // 🧠 Факты
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(button("🧠 Факты", "main_facts"));

        // 🎯 Задания дня
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(button("🎯 Задания дня", "main_daily_tasks"));

        // 🧩 Загадки и тесты
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        row3.add(button("🧩 Загадки и тесты", "main_puzzles_tests"));

        // 💡 Идеи и инсайты
        List<InlineKeyboardButton> row4 = new ArrayList<>();
        row4.add(button("💡 Идеи и инсайты", "main_ideas_insights"));

        // 🚀 Мотивация | 🎮 Игры
        List<InlineKeyboardButton> row5 = new ArrayList<>();
        row5.add(button("🚀 Мотивация", "main_motivation"));
        row5.add(button("🎮 Игры", "main_games"));

        // 📊 Мой прогресс
        List<InlineKeyboardButton> row6 = new ArrayList<>();
        row6.add(button("📊 Мой прогресс", "main_progress"));

        // Задать вопрос боту
        List<InlineKeyboardButton> row7 = new ArrayList<>();
        row7.add(button("💬 Задать вопрос боту", "main_ask_question"));

        keyboard.add(row1);
        keyboard.add(row2);
        keyboard.add(row3);
        keyboard.add(row4);
        keyboard.add(row5);
        keyboard.add(row6);
        keyboard.add(row7);

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
        return data != null
            && (data.startsWith("main_")
                || data.startsWith("facts_")
                || data.startsWith("fact_")
                || data.startsWith("ideas_")
                || data.startsWith("idea_")
                || data.startsWith("motiv_")
                || data.startsWith("motivation_")
                || data.startsWith("game_")
                || data.startsWith("games_")
                || data.startsWith("task_")
                || data.startsWith("puzzle_"));
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

        // Меню "Загадки и тесты"
        if ("main_puzzles_tests".equals(data) || "puzzle_menu".equals(data)) {
            SendMessage message = buildPuzzlesMenuMessage(chatId);
            return Mono.just(MainMenuResult.single(message, "✅"));
        }

        // Возврат в главное меню из заданий
        if ("main_menu_back".equals(data)) {
            SendMessage message = buildMainMenuMessage(chatId);
            return Mono.just(MainMenuResult.single(message, "✅"));
        }

        // Задать вопрос боту
        if ("main_ask_question".equals(data)) {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("Ожидаю вопрос");
            return Mono.just(MainMenuResult.single(message, "✅"));
        }

        // Идеи и инсайты — выбор темы
        if ("main_ideas_insights".equals(data) || "idea_menu".equals(data)) {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("💡 Идеи и инсайты\n\nВыбери тему:");
            message.setReplyMarkup(createIdeasTopicsKeyboard());
            return Mono.just(MainMenuResult.single(message, "✅"));
        }

        // Мотивация — выбор подпункта
        if ("main_motivation".equals(data) || "motivation_menu".equals(data)) {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("🚀 Мотивация\n\nВыбери раздел:");
            message.setReplyMarkup(createMotivationTopicsKeyboard());
            return Mono.just(MainMenuResult.single(message, "✅"));
        }

        // Игры — выбор игры
        if ("main_games".equals(data) || "games_menu".equals(data)) {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("🎮 Игры\n\nВыбери игру:");
            message.setReplyMarkup(createGamesKeyboard());
            return Mono.just(MainMenuResult.single(message, "✅"));
        }

        // Мой прогресс
        if ("main_progress".equals(data)) {
            int riddles = userProgressService.getRiddlesSolved(userId);
            int tasks = userProgressService.getTasksCompleted(userId);
            int facts = userProgressService.getFactsViewed(userId);
            int ideas = userProgressService.getIdeasViewed(userId);
            int motiv = userProgressService.getMotivationsViewed(userId);
            int games = userProgressService.getGamesPlayed(userId);
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(
                "📊 Мой прогресс\n\n" +
                    "Решено загадок: " + riddles + "\n" +
                    "Выполнено заданий: " + tasks + "\n" +
                    "Посмотрено фактов: " + facts + "\n" +
                    "Посмотрено идей: " + ideas + "\n" +
                    "Получено мотиваций: " + motiv + "\n" +
                    "Сыграно игр: " + games
            );
            message.setReplyMarkup(createProgressBackKeyboard());
            return Mono.just(MainMenuResult.single(message, "✅"));
        }

        // Задание отмечено выполненным
        if ("task_done".equals(data)) {
            userProgressService.incrementTasksCompleted(userId);
            SendMessage confirm = new SendMessage();
            confirm.setChatId(chatId.toString());
            confirm.setText("✅ Задание отмечено выполненным!");
            confirm.setReplyMarkup(createTaskActionsKeyboard());
            return Mono.just(MainMenuResult.single(confirm, "✅"));
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

        // Загадки и тесты
        if ("puzzle_riddle".equals(data)) {
            return buildPuzzleQuestionMessage(chatId, userId, PuzzleCategory.RIDDLE)
                .map(msg -> MainMenuResult.single(msg, "✅"));
        }

        if ("puzzle_logic".equals(data)) {
            return buildPuzzleQuestionMessage(chatId, userId, PuzzleCategory.LOGIC)
                .map(msg -> MainMenuResult.single(msg, "✅"));
        }

        if ("puzzle_personality".equals(data)) {
            return buildPuzzleQuestionMessage(chatId, userId, PuzzleCategory.PERSONALITY)
                .map(msg -> MainMenuResult.single(msg, "✅"));
        }

        if ("puzzle_iq_start".equals(data) || "puzzle_iq_next".equals(data)) {
            PuzzleSession session = puzzleSessions.computeIfAbsent(userId, id -> {
                PuzzleSession s = new PuzzleSession();
                s.category = PuzzleCategory.IQ;
                s.iqIndex = 0;
                s.iqTotal = 5;
                return s;
            });

            if (session.category != PuzzleCategory.IQ) {
                session.category = PuzzleCategory.IQ;
                session.iqIndex = 0;
                session.iqTotal = 5;
            }

            if ("puzzle_iq_next".equals(data) && session.iqIndex >= session.iqTotal) {
                SendMessage finished = new SendMessage();
                finished.setChatId(chatId.toString());
                finished.setText("🧠 IQ-мини-тест завершён! Спасибо, что прошли тест.");
                finished.setReplyMarkup(createPuzzlesMenuKeyboard());
                puzzleSessions.remove(userId);
                return Mono.just(MainMenuResult.single(finished, "✅ Тест завершён"));
            }

            return buildIqQuestionMessage(chatId, userId, session)
                .map(msg -> MainMenuResult.single(msg, "✅"));
        }

        if ("puzzle_show_answer".equals(data)) {
            PuzzleSession session = puzzleSessions.get(userId);
            SendMessage msg = new SendMessage();
            msg.setChatId(chatId.toString());

            if (session == null || session.question == null || session.answer == null) {
                msg.setText("Сейчас нет активной загадки или задания. Выберите раздел:");
                msg.setReplyMarkup(createPuzzlesMenuKeyboard());
                return Mono.just(MainMenuResult.single(msg, "❌ Нет активной загадки"));
            }

            StringBuilder sb = new StringBuilder();
            sb.append("❓ Вопрос:\n").append(session.question).append("\n\n");
            sb.append("✅ Правильный ответ: ").append(session.answer).append("\n");
            if (session.explanation != null && !session.explanation.isBlank()) {
                sb.append("\n💡 Объяснение:\n").append(session.explanation);
            }
            msg.setText(sb.toString());
            msg.setReplyMarkup(createAfterAnswerKeyboard(session.category, session));

            return Mono.just(MainMenuResult.single(msg, "✅ Ответ показан"));
        }

        // Остальные пункты главного меню пока заглушки
        if (data.startsWith("main_")) {
            String responseText;
            switch (data) {
                case "main_daily_tasks":
                    responseText = "🎯 Задания дня\n\nЗдесь будут короткие задания на день. Раздел скоро заработает.";
                    break;
                case "main_puzzles_tests":
                    responseText = "🧩 Загадки и тесты\n\nВыбирай раздел:";
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

        // Выбор темы идей — запрос одной идеи у LLM
        if (data.startsWith("ideas_")) {
            String category = data.substring("ideas_".length());
            return buildIdeaMessage(chatId, userId, category)
                .map(msg -> MainMenuResult.single(msg, "✅"))
                .onErrorResume(error -> {
                    log.error("Ошибка при запросе идеи у LLM", error);
                    SendMessage errorMessage = new SendMessage();
                    errorMessage.setChatId(chatId.toString());
                    errorMessage.setText("Не удалось получить идею. Попробуйте ещё раз позже.");
                    return Mono.just(MainMenuResult.single(errorMessage, "❌ Ошибка"));
                });
        }

        // Следующая идея по той же теме
        if (data.startsWith("idea_next:")) {
            String category = data.substring("idea_next:".length());
            return buildIdeaMessage(chatId, userId, category)
                .map(msg -> MainMenuResult.single(msg, "✅"))
                .onErrorResume(error -> {
                    log.error("Ошибка при запросе следующей идеи у LLM", error);
                    SendMessage errorMessage = new SendMessage();
                    errorMessage.setChatId(chatId.toString());
                    errorMessage.setText("Не удалось получить идею. Попробуйте ещё раз позже.");
                    return Mono.just(MainMenuResult.single(errorMessage, "❌ Ошибка"));
                });
        }

        // Выбор раздела мотивации — запрос у LLM
        if (data.startsWith("motiv_")) {
            String category = data.substring("motiv_".length());
            return buildMotivationMessage(chatId, userId, category)
                .map(msg -> MainMenuResult.single(msg, "✅"))
                .onErrorResume(error -> {
                    log.error("Ошибка при запросе мотивации у LLM", error);
                    SendMessage errorMessage = new SendMessage();
                    errorMessage.setChatId(chatId.toString());
                    errorMessage.setText("Не удалось получить контент. Попробуйте ещё раз позже.");
                    return Mono.just(MainMenuResult.single(errorMessage, "❌ Ошибка"));
                });
        }

        // Следующая мотивация по той же теме
        if (data.startsWith("motivation_next:")) {
            String category = data.substring("motivation_next:".length());
            return buildMotivationMessage(chatId, userId, category)
                .map(msg -> MainMenuResult.single(msg, "✅"))
                .onErrorResume(error -> {
                    log.error("Ошибка при запросе следующей мотивации у LLM", error);
                    SendMessage errorMessage = new SendMessage();
                    errorMessage.setChatId(chatId.toString());
                    errorMessage.setText("Не удалось получить контент. Попробуйте ещё раз позже.");
                    return Mono.just(MainMenuResult.single(errorMessage, "❌ Ошибка"));
                });
        }

        // Игры: выбор игры по коду
        if (data.startsWith("games_")) {
            String gameCode = data.substring("games_".length());
            if ("guess_number".equals(gameCode)) {
                userProgressService.incrementGamesPlayed(userId);
                return buildGuessNumberMessage(chatId, userId)
                    .map(msg -> MainMenuResult.single(msg, "✅"))
                    .onErrorResume(error -> {
                        log.error("Ошибка при старте «Угадай число»", error);
                        return Mono.just(MainMenuResult.single(errorMessage(chatId, "Не удалось начать игру."), "❌"));
                    });
            }
            if ("quiz".equals(gameCode)) {
                userProgressService.incrementGamesPlayed(userId);
                GameSession session = gameSessions.computeIfAbsent(userId, id -> new GameSession());
                session.gameCode = "quiz";
                session.quizIndex = 0;
                session.quizTotal = 5;
                return buildQuizQuestionMessage(chatId, userId, session)
                    .map(msg -> MainMenuResult.single(msg, "✅"))
                    .onErrorResume(error -> {
                        log.error("Ошибка при старте викторины", error);
                        return Mono.just(MainMenuResult.single(errorMessage(chatId, "Не удалось начать викторину."), "❌"));
                    });
            }
            // Правда/ложь, челленджи — заглушка
            Optional<Game> gameOpt = gameService.findByCode(gameCode);
            SendMessage msg = new SendMessage();
            msg.setChatId(chatId.toString());
            if (gameOpt.isPresent()) {
                msg.setText("🎮 " + gameOpt.get().getTitle() + "\n\nЭтот режим скоро появится.");
            } else {
                msg.setText("Игра не найдена.");
            }
            msg.setReplyMarkup(createGameBackKeyboard());
            return Mono.just(MainMenuResult.single(msg, "✅"));
        }

        // Угадай число — попробовать ещё раз (новый запрос к агенту)
        if ("game_guess_again".equals(data)) {
            return buildGuessNumberMessage(chatId, userId)
                .map(msg -> MainMenuResult.single(msg, "✅"))
                .onErrorResume(error -> Mono.just(MainMenuResult.single(errorMessage(chatId, "Не удалось начать игру."), "❌")));
        }

        // Викторина — следующий вопрос
        if ("game_quiz_next".equals(data)) {
            GameSession session = gameSessions.get(userId);
            if (session == null || !"quiz".equals(session.gameCode)) {
                SendMessage msg = new SendMessage();
                msg.setChatId(chatId.toString());
                msg.setText("Выбери игру:");
                msg.setReplyMarkup(createGamesKeyboard());
                return Mono.just(MainMenuResult.single(msg, "✅"));
            }
            session.quizIndex++;
            if (session.quizIndex >= session.quizTotal) {
                gameSessions.remove(userId);
                SendMessage finished = new SendMessage();
                finished.setChatId(chatId.toString());
                finished.setText("❓ Викторина завершена! Спасибо, что прошли тест.");
                finished.setReplyMarkup(createGamesMenuKeyboard());
                return Mono.just(MainMenuResult.single(finished, "✅"));
            }
            return buildQuizQuestionMessage(chatId, userId, session)
                .map(msg -> MainMenuResult.single(msg, "✅"))
                .onErrorResume(error -> Mono.just(MainMenuResult.single(errorMessage(chatId, "Ошибка загрузки вопроса."), "❌")));
        }

        // Викторина — показать ответ
        if ("game_quiz_show_answer".equals(data)) {
            GameSession session = gameSessions.get(userId);
            if (session == null || session.question == null || session.answer == null) {
                SendMessage msg = new SendMessage();
                msg.setChatId(chatId.toString());
                msg.setText("Выбери игру:");
                msg.setReplyMarkup(createGamesKeyboard());
                return Mono.just(MainMenuResult.single(msg, "✅"));
            }
            StringBuilder sb = new StringBuilder();
            sb.append("❓ Вопрос:\n").append(session.question).append("\n\n");
            sb.append("✅ Правильный ответ: ").append(session.answer).append("\n");
            if (session.explanation != null && !session.explanation.isBlank()) {
                sb.append("\n💡 ").append(session.explanation);
            }
            SendMessage msg = new SendMessage();
            msg.setChatId(chatId.toString());
            msg.setText(sb.toString());
            msg.setReplyMarkup(createQuizAfterAnswerKeyboard(session));
            return Mono.just(MainMenuResult.single(msg, "✅"));
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

        List<InlineKeyboardButton> rowBack = new ArrayList<>();
        rowBack.add(button("◀️ Возврат в главное меню", "main_menu_back"));
        keyboard.add(rowBack);

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
                userProgressService.incrementFactsViewed(userId);
                SendMessage factMessage = new SendMessage();
                factMessage.setChatId(chatId.toString());
                factMessage.setText(answer);

                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

                List<InlineKeyboardButton> row1 = new ArrayList<>();
                row1.add(button("➡️ Следующий факт", "fact_next:" + nextCategory));
                row1.add(button("📋 Главное меню", "main_menu_back"));
                keyboard.add(row1);

                List<InlineKeyboardButton> row2 = new ArrayList<>();
                row2.add(button("📋 Случайный факт по теме", "fact_menu"));
                keyboard.add(row2);

                markup.setKeyboard(keyboard);
                factMessage.setReplyMarkup(markup);

                return factMessage;
            });
    }

    private InlineKeyboardMarkup createIdeasTopicsKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        List<IdeaTopic> topics = ideaTopicService.findAll();
        List<InlineKeyboardButton> currentRow = new ArrayList<>();

        for (IdeaTopic topic : topics) {
            currentRow.add(button(topic.getTitle(), "ideas_" + topic.getCode()));
            if (currentRow.size() == 2) {
                keyboard.add(currentRow);
                currentRow = new ArrayList<>();
            }
        }
        if (!currentRow.isEmpty()) {
            keyboard.add(currentRow);
        }

        List<InlineKeyboardButton> rowBack = new ArrayList<>();
        rowBack.add(button("◀️ Возврат в главное меню", "main_menu_back"));
        keyboard.add(rowBack);
        markup.setKeyboard(keyboard);
        return markup;
    }

    private Mono<SendMessage> buildIdeaMessage(Long chatId, Long userId, String category) {
        Optional<IdeaTopic> topicOpt = ideaTopicService.findByCode(category);
        String prompt;
        String nextCategory;

        if (topicOpt.isPresent()) {
            IdeaTopic topic = topicOpt.get();
            prompt = topic.getPrompt();
            nextCategory = topic.getCode();
        } else {
            log.warn("Не найдена тема идей с кодом {}, используем дефолтный промпт", category);
            prompt = "Дай одну идею или инсайт. Кратко: 1–3 предложения.";
            nextCategory = category != null ? category : "default";
        }

        return llmService.getAnswer(prompt, userId)
            .map(answer -> {
                userProgressService.incrementIdeasViewed(userId);
                SendMessage ideaMessage = new SendMessage();
                ideaMessage.setChatId(chatId.toString());
                ideaMessage.setText(answer);

                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

                List<InlineKeyboardButton> row1 = new ArrayList<>();
                row1.add(button("➡️ Следующая идея", "idea_next:" + nextCategory));
                row1.add(button("📋 Главное меню", "main_menu_back"));
                keyboard.add(row1);

                List<InlineKeyboardButton> row2 = new ArrayList<>();
                row2.add(button("📋 К выбору тем", "idea_menu"));
                keyboard.add(row2);

                markup.setKeyboard(keyboard);
                ideaMessage.setReplyMarkup(markup);
                return ideaMessage;
            });
    }

    private InlineKeyboardMarkup createMotivationTopicsKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        List<MotivationTopic> topics = motivationTopicService.findAll();
        List<InlineKeyboardButton> currentRow = new ArrayList<>();

        for (MotivationTopic topic : topics) {
            currentRow.add(button(topic.getTitle(), "motiv_" + topic.getCode()));
            if (currentRow.size() == 2) {
                keyboard.add(currentRow);
                currentRow = new ArrayList<>();
            }
        }
        if (!currentRow.isEmpty()) {
            keyboard.add(currentRow);
        }

        List<InlineKeyboardButton> rowBack = new ArrayList<>();
        rowBack.add(button("◀️ Возврат в главное меню", "main_menu_back"));
        keyboard.add(rowBack);
        markup.setKeyboard(keyboard);
        return markup;
    }

    private Mono<SendMessage> buildMotivationMessage(Long chatId, Long userId, String category) {
        Optional<MotivationTopic> topicOpt = motivationTopicService.findByCode(category);
        String prompt;
        String nextCategory;

        if (topicOpt.isPresent()) {
            MotivationTopic topic = topicOpt.get();
            prompt = topic.getPrompt();
            nextCategory = topic.getCode();
        } else {
            log.warn("Не найдена тема мотивации с кодом {}, используем дефолтный промпт", category);
            prompt = "Дай одну короткую мотивирующую мысль. Кратко: 1–2 предложения.";
            nextCategory = category != null ? category : "default";
        }

        return llmService.getAnswer(prompt, userId)
            .map(answer -> {
                userProgressService.incrementMotivationsViewed(userId);
                SendMessage msg = new SendMessage();
                msg.setChatId(chatId.toString());
                msg.setText(answer);

                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

                List<InlineKeyboardButton> row1 = new ArrayList<>();
                row1.add(button("➡️ Следующее", "motivation_next:" + nextCategory));
                row1.add(button("📋 Главное меню", "main_menu_back"));
                keyboard.add(row1);

                List<InlineKeyboardButton> row2 = new ArrayList<>();
                row2.add(button("📋 К выбору разделов", "motivation_menu"));
                keyboard.add(row2);

                markup.setKeyboard(keyboard);
                msg.setReplyMarkup(markup);
                return msg;
            });
    }

    private InlineKeyboardMarkup createGamesKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        List<Game> games = gameService.findAll();
        List<InlineKeyboardButton> currentRow = new ArrayList<>();
        for (Game game : games) {
            currentRow.add(button(game.getTitle(), "games_" + game.getCode()));
            if (currentRow.size() == 2) {
                keyboard.add(currentRow);
                currentRow = new ArrayList<>();
            }
        }
        if (!currentRow.isEmpty()) {
            keyboard.add(currentRow);
        }
        List<InlineKeyboardButton> rowBack = new ArrayList<>();
        rowBack.add(button("◀️ Возврат в главное меню", "main_menu_back"));
        keyboard.add(rowBack);
        markup.setKeyboard(keyboard);
        return markup;
    }

    private InlineKeyboardMarkup createGameBackKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(button("📋 Главное меню", "main_menu_back"));
        keyboard.add(row);
        markup.setKeyboard(keyboard);
        return markup;
    }

    private InlineKeyboardMarkup createProgressBackKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        keyboard.add(List.of(button("📋 Главное меню", "main_menu_back")));
        markup.setKeyboard(keyboard);
        return markup;
    }

    private InlineKeyboardMarkup createTaskActionsKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(button("✅ Выполнил", "task_done"));
        row1.add(button("➡️ Следующее задание", "task_next"));
        keyboard.add(row1);
        keyboard.add(List.of(button("📋 Главное меню", "main_menu_back")));
        markup.setKeyboard(keyboard);
        return markup;
    }

    private InlineKeyboardMarkup createGamesMenuKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(button("📋 К выбору игр", "games_menu"));
        row1.add(button("📋 Главное меню", "main_menu_back"));
        keyboard.add(row1);
        markup.setKeyboard(keyboard);
        return markup;
    }

    private InlineKeyboardMarkup createGuessNumberKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(button("🔄 Попробовать еще раз", "game_guess_again"));
        row.add(button("📋 Главное меню", "main_menu_back"));
        keyboard.add(row);
        markup.setKeyboard(keyboard);
        return markup;
    }

    private InlineKeyboardMarkup createQuizAfterAnswerKeyboard(GameSession session) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        if (session.quizIndex + 1 < session.quizTotal) {
            row1.add(button("➡️ Следующий вопрос", "game_quiz_next"));
        }
        row1.add(button("📋 Главное меню", "main_menu_back"));
        keyboard.add(row1);
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(button("📋 К выбору игр", "games_menu"));
        keyboard.add(row2);
        markup.setKeyboard(keyboard);
        return markup;
    }

    private SendMessage errorMessage(Long chatId, String text) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText(text);
        return msg;
    }

    private Mono<SendMessage> buildGuessNumberMessage(Long chatId, Long userId) {
        String prompt = gameService.findByCode("guess_number")
            .map(Game::getPrompt)
            .orElse("Загадай число от 1 до 10. Ответь в формате: первая строка NUMBER: цифра. Вторая строка MESSAGE: приветственное сообщение пользователю.");
        return llmService.getAnswer(prompt, userId)
            .map(raw -> {
                int secret = 5;
                String messageToUser = "Я загадал число от 1 до 10. Попробуй угадать.";
                if (raw != null && !raw.isBlank()) {
                    String[] lines = raw.split("\\r?\\n");
                    for (String line : lines) {
                        String t = line.trim();
                        if (t.toUpperCase().startsWith("NUMBER:")) {
                            String numStr = t.substring(7).trim().replaceAll("[^0-9]", "");
                            if (!numStr.isEmpty()) {
                                try {
                                    int n = Integer.parseInt(numStr);
                                    if (n >= 1 && n <= 10) {
                                        secret = n;
                                    }
                                } catch (NumberFormatException ignored) {
                                }
                            }
                        } else if (t.toUpperCase().startsWith("MESSAGE:")) {
                            messageToUser = t.substring(8).trim();
                            if (messageToUser.isEmpty()) {
                                messageToUser = "Я загадал число от 1 до 10. Попробуй угадать.";
                            }
                        }
                    }
                }
                GameSession session = gameSessions.computeIfAbsent(userId, id -> new GameSession());
                session.gameCode = "guess_number";
                session.secretNumber = secret;
                session.messageToUser = messageToUser;

                SendMessage msg = new SendMessage();
                msg.setChatId(chatId.toString());
                msg.setText("🎮 Игра\n\n" + messageToUser);
                msg.setReplyMarkup(createGuessNumberKeyboard());
                return msg;
            });
    }

    private Mono<SendMessage> buildQuizQuestionMessage(Long chatId, Long userId, GameSession session) {
        int current = session.quizIndex + 1;
        int total = session.quizTotal;
        String prompt = gameService.findByCode("quiz")
            .map(Game::getPrompt)
            .orElse("Сгенерируй один вопрос викторины. Формат: QUESTION: ... OPTIONS: a) ... b) ... c) ... d) ... ANSWER: a|b|c|d EXPLANATION: ...");
        String promptWithNum = "Вопрос " + current + " из " + total + ".\n" + prompt;

        return llmService.getAnswer(promptWithNum, userId)
            .map(raw -> {
                ParsedPuzzle parsed = parsePuzzle(raw);
                session.question = parsed.question;
                session.answer = parsed.answer;
                session.explanation = parsed.explanation;
                session.options = parsed.options;

                StringBuilder text = new StringBuilder();
                text.append("❓ Викторина. Вопрос ").append(current).append(" из ").append(total).append(":\n\n");
                text.append(parsed.question).append("\n\n");
                if (parsed.options != null && !parsed.options.isBlank()) {
                    text.append(parsed.options).append("\n\n");
                } else {
                    text.append("Ответь буквой a, b, c или d.\n\n");
                }
                text.append("Ответь буквой (a, b, c или d) или нажми «Показать ответ».");

                SendMessage msg = new SendMessage();
                msg.setChatId(chatId.toString());
                msg.setText(text.toString());

                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
                List<InlineKeyboardButton> row1 = new ArrayList<>();
                row1.add(button("✅ Показать ответ", "game_quiz_show_answer"));
                keyboard.add(row1);
                List<InlineKeyboardButton> row2 = new ArrayList<>();
                row2.add(button("📋 К выбору игр", "games_menu"));
                keyboard.add(row2);
                markup.setKeyboard(keyboard);
                msg.setReplyMarkup(markup);
                return msg;
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
                msg.setReplyMarkup(createTaskActionsKeyboard());

                return msg;
            });
    }

    public GameAnswerResult handleGameAnswer(Long chatId, Long userId, String messageText) {
        GameSession gameSession = gameSessions.get(userId);
        if (gameSession == null) {
            return GameAnswerResult.notHandled();
        }
        if ("guess_number".equals(gameSession.gameCode)) {
            int guessed;
            try {
                String trimmed = messageText != null ? messageText.trim().replaceAll("[^0-9]", "") : "";
                if (trimmed.isEmpty()) {
                    return GameAnswerResult.notHandled();
                }
                guessed = Integer.parseInt(trimmed);
            } catch (NumberFormatException e) {
                return GameAnswerResult.notHandled();
            }
            int secret = gameSession.secretNumber != null ? gameSession.secretNumber : 5;
            gameSessions.remove(userId);

            String reply;
            if (guessed == secret) {
                reply = "✅ Вы угадали! Загаданное число было " + secret + ".";
            } else {
                reply = "❌ Не угадали. Загаданное число было " + secret + ".";
            }
            SendMessage msg = new SendMessage();
            msg.setChatId(chatId.toString());
            msg.setText(reply);
            msg.setReplyMarkup(createGuessNumberKeyboard());
            return GameAnswerResult.handled(List.of(msg));
        }
        if ("quiz".equals(gameSession.gameCode) && gameSession.question != null && gameSession.answer != null) {
            String userNormalized = extractFirstLetter(normalizeAnswer(messageText));
            String correctNormalized = extractFirstLetter(normalizeAnswer(gameSession.answer));
            boolean correct = !userNormalized.isEmpty() && !correctNormalized.isEmpty()
                && userNormalized.charAt(0) == correctNormalized.charAt(0);

            StringBuilder sb = new StringBuilder();
            if (correct) {
                sb.append("✅ Верно!\n\n");
            } else {
                sb.append("❌ Неверно.\n\n");
            }
            sb.append("❓ Вопрос:\n").append(gameSession.question).append("\n\n");
            sb.append("✅ Правильный ответ: ").append(gameSession.answer).append("\n");
            if (gameSession.explanation != null && !gameSession.explanation.isBlank()) {
                sb.append("\n💡 ").append(gameSession.explanation);
            }
            SendMessage msg = new SendMessage();
            msg.setChatId(chatId.toString());
            msg.setText(sb.toString());
            msg.setReplyMarkup(createQuizAfterAnswerKeyboard(gameSession));
            return GameAnswerResult.handled(List.of(msg));
        }
        return GameAnswerResult.notHandled();
    }

    public PuzzleAnswerResult handlePuzzleAnswer(Long chatId, Long userId, String messageText) {
        PuzzleSession session = puzzleSessions.get(userId);
        if (session == null || session.category == null || session.question == null || session.answer == null) {
            return PuzzleAnswerResult.notHandled();
        }

        String userNormalized = normalizeAnswer(messageText);
        String correctNormalized = normalizeAnswer(session.answer);

        boolean correct;
        if (session.category == PuzzleCategory.IQ) {
            // Для IQ ожидаем букву a/b/c/d
            String userLetter = extractFirstLetter(userNormalized);
            String correctLetter = extractFirstLetter(correctNormalized);
            correct = !userLetter.isEmpty() && !correctLetter.isEmpty()
                && userLetter.charAt(0) == Character.toLowerCase(correctLetter.charAt(0));
        } else {
            correct = !userNormalized.isEmpty()
                && !correctNormalized.isEmpty()
                && userNormalized.equals(correctNormalized);
        }

        if (correct) {
            userProgressService.incrementRiddlesSolved(userId);
        }

        StringBuilder sb = new StringBuilder();
        if (correct) {
            sb.append("✅ Вы абсолютно правы!\n\n");
        } else {
            sb.append("❌ Ответ не верен.\n\n");
        }
        sb.append("❓ Вопрос:\n").append(session.question).append("\n\n");
        sb.append("✅ Правильный ответ: ").append(session.answer).append("\n");
        if (session.explanation != null && !session.explanation.isBlank()) {
            sb.append("\n💡 Объяснение:\n").append(session.explanation);
        }

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText(sb.toString());
        msg.setReplyMarkup(createAfterAnswerKeyboard(session.category, session));

        List<SendMessage> messages = new ArrayList<>();
        messages.add(msg);
        return PuzzleAnswerResult.handled(messages);
    }

    private String normalizeAnswer(String text) {
        if (text == null) {
            return "";
        }
        String lower = text.trim().toLowerCase();
        // Убираем все кроме букв и цифр
        return lower.replaceAll("[^a-zа-я0-9]+", "");
    }

    private String extractFirstLetter(String text) {
        if (text == null) {
            return "";
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetter(c)) {
                return String.valueOf(Character.toLowerCase(c));
            }
        }
        return "";
    }

    private SendMessage buildPuzzlesMenuMessage(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("🧩 Загадки и тесты\n\nВыбери категорию:");
        message.setReplyMarkup(createPuzzlesMenuKeyboard());
        return message;
    }

    private InlineKeyboardMarkup createPuzzlesMenuKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(button("🧩 Загадки", "puzzle_riddle"));
        row1.add(button("🧠 Логические задачи", "puzzle_logic"));

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(button("🧪 Тесты личности", "puzzle_personality"));
        row2.add(button("🧠 IQ-мини-тест", "puzzle_iq_start"));

        List<InlineKeyboardButton> row3 = new ArrayList<>();
        row3.add(button("📋 Главное меню", "main_menu_back"));

        keyboard.add(row1);
        keyboard.add(row2);
        keyboard.add(row3);

        markup.setKeyboard(keyboard);
        return markup;
    }

    private InlineKeyboardMarkup createAfterAnswerKeyboard(PuzzleCategory category, PuzzleSession session) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        if (category == PuzzleCategory.IQ) {
            if (session.iqIndex < session.iqTotal) {
                List<InlineKeyboardButton> row1 = new ArrayList<>();
                row1.add(button("➡️ Следующий вопрос", "puzzle_iq_next"));
                keyboard.add(row1);
            } else {
                List<InlineKeyboardButton> row1 = new ArrayList<>();
                row1.add(button("🧠 Пройти ещё раз", "puzzle_iq_start"));
                keyboard.add(row1);
            }
        } else {
            List<InlineKeyboardButton> row1 = new ArrayList<>();
            switch (category) {
                case RIDDLE -> row1.add(button("➡️ Следующая загадка", "puzzle_riddle"));
                case LOGIC -> row1.add(button("➡️ Следующая задача", "puzzle_logic"));
                case PERSONALITY -> row1.add(button("➡️ Следующий тест", "puzzle_personality"));
                default -> {
                }
            }
            if (!row1.isEmpty()) {
                keyboard.add(row1);
            }
        }

        List<InlineKeyboardButton> rowBack = new ArrayList<>();
        rowBack.add(button("◀️ Меню загадок", "puzzle_menu"));
        keyboard.add(rowBack);

        markup.setKeyboard(keyboard);
        return markup;
    }

    private Mono<SendMessage> buildPuzzleQuestionMessage(Long chatId, Long userId, PuzzleCategory category) {
        String prompt;
        switch (category) {
            case RIDDLE -> prompt =
                "Сгенерируй одну короткую загадку на русском языке.\n" +
                    "Ответ должен быть одним словом или короткой фразой.\n" +
                    "Формат ответа СТРОГО такой (без лишнего текста до и после):\n" +
                    "QUESTION: <текст загадки>\n" +
                    "ANSWER: <правильный ответ>\n" +
                    "EXPLANATION: <краткое объяснение ответа>";
            case LOGIC -> prompt =
                "Сгенерируй одну логическую задачу на русском языке с однозначным правильным ответом.\n" +
                    "Формат ответа СТРОГО такой (без лишнего текста до и после):\n" +
                    "QUESTION: <текст задачи>\n" +
                    "ANSWER: <правильный ответ>\n" +
                    "EXPLANATION: <краткое объяснение решения>";
            case PERSONALITY -> prompt =
                "Сгенерируй один короткий вопрос теста личности на русском языке.\n" +
                    "Пусть у вопроса будет однозначный 'идеальный' ответ (но пользователь может отвечать по-своему).\n" +
                    "Формат ответа СТРОГО такой (без лишнего текста до и после):\n" +
                    "QUESTION: <текст вопроса>\n" +
                    "ANSWER: <идеальный или примерный ответ>\n" +
                    "EXPLANATION: <краткая интерпретация ответа>";
            default -> prompt =
                "Сгенерируй одну короткую загадку на русском языке в формате:\n" +
                    "QUESTION: ...\nANSWER: ...\nEXPLANATION: ...";
        }

        return llmService.getAnswer(prompt, userId)
            .map(raw -> {
                ParsedPuzzle parsed = parsePuzzle(raw);
                PuzzleSession session = puzzleSessions.computeIfAbsent(userId, id -> new PuzzleSession());
                session.category = category;
                session.question = parsed.question;
                session.answer = parsed.answer;
                session.explanation = parsed.explanation;

                SendMessage msg = new SendMessage();
                msg.setChatId(chatId.toString());
                msg.setText("❓ " + session.question + "\n\n" +
                    "Можешь ответить текстом или нажать «Показать ответ».");

                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

                List<InlineKeyboardButton> row1 = new ArrayList<>();
                row1.add(button("✅ Показать ответ", "puzzle_show_answer"));
                keyboard.add(row1);

                List<InlineKeyboardButton> row2 = new ArrayList<>();
                switch (category) {
                    case RIDDLE -> row2.add(button("➡️ Следующая загадка", "puzzle_riddle"));
                    case LOGIC -> row2.add(button("➡️ Следующая задача", "puzzle_logic"));
                    case PERSONALITY -> row2.add(button("➡️ Следующий тест", "puzzle_personality"));
                    default -> {
                    }
                }
                if (!row2.isEmpty()) {
                    keyboard.add(row2);
                }

                List<InlineKeyboardButton> row3 = new ArrayList<>();
                row3.add(button("◀️ Меню загадок", "puzzle_menu"));
                keyboard.add(row3);

                markup.setKeyboard(keyboard);
                msg.setReplyMarkup(markup);

                return msg;
            });
    }

    private Mono<SendMessage> buildIqQuestionMessage(Long chatId, Long userId, PuzzleSession session) {
        session.category = PuzzleCategory.IQ;
        if (session.iqTotal <= 0) {
            session.iqTotal = 5;
        }
        session.iqIndex++;

        int current = session.iqIndex;
        int total = session.iqTotal;

        String prompt =
            "Сгенерируй один вопрос мини IQ-теста на русском языке (вопрос № " + current + " из " + total + ").\n" +
                "Дай ровно 4 варианта ответов (a, b, c, d), ровно один из них должен быть правильным.\n" +
                "Формат ответа СТРОГО такой (без лишнего текста до и после):\n" +
                "QUESTION: <текст вопроса>\n" +
                "OPTIONS: a) <вариант>; b) <вариант>; c) <вариант>; d) <вариант>\n" +
                "ANSWER: a|b|c|d\n" +
                "EXPLANATION: <краткое объяснение правильного ответа>";

        return llmService.getAnswer(prompt, userId)
            .map(raw -> {
                ParsedPuzzle parsed = parsePuzzle(raw);
                session.question = parsed.question;
                session.answer = parsed.answer;
                session.explanation = parsed.explanation;

                String optionsLine = parsed.options != null ? parsed.options : "";

                StringBuilder text = new StringBuilder();
                text.append("🧠 IQ-вопрос ").append(current).append(" из ").append(total).append(":\n\n");
                text.append(parsed.question).append("\n\n");
                if (!optionsLine.isBlank()) {
                    text.append(optionsLine).append("\n\n");
                } else {
                    text.append("Ответь буквой a, b, c или d.\n\n");
                }
                text.append("Ответь буквой (a, b, c или d) или нажми «Показать ответ».");

                SendMessage msg = new SendMessage();
                msg.setChatId(chatId.toString());
                msg.setText(text.toString());

                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

                List<InlineKeyboardButton> row1 = new ArrayList<>();
                row1.add(button("✅ Показать ответ", "puzzle_show_answer"));
                keyboard.add(row1);

                List<InlineKeyboardButton> rowBack = new ArrayList<>();
                rowBack.add(button("◀️ Меню загадок", "puzzle_menu"));
                keyboard.add(rowBack);

                markup.setKeyboard(keyboard);
                msg.setReplyMarkup(markup);

                return msg;
            });
    }

    private ParsedPuzzle parsePuzzle(String raw) {
        ParsedPuzzle result = new ParsedPuzzle();
        if (raw == null || raw.isBlank()) {
            result.question = "Не удалось получить вопрос.";
            result.answer = "";
            result.explanation = "";
            result.options = "";
            return result;
        }

        String[] lines = raw.split("\\r?\\n");
        StringBuilder currentQuestion = new StringBuilder();
        StringBuilder currentAnswer = new StringBuilder();
        StringBuilder currentExplanation = new StringBuilder();
        StringBuilder currentOptions = new StringBuilder();

        String currentSection = "";

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("QUESTION:")) {
                currentSection = "Q";
                currentQuestion.append(trimmed.substring("QUESTION:".length()).trim());
            } else if (trimmed.startsWith("ANSWER:")) {
                currentSection = "A";
                currentAnswer.append(trimmed.substring("ANSWER:".length()).trim());
            } else if (trimmed.startsWith("EXPLANATION:")) {
                currentSection = "E";
                currentExplanation.append(trimmed.substring("EXPLANATION:".length()).trim());
            } else if (trimmed.startsWith("OPTIONS:")) {
                currentSection = "O";
                currentOptions.append(trimmed.substring("OPTIONS:".length()).trim());
            } else {
                switch (currentSection) {
                    case "Q" -> {
                        if (!currentQuestion.isEmpty()) {
                            currentQuestion.append("\n");
                        }
                        currentQuestion.append(trimmed);
                    }
                    case "A" -> {
                        if (!currentAnswer.isEmpty()) {
                            currentAnswer.append(" ");
                        }
                        currentAnswer.append(trimmed);
                    }
                    case "E" -> {
                        if (!currentExplanation.isEmpty()) {
                            currentExplanation.append("\n");
                        }
                        currentExplanation.append(trimmed);
                    }
                    case "O" -> {
                        if (!currentOptions.isEmpty()) {
                            currentOptions.append(" ");
                        }
                        currentOptions.append(trimmed);
                    }
                    default -> {
                    }
                }
            }
        }

        result.question = currentQuestion.isEmpty() ? raw : currentQuestion.toString();
        result.answer = currentAnswer.toString();
        result.explanation = currentExplanation.toString();
        result.options = currentOptions.toString();

        return result;
    }

    private static class ParsedPuzzle {
        String question;
        String answer;
        String explanation;
        String options;
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

