package com.example.m1nd.bot;

import com.example.m1nd.config.TelegramBotConfig;
import com.example.m1nd.service.AdminService;
import com.example.m1nd.service.FeedbackService;
import com.example.m1nd.service.LLMService;
import com.example.m1nd.service.StatisticsService;
import com.example.m1nd.service.SummaryService;
import com.example.m1nd.service.UserService;
import com.example.m1nd.service.WorkingApiService;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
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
    private final StatisticsService statisticsService;
    private final AdminService adminService;
    private final FeedbackService feedbackService;
    private final SummaryService summaryService;
    
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
    
    // Храним состояние ожидания username для добавления админа
    private final java.util.Map<Long, Boolean> waitingForAdminUsername = new java.util.concurrent.ConcurrentHashMap<>();
    // Храним состояние ожидания username для удаления админа
    private final java.util.Map<Long, Boolean> waitingForRemoveAdminUsername = new java.util.concurrent.ConcurrentHashMap<>();
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
        } else if (normalizedText.startsWith("/addadmin")) {
            // Обработка команды /addadmin (только для администраторов)
            logger.info("Обработка команды /addadmin");
            handleAddAdminCommand(update, messageText);
        } else {
            // Проверяем, ожидаем ли мы username для добавления/удаления админа
            Long userId = update.getMessage().getFrom().getId();
            String username = update.getMessage().getFrom().getUserName();
            
            if (waitingForAdminUsername.getOrDefault(userId, false) && 
                username != null && adminService.isAdmin(username)) {
                // Обрабатываем username для добавления админа
                handleAddAdminUsername(update, messageText);
                waitingForAdminUsername.remove(userId);
            } else if (waitingForRemoveAdminUsername.getOrDefault(userId, false) && 
                username != null && adminService.isAdmin(username)) {
                // Обрабатываем username для удаления админа
                handleRemoveAdminUsername(update, messageText);
                waitingForRemoveAdminUsername.remove(userId);
            } else if (waitingForFeedback.getOrDefault(userId, "").equals("comment")) {
                // Обрабатываем комментарий к опросу
                handleFeedbackComment(update, messageText);
            } else {
                // Обработка обычных сообщений (вопросов)
                logger.info("Обработка вопроса: {}", messageText);
                handleQuestion(update, messageText);
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
                message.setReplyMarkup(createAdminKeyboard());
                logger.info("Кнопки администратора добавлены для {}", username);
            }
        } else {
            logger.warn("Username пользователя {} равен null", userId);
        }
        
        try {
            execute(message);
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
            // Если сообщение короткое, отправляем как есть
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(text);
            
            // Добавляем кнопки админа на последнее сообщение
            if (isAdmin) {
                message.setReplyMarkup(createAdminKeyboard());
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
            
            // Добавляем кнопки админа только на последнее сообщение
            if (isAdmin && partNumber == totalParts) {
                message.setReplyMarkup(createAdminKeyboard());
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
        
        // Отслеживаем активность
        userService.trackUserActivity(userId);
        
        // Получаем статистику и отправляем в code block
        sendStatistics(chatId);
    }
    
    /**
     * Отправляет статистику в code block (разбивает на части если нужно)
     */
    private void sendStatistics(Long chatId) {
        String statistics = statisticsService.formatStatistics();
        
        // Разбиваем на части по 4000 символов (с запасом для code block)
        final int MAX_LENGTH = 4000;
        
        if (statistics.length() <= MAX_LENGTH) {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("```\n" + statistics + "\n```");
            message.setParseMode("Markdown");
            
            // Добавляем кнопки для админов
            message.setReplyMarkup(createAdminKeyboard());
            
            try {
                execute(message);
                logger.info("Статистика отправлена администратору");
            } catch (TelegramApiException e) {
                logger.error("Ошибка при отправке статистики", e);
            }
        } else {
            // Разбиваем на части
            int partNumber = 1;
            int offset = 0;
            
            while (offset < statistics.length()) {
                int endIndex = Math.min(offset + MAX_LENGTH, statistics.length());
                String part = statistics.substring(offset, endIndex);
                
                SendMessage message = new SendMessage();
                message.setChatId(chatId.toString());
                message.setText("```\n" + part + "\n```");
                message.setParseMode("Markdown");
                
                // Кнопки только на последнем сообщении
                if (endIndex >= statistics.length()) {
                    message.setReplyMarkup(createAdminKeyboard());
                }
                
                try {
                    execute(message);
                    logger.info("Отправлена часть статистики {}/{}", partNumber, 
                        (int) Math.ceil((double) statistics.length() / MAX_LENGTH));
                    
                    if (endIndex < statistics.length()) {
                        Thread.sleep(200); // Задержка между сообщениями
                    }
                } catch (TelegramApiException e) {
                    logger.error("Ошибка при отправке части статистики", e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("Прервана отправка статистики", e);
                    break;
                }
                
                offset = endIndex;
                partNumber++;
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
        
        // Проверяем, является ли пользователь администратором
        if (username == null || !adminService.isAdmin(username)) {
            sendCallbackAnswer(callbackQuery.getId(), "❌ У вас нет доступа к этой функции.");
            return;
        }
        
        if ("stats".equals(data)) {
            // Отслеживаем активность
            userService.trackUserActivity(userId);
            // Отправляем статистику
            sendStatistics(chatId);
            sendCallbackAnswer(callbackQuery.getId(), "✅ Статистика отправлена");
        } else if ("admin_menu".equals(data)) {
            // Показываем меню администратора
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("👤 Меню администратора\n\nВыберите действие:");
            message.setReplyMarkup(createAdminMenuKeyboard());
            
            try {
                execute(message);
                sendCallbackAnswer(callbackQuery.getId(), "✅ Меню открыто");
            } catch (TelegramApiException e) {
                logger.error("Ошибка при отправке меню", e);
                sendCallbackAnswer(callbackQuery.getId(), "❌ Ошибка");
            }
        } else if ("back_to_main".equals(data)) {
            // Возврат к главному меню
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("Главное меню");
            message.setReplyMarkup(createAdminKeyboard());
            
            try {
                execute(message);
                sendCallbackAnswer(callbackQuery.getId(), "✅");
            } catch (TelegramApiException e) {
                logger.error("Ошибка при отправке сообщения", e);
                sendCallbackAnswer(callbackQuery.getId(), "❌ Ошибка");
            }
        } else if ("add_admin_prompt".equals(data)) {
            // Запрос на добавление админа - устанавливаем флаг ожидания
            waitingForAdminUsername.put(userId, true);
            
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("📝 Отправьте username пользователя, которого хотите добавить как администратора.\n\n" +
                "Формат: @username или просто username\n\n" +
                "Пример: @puh2012 или puh2012");
            message.setReplyMarkup(createAdminMenuKeyboard());
            
            try {
                execute(message);
                sendCallbackAnswer(callbackQuery.getId(), "✅ Введите username");
            } catch (TelegramApiException e) {
                logger.error("Ошибка при отправке сообщения", e);
                sendCallbackAnswer(callbackQuery.getId(), "❌ Ошибка");
                waitingForAdminUsername.remove(userId);
            }
        } else if ("list_admins".equals(data)) {
            // Показываем список админов
            handleListAdmins(chatId);
            sendCallbackAnswer(callbackQuery.getId(), "✅ Список отправлен");
        } else if ("view_feedbacks".equals(data)) {
            // Показываем опросы
            handleViewFeedbacks(chatId);
            sendCallbackAnswer(callbackQuery.getId(), "✅ Опросы отправлены");
        } else if ("admin_activity".equals(data)) {
            // Показываем выбор даты для просмотра активности
            handleActivityDateSelection(chatId);
            sendCallbackAnswer(callbackQuery.getId(), "✅ Выберите дату");
        } else if (data != null && data.startsWith("activity_date:")) {
            // Обработка выбора даты
            String dateStr = data.substring("activity_date:".length());
            handleActivityDateSelected(chatId, dateStr);
            sendCallbackAnswer(callbackQuery.getId(), "✅");
        } else if (data != null && data.startsWith("activity_user:")) {
            // Обработка выбора пользователя
            String[] parts = data.substring("activity_user:".length()).split(":");
            if (parts.length == 2) {
                String dateStr = parts[0];
                Long targetUserId = Long.parseLong(parts[1]);
                handleActivityUserSelected(chatId, dateStr, targetUserId);
                sendCallbackAnswer(callbackQuery.getId(), "✅");
            }
        } else if ("remove_admin_prompt".equals(data)) {
            // Запрос на удаление админа - устанавливаем флаг ожидания
            waitingForRemoveAdminUsername.put(userId, true);
            
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("📝 Отправьте username администратора, которого хотите удалить.\n\n" +
                "Формат: @username или просто username\n\n" +
                "Пример: @puh2012 или puh2012");
            message.setReplyMarkup(createAdminMenuKeyboard());
            
            try {
                execute(message);
                sendCallbackAnswer(callbackQuery.getId(), "✅ Введите username");
            } catch (TelegramApiException e) {
                logger.error("Ошибка при отправке сообщения", e);
                sendCallbackAnswer(callbackQuery.getId(), "❌ Ошибка");
                waitingForRemoveAdminUsername.remove(userId);
            }
        } else if (data != null && data.startsWith("add_admin:")) {
            // Обработка добавления админа (старый формат, оставлен для совместимости)
            String targetUsername = data.substring("add_admin:".length());
            handleAddAdminCallback(callbackQuery, targetUsername);
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
        message.setReplyMarkup(createAdminKeyboard());
        
        try {
            execute(message);
        } catch (TelegramApiException e) {
            logger.error("Ошибка при отправке сообщения", e);
        }
    }
    
    /**
     * Показывает список всех администраторов
     */
    private void handleListAdmins(Long chatId) {
        List<com.example.m1nd.model.Admin> admins = adminService.getAllAdmins();
        
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        
        if (admins.isEmpty()) {
            message.setText("📋 Список администраторов пуст.");
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("📋 Список администраторов (").append(admins.size()).append("):\n\n");
            
            for (int i = 0; i < admins.size(); i++) {
                com.example.m1nd.model.Admin admin = admins.get(i);
                sb.append(i + 1).append(". ").append(admin.getUsername());
                if (admin.getAddedAt() != null) {
                    sb.append("\n   Добавлен: ").append(admin.getAddedAt().toLocalDate());
                }
                if (admin.getAddedBy() != null && !admin.getAddedBy().equals("system")) {
                    sb.append("\n   Добавил: ").append(admin.getAddedBy());
                }
                sb.append("\n\n");
            }
            
            message.setText(sb.toString());
        }
        
        message.setReplyMarkup(createAdminMenuKeyboard());
        
        try {
            execute(message);
            logger.info("Список администраторов отправлен");
        } catch (TelegramApiException e) {
            logger.error("Ошибка при отправке списка администраторов", e);
        }
    }
    
    /**
     * Обрабатывает username для удаления админа
     */
    private void handleRemoveAdminUsername(Update update, String messageText) {
        String username = update.getMessage().getFrom().getUserName();
        Long chatId = update.getMessage().getChatId();
        
        // Извлекаем username из сообщения
        String targetUsername = messageText.trim();
        
        // Проверяем, не пытается ли пользователь удалить самого себя
        String cleanTargetUsername = targetUsername.startsWith("@") ? targetUsername.substring(1) : targetUsername;
        String cleanCurrentUsername = username != null && username.startsWith("@") ? username.substring(1) : username;
        
        if (cleanTargetUsername.equalsIgnoreCase(cleanCurrentUsername)) {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("❌ Вы не можете удалить самого себя из администраторов.");
            message.setReplyMarkup(createAdminMenuKeyboard());
            
            try {
                execute(message);
            } catch (TelegramApiException e) {
                logger.error("Ошибка при отправке сообщения", e);
            }
            return;
        }
        
        boolean removed = adminService.removeAdmin(targetUsername);
        
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        
        if (removed) {
            message.setText("✅ Администратор @" + targetUsername.replace("@", "") + " успешно удален!");
        } else {
            message.setText("❌ Не удалось удалить администратора. Возможно, он не найден в списке.");
        }
        
        // Добавляем кнопки админа
        message.setReplyMarkup(createAdminKeyboard());
        
        try {
            execute(message);
        } catch (TelegramApiException e) {
            logger.error("Ошибка при отправке сообщения", e);
        }
    }
    
    /**
     * Обрабатывает callback для добавления админа (старый метод, оставлен для совместимости)
     */
    private void handleAddAdminCallback(CallbackQuery callbackQuery, String targetUsername) {
        String username = callbackQuery.getFrom().getUserName();
        Long chatId = callbackQuery.getMessage().getChatId();
        
        boolean added = adminService.addAdmin(targetUsername, username);
        
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        
        if (added) {
            message.setText("✅ Администратор @" + targetUsername.replace("@", "") + " успешно добавлен!");
            sendCallbackAnswer(callbackQuery.getId(), "✅ Администратор добавлен");
        } else {
            message.setText("❌ Не удалось добавить администратора. Возможно, он уже является администратором.");
            sendCallbackAnswer(callbackQuery.getId(), "❌ Ошибка добавления");
        }
        
        try {
            execute(message);
        } catch (TelegramApiException e) {
            logger.error("Ошибка при отправке сообщения", e);
        }
    }
    
    /**
     * Создает клавиатуру с кнопками для администраторов
     */
    private InlineKeyboardMarkup createAdminKeyboard() {
        logger.debug("Создание клавиатуры для администратора");
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        
        InlineKeyboardButton statsButton = new InlineKeyboardButton();
        statsButton.setText("📊 Статистика");
        statsButton.setCallbackData("stats");
        
        InlineKeyboardButton adminButton = new InlineKeyboardButton();
        adminButton.setText("👤 Админ");
        adminButton.setCallbackData("admin_menu");
        
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(statsButton);
        row.add(adminButton);
        
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        keyboard.add(row);
        
        markup.setKeyboard(keyboard);
        logger.debug("Клавиатура создана с {} кнопками", row.size());
        return markup;
    }
    
    /**
     * Создает меню администратора
     */
    private InlineKeyboardMarkup createAdminMenuKeyboard() {
        logger.debug("Создание меню администратора");
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        
        InlineKeyboardButton addAdminButton = new InlineKeyboardButton();
        addAdminButton.setText("➕ Добавить админа");
        addAdminButton.setCallbackData("add_admin_prompt");
        
        InlineKeyboardButton listAdminsButton = new InlineKeyboardButton();
        listAdminsButton.setText("📋 Список админов");
        listAdminsButton.setCallbackData("list_admins");
        
        InlineKeyboardButton removeAdminButton = new InlineKeyboardButton();
        removeAdminButton.setText("➖ Удалить админа");
        removeAdminButton.setCallbackData("remove_admin_prompt");
        
        InlineKeyboardButton feedbacksButton = new InlineKeyboardButton();
        feedbacksButton.setText("📝 Опросы");
        feedbacksButton.setCallbackData("view_feedbacks");
        
        InlineKeyboardButton activityButton = new InlineKeyboardButton();
        activityButton.setText("📈 Активность");
        activityButton.setCallbackData("admin_activity");
        
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("◀️ Назад");
        backButton.setCallbackData("back_to_main");
        
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(addAdminButton);
        
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(listAdminsButton);
        
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        row3.add(removeAdminButton);
        
        List<InlineKeyboardButton> row4 = new ArrayList<>();
        row4.add(feedbacksButton);
        
        List<InlineKeyboardButton> row5 = new ArrayList<>();
        row5.add(activityButton);
        
        List<InlineKeyboardButton> row6 = new ArrayList<>();
        row6.add(backButton);
        
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        keyboard.add(row1);
        keyboard.add(row2);
        keyboard.add(row3);
        keyboard.add(row4);
        keyboard.add(row5);
        keyboard.add(row6);
        
        markup.setKeyboard(keyboard);
        logger.debug("Меню администратора создано");
        return markup;
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
     * Показывает опросы администратору
     */
    private void handleViewFeedbacks(Long chatId) {
        List<com.example.m1nd.model.Feedback> feedbacks = feedbackService.getRecentFeedbacks(30);  // За последние 30 дней
        
        if (feedbacks.isEmpty()) {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("📝 Опросов пока нет.");
            message.setReplyMarkup(createAdminMenuKeyboard());
            try {
                execute(message);
            } catch (TelegramApiException e) {
                logger.error("Ошибка при отправке сообщения", e);
            }
            return;
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("📝 Опросы пользователей (последние 30 дней):\n\n");
        
        for (com.example.m1nd.model.Feedback feedback : feedbacks) {
            sb.append("👤 ").append(feedback.getUsername() != null ? feedback.getUsername() : feedback.getFirstName())
              .append(" (").append(feedback.getUserId()).append(")\n");
            
            if (feedback.getRating() != null) {
                sb.append("⭐ Оценка: ").append(feedback.getRating()).append("/10\n");
            }
            
            if (feedback.getWasUseful() != null) {
                sb.append("💡 Полезно: ").append(feedback.getWasUseful() ? "Да" : "Нет").append("\n");
            }
            
            if (feedback.getComment() != null && !feedback.getComment().isEmpty()) {
                sb.append("💭 Комментарий: ").append(feedback.getComment()).append("\n");
            }
            
            if (feedback.getQuestion() != null && !feedback.getQuestion().isEmpty()) {
                String questionPreview = feedback.getQuestion().length() > 50 
                    ? feedback.getQuestion().substring(0, 50) + "..." 
                    : feedback.getQuestion();
                sb.append("❓ Вопрос: ").append(questionPreview).append("\n");
            }
            
            sb.append("📅 ").append(feedback.getCreatedAt().toLocalDate()).append("\n\n");
        }
        
        // Разбиваем на части если длинно
        sendLongMessage(chatId, sb.toString(), true);
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
     * Показывает выбор даты для просмотра активности
     */
    private void handleActivityDateSelection(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("📅 Выберите дату для просмотра активности:");
        
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // Предлагаем последние 7 дней
        java.time.LocalDate today = java.time.LocalDate.now();
        for (int i = 0; i < 7; i++) {
            java.time.LocalDate date = today.minusDays(i);
            String dateStr = date.toString();
            String displayText = i == 0 ? "Сегодня" : 
                               i == 1 ? "Вчера" : 
                               date.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM"));
            
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(displayText);
            button.setCallbackData("activity_date:" + dateStr);
            
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(button);
            keyboard.add(row);
        }
        
        // Кнопка "Назад"
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("◀️ Назад");
        backButton.setCallbackData("admin_menu");
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        backRow.add(backButton);
        keyboard.add(backRow);
        
        markup.setKeyboard(keyboard);
        message.setReplyMarkup(markup);
        
        try {
            execute(message);
        } catch (TelegramApiException e) {
            logger.error("Ошибка при отправке выбора даты", e);
        }
    }
    
    /**
     * Обрабатывает выбранную дату и показывает список активных пользователей
     */
    private void handleActivityDateSelected(Long chatId, String dateStr) {
        try {
            java.time.LocalDate date = java.time.LocalDate.parse(dateStr);
            List<com.example.m1nd.service.SummaryService.UserActivityInfo> users = 
                summaryService.getActiveUsersByDate(date);
            
            if (users.isEmpty()) {
                SendMessage message = new SendMessage();
                message.setChatId(chatId.toString());
                message.setText("❌ На выбранную дату (" + dateStr + ") нет активности.");
                message.setReplyMarkup(createAdminMenuKeyboard());
                
                try {
                    execute(message);
                } catch (TelegramApiException e) {
                    logger.error("Ошибка при отправке сообщения", e);
                }
                return;
            }
            
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("👥 Активные пользователи на " + dateStr + ":\n\nВыберите пользователя:");
            
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            
            for (com.example.m1nd.service.SummaryService.UserActivityInfo user : users) {
                InlineKeyboardButton button = new InlineKeyboardButton();
                String displayName = user.username != null ? "@" + user.username : "ID: " + user.userId;
                button.setText(displayName);
                button.setCallbackData("activity_user:" + dateStr + ":" + user.userId);
                
                List<InlineKeyboardButton> row = new ArrayList<>();
                row.add(button);
                keyboard.add(row);
            }
            
            // Кнопка "Назад"
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("◀️ Назад");
            backButton.setCallbackData("admin_activity");
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            backRow.add(backButton);
            keyboard.add(backRow);
            
            markup.setKeyboard(keyboard);
            message.setReplyMarkup(markup);
            
            try {
                execute(message);
            } catch (TelegramApiException e) {
                logger.error("Ошибка при отправке списка пользователей", e);
            }
        } catch (Exception e) {
            logger.error("Ошибка при обработке выбранной даты", e);
            SendMessage errorMessage = new SendMessage();
            errorMessage.setChatId(chatId.toString());
            errorMessage.setText("❌ Ошибка: неверный формат даты. Используйте ГГГГ-ММ-ДД");
            
            try {
                execute(errorMessage);
            } catch (TelegramApiException ex) {
                logger.error("Ошибка при отправке сообщения об ошибке", ex);
            }
        }
    }
    
    /**
     * Показывает сводки активности выбранного пользователя
     */
    private void handleActivityUserSelected(Long chatId, String dateStr, Long userId) {
        try {
            java.time.LocalDate date = java.time.LocalDate.parse(dateStr);
            List<com.example.m1nd.model.UserSessionSummary> summaries = 
                summaryService.getSummariesByUserAndDate(userId, date);
            
            if (summaries.isEmpty()) {
                SendMessage message = new SendMessage();
                message.setChatId(chatId.toString());
                message.setText("❌ Для выбранного пользователя на эту дату нет сводок.");
                message.setReplyMarkup(createAdminMenuKeyboard());
                
                try {
                    execute(message);
                } catch (TelegramApiException e) {
                    logger.error("Ошибка при отправке сообщения", e);
                }
                return;
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append("📊 Сводки активности пользователя ");
            if (summaries.get(0).getUsername() != null) {
                sb.append("@").append(summaries.get(0).getUsername());
            } else {
                sb.append("ID: ").append(userId);
            }
            sb.append(" за ").append(dateStr).append(":\n\n");
            
            for (int i = 0; i < summaries.size(); i++) {
                com.example.m1nd.model.UserSessionSummary summary = summaries.get(i);
                sb.append("━━━━━━━━━━━━━━━━━━━━\n");
                sb.append("📝 Сводка #").append(i + 1).append("\n\n");
                sb.append("❓ ВОПРОС:\n");
                sb.append(summary.getSummaryQuestion()).append("\n\n");
                sb.append("💬 ОТВЕТ:\n");
                sb.append(summary.getSummaryAnswer()).append("\n\n");
            }
            
            // Разбиваем на части если длинно
            sendLongMessage(chatId, sb.toString(), true);
        } catch (Exception e) {
            logger.error("Ошибка при показе сводок пользователя", e);
            SendMessage errorMessage = new SendMessage();
            errorMessage.setChatId(chatId.toString());
            errorMessage.setText("❌ Ошибка при получении сводок: " + e.getMessage());
            
            try {
                execute(errorMessage);
            } catch (TelegramApiException ex) {
                logger.error("Ошибка при отправке сообщения об ошибке", ex);
            }
        }
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

