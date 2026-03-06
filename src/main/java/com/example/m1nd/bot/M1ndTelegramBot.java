package com.example.m1nd.bot;

import com.example.m1nd.config.TelegramBotConfig;
import com.example.m1nd.service.AdminService;
import com.example.m1nd.service.FeedbackService;
import com.example.m1nd.service.LLMService;
import com.example.m1nd.service.SummaryService;
import com.example.m1nd.service.UserService;
import com.example.m1nd.service.WorkingApiService;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;

import java.util.List;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class M1ndTelegramBot extends TelegramLongPollingBot {
    
    private static final Logger logger = LoggerFactory.getLogger(M1ndTelegramBot.class);
    
    private final TelegramBotConfig botConfig;
    private final UserService userService;
    private final LLMService llmService;
    private final WorkingApiService workingApiService;
    private final AdminService adminService;
    private final FeedbackService feedbackService;
    private final SummaryService summaryService;
    private final MainMenuService mainMenuService;
    private final AdminMenuService adminMenuService;
    
    @Value("${llm.api.use-llm-service:true}")
    private boolean useLlmService;
    
    @Value("${app.feedback.delay-minutes:10}")
    private int feedbackDelayMinutes;
    
    @Value("${app.summary.auto-create-enabled:true}")
    private boolean autoSummaryEnabled;
    
    @Value("${app.summary.delay-minutes:5}")
    private int summaryDelayMinutes;
    
    // Планировщик для отправки опросов
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);
    
    // Храним последний вопрос пользователя для опроса
    private final java.util.Map<Long, String> lastUserQuestion = new java.util.concurrent.ConcurrentHashMap<>();
    // Храним состояние ожидания ответа на опрос
    private final java.util.Map<Long, String> waitingForFeedback = new java.util.concurrent.ConcurrentHashMap<>();
    // Храним выбранный рейтинг до получения обязательного комментария
    private final java.util.Map<Long, Integer> pendingRatings = new java.util.concurrent.ConcurrentHashMap<>();
    // Храним запланированные задачи создания сводки для каждого пользователя
    private final java.util.Map<Long, ScheduledFuture<?>> scheduledSummaries = new java.util.concurrent.ConcurrentHashMap<>();
    
    @Override
    public String getBotUsername() {
        return botConfig.getUsername();
    }
    
    @Override
    public String getBotToken() {
        return botConfig.getToken();
    }
    
    @PostConstruct
    public void init() {
        String tokenPreview = botConfig.getToken() != null && botConfig.getToken().length() > 4
            ? "***" + botConfig.getToken().substring(botConfig.getToken().length() - 4)
            : "null";
        logger.info("Бот инициализирован. Username: {}, Token: {}", 
            botConfig.getUsername(), tokenPreview);
        logger.info("Задержка перед отправкой опроса: {} минут", feedbackDelayMinutes);
        logger.info("Автоматическое создание сводки: {} (задержка: {} минут)", 
            autoSummaryEnabled ? "включено" : "выключено", summaryDelayMinutes);
        
        // Вебхук теперь удаляется в TelegramBotConfiguration перед регистрацией бота
        logger.info("Бот готов к получению обновлений. Ожидаю команды /start...");
    }
    
    @PreDestroy
    public void destroy() {
        logger.info("Завершение работы бота, закрытие планировщика...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    @Override
    public void onUpdateReceived(Update update) {
        logger.info("Получено обновление: {}", update);
        
        // Обработка callback от кнопок
        if (update.hasCallbackQuery()) {
            handleCallbackQuery(update.getCallbackQuery());
            return;
        }
        
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText().trim();
            logger.info("Получено сообщение: '{}' от пользователя {}", 
                messageText, update.getMessage().getFrom().getId());
            
            // Нормализуем команду (убираем регистр и параметры)
            String normalizedText = messageText.toLowerCase();
            
            // Обработка команды /start (независимо от регистра и параметров)
            if (normalizedText.startsWith("/start")) {
                logger.info("Обработка команды /start");
                handleStartCommand(update);
            } else if (normalizedText.startsWith("/stats")) {
                // Обработка команды /stats (только для администраторов)
                logger.info("Обработка команды /stats");
                handleStatsCommand(update);
            } else if (normalizedText.startsWith("/summary")) {
                // Обработка команды /summary (для всех пользователей)
                logger.info("Обработка команды /summary");
                handleSummaryCommand(update);
            } else if (normalizedText.startsWith("/menu")
                    || "📋 Меню".equals(messageText)
                    || "меню".equalsIgnoreCase(messageText)) {
                logger.info("Обработка команды /menu");
                Long chatId = update.getMessage().getChatId();
                SendMessage menuMessage = mainMenuService.buildMainMenuMessage(chatId);
                try {
                    execute(menuMessage);
                } catch (TelegramApiException e) {
                    logger.error("Ошибка при отправке главного меню", e);
                }
            } else if (normalizedText.startsWith("/addadmin")) {
            // Обработка команды /addadmin (только для администраторов)
            logger.info("Обработка команды /addadmin");
            handleAddAdminCommand(update, messageText);
        } else {
            Long userId = update.getMessage().getFrom().getId();

            AdminMenuService.AdminTextResult adminTextResult =
                adminMenuService.handleAdminText(update, messageText);

            if (adminTextResult.isHandled()) {
                for (SendMessage msg : adminTextResult.getMessages()) {
                    try {
                        execute(msg);
                    } catch (TelegramApiException e) {
                        logger.error("Ошибка при отправке сообщения администратора", e);
                    }
                }
            } else {
                Long chatId = update.getMessage().getChatId();
                MainMenuService.PuzzleAnswerResult puzzleResult =
                    mainMenuService.handlePuzzleAnswer(chatId, userId, messageText);

                if (puzzleResult.isHandled()) {
                    for (SendMessage msg : puzzleResult.getMessages()) {
                        try {
                            execute(msg);
                        } catch (TelegramApiException e) {
                            logger.error("Ошибка при отправке ответа на загадку", e);
                        }
                    }
                } else if (waitingForFeedback.getOrDefault(userId, "").equals("comment")) {
                    // Обрабатываем комментарий к опросу
                    handleFeedbackComment(update, messageText);
                } else {
                    // Обработка обычных сообщений (вопросов)
                    logger.info("Обработка вопроса: {}", messageText);
                    handleQuestion(update, messageText);
                }
            }
        }
        } else {
            logger.warn("Обновление не содержит текстового сообщения: {}", update);
        }
    }
    
    private void handleStartCommand(Update update) {
        // Регистрируем пользователя (это также отслеживает активность)
        userService.registerUser(update);
        
        Long chatId = update.getMessage().getChatId();
        Long userId = update.getMessage().getFrom().getId();
        String username = update.getMessage().getFrom().getUserName();
        
        // Отслеживаем активность
        userService.trackUserActivity(userId);
        
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Привет! Это ♾\uFE0F пространство для тех, кто ищет ресурсы — знания, ответы, поддержку. Для роста, масштабирования и гармонии. Спрашивай, о чем угодно! Помогу с ответами.");
        
        // Если пользователь администратор - добавляем кнопки
        if (username != null) {
            boolean isAdmin = adminService.isAdmin(username);
            logger.info("Проверка админа для username '{}' (userId: {}): {}", username, userId, isAdmin);
            if (isAdmin) {
                message.setReplyMarkup(adminMenuService.createAdminKeyboard());
                logger.info("Кнопки администратора добавлены для {}", username);
            }
        } else {
            logger.warn("Username пользователя {} равен null", userId);
        }
        
        try {
            execute(message);
            
            // Отправляем главное меню в виде клавиатуры внизу экрана
            SendMessage menuMessage = mainMenuService.buildStartMainMenuMessage(chatId);
            execute(menuMessage);
        } catch (TelegramApiException e) {
            logger.error("Ошибка при отправке сообщения", e);
        }
    }
    
    private void handleQuestion(Update update, String messageText) {
        Long chatId = update.getMessage().getChatId();
        Long userId = update.getMessage().getFrom().getId();
        
        // Отслеживаем активность пользователя
        userService.trackUserActivity(userId);
        
        // Отменяем предыдущую запланированную сводку, если она есть
        cancelScheduledSummary(userId);
        
        // Отправляем сообщение о том, что обрабатываем запрос
        SendMessage processingMessage = new SendMessage();
        processingMessage.setChatId(chatId.toString());
        processingMessage.setText("Обрабатываю ваш вопрос...");
        
        try {
            execute(processingMessage);
        } catch (TelegramApiException e) {
            logger.error("Ошибка при отправке сообщения", e);
        }
        
        // Выбираем сервис в зависимости от настройки
        Mono<String> answerMono;
        if (useLlmService) {
            logger.info("Используется LLMService для пользователя {}", userId);
            answerMono = llmService.getAnswer(messageText, userId);
        } else {
            logger.info("Используется WorkingApiService для пользователя {}", userId);
            answerMono = workingApiService.getAnswer(messageText);
        }
        
        // Получаем ответ от выбранного сервиса
        String username = update.getMessage().getFrom().getUserName();
        boolean isAdmin = username != null && adminService.isAdmin(username);
        
        answerMono.subscribe(
            answer -> {
                logger.info("Получен ответ, длина: {} символов", answer.length());
                sendLongMessage(chatId, answer, isAdmin);
                userService.incrementQuestionsCount(userId);
                
                // Сохраняем вопрос для опроса
                lastUserQuestion.put(userId, messageText);
                
                // Планируем отправку опроса через N минут
                scheduler.schedule(() -> {
                    // Проверяем, что пользователь еще не ответил на опрос
                    if (lastUserQuestion.containsKey(userId) && 
                        !waitingForFeedback.containsKey(userId)) {
                        sendFeedbackRequest(chatId, userId);
                    }
                }, feedbackDelayMinutes, TimeUnit.MINUTES);
                
                // Планируем автоматическое создание сводки через N минут после последнего сообщения
                if (autoSummaryEnabled) {
                    scheduleAutoSummary(userId, username);
                }
                
                logger.info("Запланирован опрос для пользователя {} через {} минут", userId, feedbackDelayMinutes);
                logger.info("Ответ отправлен пользователю {}: {}", userId, answer.substring(0, Math.min(50, answer.length())));
            },
            error -> {
                logger.error("Ошибка при получении ответа", error);
                SendMessage errorMessage = new SendMessage();
                errorMessage.setChatId(chatId.toString());
                errorMessage.setText("Извините, произошла ошибка. Попробуйте позже.");
                
                try {
                    execute(errorMessage);
                } catch (TelegramApiException e) {
                    logger.error("Ошибка при отправке сообщения об ошибке", e);
                }
            }
        );
    }
    
    /**
     * Разбивает длинное сообщение на части и отправляет их последовательно.
     * Telegram ограничивает длину текстового сообщения 4096 символами.
     */
    private void sendLongMessage(Long chatId, String text) {
        sendLongMessage(chatId, text, false);
    }
    
    /**
     * Разбивает длинное сообщение на части и отправляет их последовательно.
     * @param isAdmin показывать ли кнопки администратора
     */
    private void sendLongMessage(Long chatId, String text, boolean isAdmin) {
        final int MAX_MESSAGE_LENGTH = 4096;
        
        if (text == null || text.isEmpty()) {
            logger.warn("Попытка отправить пустое сообщение");
            return;
        }
        
        logger.info("Обработка сообщения длиной {} символов", text.length());
        
        if (text.length() <= MAX_MESSAGE_LENGTH) {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(text);
            
            if (isAdmin) {
                message.setReplyMarkup(adminMenuService.createAdminKeyboard());
            }
            
            try {
                execute(message);
                logger.info("Сообщение отправлено целиком (длина: {})", text.length());
            } catch (TelegramApiException e) {
                logger.error("Ошибка при отправке сообщения", e);
                // Если даже короткое сообщение не отправилось, возможно оно все же слишком длинное
                // Попробуем разбить
                if (text.length() > 0) {
                    logger.info("Попытка разбить сообщение после ошибки");
                    splitAndSend(chatId, text, isAdmin);
                }
            }
        } else {
            // Разбиваем на части
            splitAndSend(chatId, text, isAdmin);
        }
    }
    
    /**
     * Разбивает текст на части и отправляет их последовательно
     */
    private void splitAndSend(Long chatId, String text) {
        splitAndSend(chatId, text, false);
    }
    
    /**
     * Разбивает текст на части и отправляет их последовательно
     * @param isAdmin показывать ли кнопки администратора на последнем сообщении
     */
    private void splitAndSend(Long chatId, String text, boolean isAdmin) {
        final int MAX_MESSAGE_LENGTH = 4096;
        final int SAFE_PREFIX_LENGTH = 30; // Запас для префикса "(XX/XX)\n\n"
        
        int offset = 0;
        int partNumber = 1;
        int maxPartLength = MAX_MESSAGE_LENGTH - SAFE_PREFIX_LENGTH;
        int totalParts = (int) Math.ceil((double) text.length() / maxPartLength);
        
        logger.info("Разбиваю сообщение на {} частей", totalParts);
        
        while (offset < text.length()) {
            int endIndex = Math.min(offset + maxPartLength, text.length());
            String part = text.substring(offset, endIndex);
            
            // Если это не последняя часть, пытаемся найти хорошее место для разрыва
            if (endIndex < text.length() && partNumber < totalParts) {
                // Ищем перенос строки в последних 300 символах
                int searchStart = Math.max(0, part.length() - 300);
                int lastNewline = part.lastIndexOf('\n', part.length() - 1);
                int lastDoubleNewline = part.lastIndexOf("\n\n", part.length() - 1);
                
                // Предпочитаем двойной перенос строки (конец абзаца)
                if (lastDoubleNewline >= searchStart) {
                    part = text.substring(offset, offset + lastDoubleNewline + 2);
                    endIndex = offset + lastDoubleNewline + 2;
                } else if (lastNewline >= searchStart) {
                    part = text.substring(offset, offset + lastNewline + 1);
                    endIndex = offset + lastNewline + 1;
                } else {
                    // Если переноса строки нет, ищем пробел
                    int lastSpace = part.lastIndexOf(' ', part.length() - 1);
                    if (lastSpace >= searchStart) {
                        part = text.substring(offset, offset + lastSpace);
                        endIndex = offset + lastSpace + 1; // +1 чтобы пропустить пробел
                    }
                }
            }
            
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            
            // Добавляем номер части
            String prefix = String.format("(%d/%d)\n\n", partNumber, totalParts);
            String messageText = prefix + part;
            
            // Финальная проверка длины
            if (messageText.length() > MAX_MESSAGE_LENGTH) {
                int availableLength = MAX_MESSAGE_LENGTH - prefix.length();
                if (availableLength > 0) {
                    part = part.substring(0, availableLength);
                    messageText = prefix + part;
                } else {
                    logger.error("Префикс слишком длинный! Пропускаю часть {}", partNumber);
                    offset = endIndex;
                    partNumber++;
                    continue;
                }
            }
            
            message.setText(messageText);
            
            if (isAdmin && partNumber == totalParts) {
                message.setReplyMarkup(adminMenuService.createAdminKeyboard());
            }
            
            try {
                execute(message);
                logger.info("✓ Отправлена часть {}/{} (длина текста: {}, общая длина: {})", 
                    partNumber, totalParts, part.length(), messageText.length());
                
                // Задержка между сообщениями
                if (partNumber < totalParts) {
                    Thread.sleep(150);
                }
            } catch (TelegramApiException e) {
                logger.error("✗ Ошибка при отправке части {}/{}: {}", partNumber, totalParts, e.getMessage());
                // Продолжаем отправку следующих частей
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Прервана отправка сообщения", e);
                break;
            }
            
            offset = endIndex;
            partNumber++;
        }
        
        logger.info("Завершена отправка всех частей сообщения");
    }
    
    /**
     * Обрабатывает команду /stats (только для администраторов)
     */
    private void handleStatsCommand(Update update) {
        String username = update.getMessage().getFrom().getUserName();
        Long userId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();
        
        if (username == null || !adminService.isAdmin(username)) {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("❌ У вас нет доступа к этой команде.");
            
            try {
                execute(message);
            } catch (TelegramApiException e) {
                logger.error("Ошибка при отправке сообщения", e);
            }
            return;
        }
        
        userService.trackUserActivity(userId);
        
        for (SendMessage msg : adminMenuService.buildStatisticsMessages(chatId)) {
            try {
                execute(msg);
            } catch (TelegramApiException e) {
                logger.error("Ошибка при отправке статистики", e);
            }
        }
    }
    
    /**
     * Обрабатывает callback от кнопок
     */
    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        Long userId = callbackQuery.getFrom().getId();
        String username = callbackQuery.getFrom().getUserName();
        Long chatId = callbackQuery.getMessage().getChatId();
        
        logger.info("Обработка callback: {} от пользователя {}", data, username);
        
        // Обработка опросов (доступны всем пользователям)
        if (data != null && data.startsWith("feedback_")) {
            handleFeedbackCallback(callbackQuery, data);
            return;
        }
        
        // Обработка главного меню и фактов (доступно всем пользователям)
        if (mainMenuService.canHandleCallback(data)) {
            mainMenuService.handleCallback(callbackQuery)
                .subscribe(
                    result -> {
                        for (SendMessage msg : result.getMessages()) {
                            try {
                                execute(msg);
                            } catch (TelegramApiException e) {
                                logger.error("Ошибка при отправке сообщения главного меню", e);
                            }
                        }
                        if (result.getCallbackAnswer() != null) {
                            sendCallbackAnswer(callbackQuery.getId(), result.getCallbackAnswer());
                        }
                    },
                    error -> {
                        logger.error("Ошибка при обработке главного меню", error);
                        sendCallbackAnswer(callbackQuery.getId(), "❌ Ошибка");
                    }
                );
            return;
        }
        
        if (adminMenuService.canHandleCallback(data)) {
            AdminMenuService.AdminMenuResult result = adminMenuService.handleCallback(callbackQuery);

            for (SendMessage msg : result.getMessages()) {
                try {
                    execute(msg);
                } catch (TelegramApiException e) {
                    logger.error("Ошибка при отправке сообщения админ-меню", e);
                }
            }

            if (result.getCallbackAnswer() != null) {
                sendCallbackAnswer(callbackQuery.getId(), result.getCallbackAnswer());
            }
        } else {
            sendCallbackAnswer(callbackQuery.getId(), "❌ Неизвестная команда");
        }
    }
    
    /**
     * Обрабатывает команду /addadmin
     */
    private void handleAddAdminCommand(Update update, String messageText) {
        String username = update.getMessage().getFrom().getUserName();
        Long chatId = update.getMessage().getChatId();
        
        // Проверяем, является ли пользователь администратором
        if (username == null || !adminService.isAdmin(username)) {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("❌ У вас нет доступа к этой команде.");
            
            try {
                execute(message);
            } catch (TelegramApiException e) {
                logger.error("Ошибка при отправке сообщения", e);
            }
            return;
        }
        
        // Извлекаем username из команды
        String[] parts = messageText.split("\\s+", 2);
        if (parts.length < 2) {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("📝 Использование: /addadmin @username\n\nПример: /addadmin @puh2012");
            
            try {
                execute(message);
            } catch (TelegramApiException e) {
                logger.error("Ошибка при отправке сообщения", e);
            }
            return;
        }
        
        String targetUsername = parts[1].trim();
        boolean added = adminService.addAdmin(targetUsername, username);
        
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        
        if (added) {
            message.setText("✅ Администратор @" + targetUsername.replace("@", "") + " успешно добавлен!");
        } else {
            message.setText("❌ Не удалось добавить администратора. Возможно, он уже является администратором.");
        }
        
        try {
            execute(message);
        } catch (TelegramApiException e) {
            logger.error("Ошибка при отправке сообщения", e);
        }
    }
    
    /**
     * Обрабатывает username для добавления админа (после нажатия кнопки)
     */
    private void handleAddAdminUsername(Update update, String messageText) {
        String username = update.getMessage().getFrom().getUserName();
        Long chatId = update.getMessage().getChatId();
        
        // Извлекаем username из сообщения
        String targetUsername = messageText.trim();
        
        boolean added = adminService.addAdmin(targetUsername, username);
        
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        
        if (added) {
            message.setText("✅ Администратор @" + targetUsername.replace("@", "") + " успешно добавлен!");
        } else {
            message.setText("❌ Не удалось добавить администратора. Возможно, он уже является администратором.");
        }
        
        // Добавляем кнопки админа
        message.setReplyMarkup(adminMenuService.createAdminKeyboard());
        
        try {
            execute(message);
        } catch (TelegramApiException e) {
            logger.error("Ошибка при отправке сообщения", e);
        }
    }
    
    /**
     * Отправляет запрос на опрос пользователю
     */
    private void sendFeedbackRequest(Long chatId, Long userId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("💬 Оцени, пожалуйста, ответ по шкале от 1 до 10.\n\n" +
            "10 — всё идеально и максимально полезно.\n" +
            "Если это не 10, напиши потом короткий комментарий, что можно улучшить.");
        
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // Кнопки с рейтингом 1–5
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(String.valueOf(i));
            button.setCallbackData("feedback_rating_" + i);
            row1.add(button);
        }
        
        // Кнопки с рейтингом 6–10
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        for (int i = 6; i <= 10; i++) {
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(String.valueOf(i));
            button.setCallbackData("feedback_rating_" + i);
            row2.add(button);
        }
        
        keyboard.add(row1);
        keyboard.add(row2);
        markup.setKeyboard(keyboard);
        message.setReplyMarkup(markup);
        
        try {
            execute(message);
            waitingForFeedback.put(userId, "waiting");  // Устанавливаем флаг ожидания
            logger.info("Опрос отправлен пользователю {}", userId);
        } catch (TelegramApiException e) {
            logger.error("Ошибка при отправке опроса", e);
        }
    }
    
    /**
     * Обрабатывает callback от кнопок опроса
     */
    private void handleFeedbackCallback(CallbackQuery callbackQuery, String data) {
        Long userId = callbackQuery.getFrom().getId();
        String username = callbackQuery.getFrom().getUserName();
        String firstName = callbackQuery.getFrom().getFirstName();
        Long chatId = callbackQuery.getMessage().getChatId();
        
        String question = lastUserQuestion.getOrDefault(userId, "Неизвестный вопрос");
        
        if (data != null && data.startsWith("feedback_rating_")) {
            int rating;
            try {
                rating = Integer.parseInt(data.substring("feedback_rating_".length()));
            } catch (NumberFormatException e) {
                logger.error("Некорректное значение рейтинга в callback: {}", data, e);
                sendCallbackAnswer(callbackQuery.getId(), "❌ Некорректная оценка");
                return;
            }
            
            if (rating == 10) {
                // Для 10/10 комментарий не обязателен
                feedbackService.saveFeedback(userId, username, firstName, rating, null, null, question);
                sendCallbackAnswer(callbackQuery.getId(), "✅ Спасибо за оценку 10/10!");
                
                SendMessage message = new SendMessage();
                message.setChatId(chatId.toString());
                message.setText("Спасибо за отличную оценку! 🙏");
                try {
                    execute(message);
                } catch (TelegramApiException e) {
                    logger.error("Ошибка при отправке сообщения", e);
                }
                
                waitingForFeedback.remove(userId);
                pendingRatings.remove(userId);
                lastUserQuestion.remove(userId);
            } else {
                // Для любой оценки ниже 10 требуем комментарий
                pendingRatings.put(userId, rating);
                waitingForFeedback.put(userId, "comment");
                
                sendCallbackAnswer(callbackQuery.getId(), "✅ Оценка " + rating + "/10 получена");
                
                SendMessage message = new SendMessage();
                message.setChatId(chatId.toString());
                message.setText("Спасибо за оценку " + rating + "/10.\n" +
                    "Пожалуйста, напишите короткий комментарий: что можно улучшить, чтобы было 10/10?");
                try {
                    execute(message);
                } catch (TelegramApiException e) {
                    logger.error("Ошибка при отправке сообщения", e);
                }
            }
        }
    }
    
    /**
     * Обрабатывает комментарий к опросу
     */
    private void handleFeedbackComment(Update update, String comment) {
        Long userId = update.getMessage().getFrom().getId();
        String username = update.getMessage().getFrom().getUserName();
        String firstName = update.getMessage().getFrom().getFirstName();
        String question = lastUserQuestion.getOrDefault(userId, "Неизвестный вопрос");
        Integer rating = pendingRatings.getOrDefault(userId, null);
        
        feedbackService.saveFeedback(userId, username, firstName, rating, null, comment, question);
        
        SendMessage message = new SendMessage();
        message.setChatId(update.getMessage().getChatId().toString());
        message.setText("✅ Спасибо за комментарий!");
        
        try {
            execute(message);
        } catch (TelegramApiException e) {
            logger.error("Ошибка при отправке сообщения", e);
        }
        
        waitingForFeedback.remove(userId);
        pendingRatings.remove(userId);
        lastUserQuestion.remove(userId);
    }

    /**
     * Отправляет напоминание пользователю
     */
    public void sendReminderMessage(Long userId, String message) {
        try {
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(userId.toString());
            sendMessage.setText(message);
            
            execute(sendMessage);
            logger.info("Напоминание отправлено пользователю {}", userId);
        } catch (TelegramApiException e) {
            // Если пользователь заблокировал бота, это нормально
            if (e.getMessage() != null && e.getMessage().contains("blocked")) {
                logger.debug("Пользователь {} заблокировал бота", userId);
            } else {
                logger.error("Ошибка при отправке напоминания пользователю {}", userId, e);
            }
        }
    }
    
    /**
     * Отправляет ответ на callback query
     */
    private void sendCallbackAnswer(String callbackQueryId, String text) {
        try {
            AnswerCallbackQuery answer = new AnswerCallbackQuery();
            answer.setCallbackQueryId(callbackQueryId);
            answer.setText(text);
            answer.setShowAlert(false);
            execute(answer);
        } catch (TelegramApiException e) {
            logger.error("Ошибка при отправке ответа на callback", e);
        }
    }
    
    /**
     * Обрабатывает команду /summary - создаёт сводку диалога
     */
    private void handleSummaryCommand(Update update) {
        Long chatId = update.getMessage().getChatId();
        Long userId = update.getMessage().getFrom().getId();
        String username = update.getMessage().getFrom().getUserName();
        
        // Отслеживаем активность
        userService.trackUserActivity(userId);
        
        // Отменяем автоматическую задачу, если она запланирована
        cancelScheduledSummary(userId);
        
        SendMessage processingMessage = new SendMessage();
        processingMessage.setChatId(chatId.toString());
        processingMessage.setText("⏳ Создаю сводку диалога...");
        
        try {
            execute(processingMessage);
        } catch (TelegramApiException e) {
            logger.error("Ошибка при отправке сообщения", e);
        }
        
        // Создаём сводку
        summaryService.createAndSaveSummary(userId, username)
            .subscribe(
                result -> {
                    SendMessage resultMessage = new SendMessage();
                    resultMessage.setChatId(chatId.toString());
                    resultMessage.setText(result);
                    
                    try {
                        execute(resultMessage);
                    } catch (TelegramApiException e) {
                        logger.error("Ошибка при отправке результата сводки", e);
                    }
                },
                error -> {
                    logger.error("Ошибка при создании сводки", error);
                    SendMessage errorMessage = new SendMessage();
                    errorMessage.setChatId(chatId.toString());
                    errorMessage.setText("❌ Ошибка при создании сводки: " + error.getMessage());
                    
                    try {
                        execute(errorMessage);
                    } catch (TelegramApiException e) {
                        logger.error("Ошибка при отправке сообщения об ошибке", e);
                    }
                }
            );
    }
    
    /**
     * Планирует автоматическое создание сводки через N минут после последнего сообщения
     */
    private void scheduleAutoSummary(Long userId, String username) {
        // Отменяем предыдущую задачу, если она есть
        cancelScheduledSummary(userId);
        
        // Планируем новую задачу
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            try {
                logger.info("Автоматически создаю сводку для пользователя {} через {} минут после последнего сообщения", 
                    userId, summaryDelayMinutes);
                createSummarySilently(userId, username);
            } catch (Exception e) {
                logger.error("Ошибка при автоматическом создании сводки для пользователя {}", userId, e);
            } finally {
                // Удаляем задачу из Map после выполнения
                scheduledSummaries.remove(userId);
            }
        }, summaryDelayMinutes, TimeUnit.MINUTES);
        
        scheduledSummaries.put(userId, future);
        logger.info("Запланировано автоматическое создание сводки для пользователя {} через {} минут", 
            userId, summaryDelayMinutes);
    }
    
    /**
     * Отменяет запланированное создание сводки для пользователя
     */
    private void cancelScheduledSummary(Long userId) {
        ScheduledFuture<?> future = scheduledSummaries.remove(userId);
        if (future != null && !future.isDone()) {
            future.cancel(false);
            logger.debug("Отменено запланированное создание сводки для пользователя {}", userId);
        }
    }
    
    /**
     * Создает сводку без отправки сообщений пользователю (тихий режим)
     */
    private void createSummarySilently(Long userId, String username) {
        // Проверяем, есть ли история диалога через SummaryService
        // SummaryService использует ConversationService внутри, так что проверка будет там
        logger.info("Начинаем автоматическое создание сводки для пользователя {} (без уведомления)", userId);
        
        // Создаём сводку без отправки сообщений пользователю
        summaryService.createAndSaveSummary(userId, username)
            .subscribe(
                result -> {
                    logger.info("Автоматическая сводка успешно создана для пользователя {}: {}", userId, result);
                },
                error -> {
                    logger.error("Ошибка при автоматическом создании сводки для пользователя {}", userId, error);
                }
            );
    }
}

