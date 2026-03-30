package com.example.m1nd.bot;

import com.example.m1nd.config.TelegramBotConfig;
import com.example.m1nd.model.AssistantType;
import com.example.m1nd.service.AdminService;
import com.example.m1nd.service.AssistantPromptContextService;
import com.example.m1nd.service.AssistantService;
import com.example.m1nd.service.FeedbackService;
import com.example.m1nd.service.LLMService;
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
    private final AdminService adminService;
    private final AssistantService assistantService;
    private final AssistantPromptContextService assistantPromptContextService;
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
    private static class HumanExpertRequest {
        private final Long userId;
        private final Long assistantUserId;
        private final String mode; // question | meeting

        private HumanExpertRequest(Long userId, Long assistantUserId, String mode) {
            this.userId = userId;
            this.assistantUserId = assistantUserId;
            this.mode = mode;
        }
    }

    private final java.util.Map<String, HumanExpertRequest> humanExpertRequests =
        new java.util.concurrent.ConcurrentHashMap<>();

    private final java.util.Map<Long, String> activeMeetingTicketByUser =
        new java.util.concurrent.ConcurrentHashMap<>();

    // Human expert: активная "вкладка" (ticket) у специалиста
    private final java.util.Map<Long, String> activeTicketByAssistantUserId =
        new java.util.concurrent.ConcurrentHashMap<>();

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
            Long userId = update.getMessage().getFrom().getId();
            
            // Нормализуем команду (убираем регистр и параметры)
            String normalizedText = messageText.toLowerCase();
            
            // Если сообщение от ассистента бизнеса и это ответ на вопрос
            if (assistantService.isAssistant(userId) && normalizedText.startsWith("/answer_")) {
                handleAssistantAnswer(update, messageText);
                return;
            }

            // Human expert: если специалист сейчас отвечает на ticket —
            // сразу отправляем ответ пользователю без черновика.
            if (assistantService.isAssistant(userId)) {
                String activeTicketId = activeTicketByAssistantUserId.get(userId);
                if (activeTicketId != null) {
                    sendAssistantTicketAnswer(userId, update.getMessage().getChatId(), activeTicketId, messageText);
                    return;
                }
            }

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
                } else {
                    MainMenuService.GameAnswerResult gameResult =
                        mainMenuService.handleGameAnswer(chatId, userId, messageText);
                    if (gameResult.isHandled()) {
                        for (SendMessage msg : gameResult.getMessages()) {
                            try {
                                execute(msg);
                            } catch (TelegramApiException e) {
                                logger.error("Ошибка при отправке ответа в игре", e);
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

        // На /start сбрасываем активную Human Expert встречу (если была).
        clearActiveMeetingState(userId);
        clearActiveQuestionState(userId);
        
        // Отслеживаем активность
        userService.trackUserActivity(userId);
        
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(
            "Добро пожаловать в ваш личный помощник по решению задач.\n\n" +
            "Чем могу помочь? Для вашего удобства доступны два формата поддержки:\n\n" +
            "AI Assistant — молниеносная скорость и точность от ИИ-асситентов.\n" +
            "Human Expert — индивидуальный подход и экспертиза от реальных специалистов.\n\n" +
            "Напишите суть вопроса, и я порекомендую, кто справится с ним лучше: нейросеть или живой профессионал."
        );
        
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

            // Сразу показываем меню выбора из 3 ассистентов
            SendMessage menuMessage = mainMenuService.buildMainMenuMessage(chatId);
            execute(menuMessage);
        } catch (TelegramApiException e) {
            logger.error("Ошибка при отправке сообщения", e);
        }
    }
    
    private void handleQuestion(Update update, String messageText) {
        Long chatId = update.getMessage().getChatId();
        Long userId = update.getMessage().getFrom().getId();
        String modeCode = assistantPromptContextService.getMode(userId);
        
        // Отслеживаем активность пользователя
        userService.trackUserActivity(userId);
        
        // Отменяем предыдущую запланированную сводку, если она есть
        cancelScheduledSummary(userId);

        // Human expert: режим question — создаем ticket сразу при сообщении пользователя
        if ("question".equals(modeCode)) {
            SendMessage processingToUser = new SendMessage();
            processingToUser.setChatId(chatId.toString());
            processingToUser.setText("Отправляю вопрос специалисту....");
            try {
                execute(processingToUser);
            } catch (TelegramApiException e) {
                logger.error("Ошибка при отправке пользователю статуса отправки запроса", e);
            }

            var assistantOpt = assistantService.findRandomActiveAssistantByType(AssistantType.MESSAGE);
            if (assistantOpt.isEmpty()) {
                SendMessage msg = new SendMessage();
                msg.setChatId(chatId.toString());
                msg.setText("Сейчас нет доступных специалистов для сообщений. Попробуйте немного позже.");
                try {
                    execute(msg);
                } catch (TelegramApiException e) {
                    logger.error("Ошибка при отправке сообщения об отсутствии специалистов", e);
                }
                assistantPromptContextService.clear(userId);
                return;
            }

            var assistant = assistantOpt.get();
            Long assistantTelegramUserId = assistant.getTelegramUserId();

            String ticketId = java.util.UUID.randomUUID().toString();
            humanExpertRequests.put(ticketId, new HumanExpertRequest(userId, assistantTelegramUserId, "question"));

            // Сообщаем пользователю, что запрос создан
            SendMessage toUser = new SendMessage();
            toUser.setChatId(chatId.toString());
            toUser.setText("Ваш вопрос отправлен специалисту. Ожидайте ответа в этом чате");
            try {
                execute(toUser);
            } catch (TelegramApiException e) {
                logger.error("Ошибка при отправке пользователю подтверждения отправки", e);
            }

            // Сообщаем специалисту в чате с ботом
            String userUsername = update.getMessage().getFrom().getUserName();
            String who = userUsername != null ? "@" + userUsername : "пользователь с id=" + userId;

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText("Ответить на вопрос пользователю");
            button.setCallbackData("expert_answer_start:" + ticketId);
            markup.setKeyboard(List.of(List.of(button)));

            SendMessage toAssistant = new SendMessage();
            toAssistant.setChatId(assistantTelegramUserId.toString());
            toAssistant.setText("💼 Новый запрос (вопрос):\n\n" + messageText + "\n\n" + "Пользователь: " + who);
            toAssistant.setReplyMarkup(markup);
            try {
                execute(toAssistant);
            } catch (TelegramApiException e) {
                logger.error("Ошибка при отправке запроса специалисту", e);
            }

            // Сбрасываем режим у пользователя, чтобы не создавать второй ticket
            assistantPromptContextService.clear(userId);
            return;
        }

        // Human expert: режим meeting — оставляем старую логику
        if ("meeting".equals(modeCode)) {
            SendMessage processingMessage = new SendMessage();
            processingMessage.setChatId(chatId.toString());
            processingMessage.setText("Отправляю запрос специалисту...");
            try {
                execute(processingMessage);
            } catch (TelegramApiException e) {
                logger.error("Ошибка при отправке сообщения", e);
            }

            handleHumanExpertRequest(update, messageText, modeCode);
            return;
        }

        // AI: обрабатываем обычный вопрос
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
            answerMono = workingApiService.getAnswer(messageText, userId);
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

        // При смене раздела/режима прекращаем активную "встречу" (если она была).
        if ("main_menu_back".equals(data)
            || "main_business_ai_assistant".equals(data)
            || "main_financial_ai_assistant".equals(data)
            || "main_thinking_ai_assistant".equals(data)) {
            clearActiveMeetingState(userId);
            clearActiveQuestionState(userId);
        } else if (data != null && data.startsWith("assistant_choice:")) {
            String[] parts = data.split(":");
            if (parts.length == 3 && !"meeting".equals(parts[2])) {
                clearActiveMeetingState(userId);
                clearActiveQuestionState(userId);
            }
        }
        
        // Обработка опросов (доступны всем пользователям)
        if (data != null && data.startsWith("feedback_")) {
            handleFeedbackCallback(callbackQuery, data);
            return;
        }

        // Human expert: открываем ticket у специалиста (переходим в ввод ответа)
        if (data != null && data.startsWith("expert_answer_start:")) {
            String ticketId = data.substring("expert_answer_start:".length()).trim();
            if (ticketId.isBlank()) {
                sendCallbackAnswer(callbackQuery.getId(), "❌ Некорректный ticket");
                return;
            }

            HumanExpertRequest request = humanExpertRequests.get(ticketId);
            if (request == null) {
                sendCallbackAnswer(callbackQuery.getId(), "❌ Ticket не найден или уже завершен");
                return;
            }

            if (!userId.equals(request.assistantUserId)) {
                sendCallbackAnswer(callbackQuery.getId(), "❌ Этот ticket назначен другому специалисту");
                return;
            }

            activeTicketByAssistantUserId.put(userId, ticketId);

            SendMessage toAssistant = new SendMessage();
            toAssistant.setChatId(callbackQuery.getMessage().getChatId().toString());

            String targetUsername = userService.getUser(request.userId)
                .map(u -> u.getUsername())
                .orElse(null);
            String displayUsername = (targetUsername != null && !targetUsername.isBlank())
                ? targetUsername
                : String.valueOf(request.userId);

            toAssistant.setText("Введите ответ на вопрос пользователя " + displayUsername + " :");
            try {
                execute(toAssistant);
            } catch (TelegramApiException e) {
                logger.error("Ошибка при отправке приглашения специалисту", e);
            }

            sendCallbackAnswer(callbackQuery.getId(), "✅ Открыта вкладка ответа");
            return;
        }

        // Для режимов question/meeting сразу отдаем контакт специалиста для прямого общения.
        if (data != null && data.startsWith("assistant_choice:")) {
            String[] parts = data.split(":");
            if (parts.length == 3) {
                String assistantCode = parts[1];
                String modeCode = parts[2];
                if ("question".equals(modeCode) || "meeting".equals(modeCode)) {
                    assistantPromptContextService.setAssistant(userId, assistantCode);
                    assistantPromptContextService.setMode(userId, modeCode);

                    sendSpecialistDirectContact(chatId, modeCode, username);
                    // Для meeting оставляем старый сценарий (прямой handoff), для question —
                    // режим остается активным до первого отправленного сообщения пользователя.
                    if ("meeting".equals(modeCode)) {
                        assistantPromptContextService.clearMode(userId);
                        sendCallbackAnswer(callbackQuery.getId(), "✅ Контакт специалиста отправлен");
                    } else {
                        sendCallbackAnswer(callbackQuery.getId(), "✅ Режим вопроса активирован. Напишите текст вопроса.");
                    }
                    return;
                }
            }
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

    private void handleHumanExpertRequest(Update update, String messageText, String modeCode) {
        Long userId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();

        // meeting оставляем как прямой handoff (как было).
        // question теперь идет через ticket (см. human_question_send), поэтому сюда обычно не попадает.
        if ("meeting".equals(modeCode)) {
            sendSpecialistDirectContact(chatId, modeCode, update.getMessage().getFrom().getUserName());
            assistantPromptContextService.clearMode(userId);
        }
    }

    private void sendSpecialistDirectContact(Long chatId, String modeCode, String userUsername) {
        boolean isMeeting = "meeting".equals(modeCode);
        AssistantType requiredType = isMeeting ? AssistantType.MEETING : AssistantType.MESSAGE;
        var assistantOpt = assistantService.findRandomActiveAssistantByType(requiredType);

        if (assistantOpt.isEmpty()) {
            SendMessage msg = new SendMessage();
            msg.setChatId(chatId.toString());
            msg.setText(isMeeting
                ? "Сейчас нет доступных специалистов для встречи. Попробуйте немного позже."
                : "Сейчас нет доступных специалистов для сообщений. Попробуйте немного позже.");
            try {
                execute(msg);
            } catch (TelegramApiException e) {
                logger.error("Ошибка при отправке сообщения об отсутствии специалистов", e);
            }
            return;
        }

        var assistant = assistantOpt.get();
        String assistantUsername = assistant.getUsername();
        String cleanAssistantUsername = assistantUsername == null ? "" :
            (assistantUsername.startsWith("@") ? assistantUsername.substring(1) : assistantUsername);

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());

        if (cleanAssistantUsername.isBlank()) {
            if (isMeeting) {
                message.setText(
                    "Специалист выбран, но у него пока не указан публичный username.\n\n" +
                    "Пожалуйста, обратитесь в поддержку бота, чтобы получить контакт."
                );
            } else {
                // Для question нам публичный username не нужен: ответ будет идти через бота по ticket.
                message.setText("Задай вопрос Дмитрию, чтобы получить ответ.");
            }
        } else if (isMeeting) {
            message.setText(
                "Запланируй онлайн встречу с Дмитрием. Связаться с ассистентом Дмитрия, для согласования времени и деталей."
            );

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText("Написать специалисту");
            button.setUrl("https://t.me/" + cleanAssistantUsername);
            markup.setKeyboard(List.of(List.of(button)));
            message.setReplyMarkup(markup);
        } else {
            message.setText(
                "Задай вопрос Дмитрию, чтобы получить ответ."
            );
        }

        try {
            execute(message);
        } catch (TelegramApiException e) {
            logger.error("Ошибка при отправке контакта специалиста пользователю", e);
        }
    }

    private void handleAssistantAnswer(Update update, String messageText) {
        Long assistantUserId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();

        String text = messageText.trim();
        int spaceIndex = text.indexOf(' ');
        if (!text.startsWith("/answer_") || spaceIndex <= "/answer_".length()) {
            SendMessage msg = new SendMessage();
            msg.setChatId(chatId.toString());
            msg.setText("❗ Используйте формат:\n/answer_<ticket> текст ответа");
            try {
                execute(msg);
            } catch (TelegramApiException e) {
                logger.error("Ошибка при отправке подсказки ассистенту", e);
            }
            return;
        }

        String ticketId = text.substring("/answer_".length(), spaceIndex).trim();
        String answerText = text.substring(spaceIndex + 1).trim();

        HumanExpertRequest request = humanExpertRequests.get(ticketId);
        if (request == null) {
            SendMessage msg = new SendMessage();
            msg.setChatId(chatId.toString());
            msg.setText("❌ Некорректный ticket или запрос уже завершен.");
            try {
                execute(msg);
            } catch (TelegramApiException ex) {
                logger.error("Ошибка при отправке сообщения об ошибке ассистенту", ex);
            }
            return;
        }

        if (!assistantUserId.equals(request.assistantUserId)) {
            SendMessage msg = new SendMessage();
            msg.setChatId(chatId.toString());
            msg.setText("❌ Этот ticket назначен другому специалисту.");
            try {
                execute(msg);
            } catch (TelegramApiException ex) {
                logger.error("Ошибка при отправке сообщения об ошибке ассистенту", ex);
            }
            return;
        }

        sendAssistantTicketAnswer(assistantUserId, chatId, ticketId, answerText);
    }

    private void sendAssistantTicketAnswer(Long assistantUserId, Long assistantChatId, String ticketId, String answerText) {
        HumanExpertRequest request = humanExpertRequests.get(ticketId);
        if (request == null) {
            SendMessage msg = new SendMessage();
            msg.setChatId(assistantChatId.toString());
            msg.setText("❌ Ticket не найден или уже завершен.");
            try {
                execute(msg);
            } catch (TelegramApiException e) {
                logger.error("Ошибка при отправке сообщения об ошибке ассистенту", e);
            }
            activeTicketByAssistantUserId.remove(assistantUserId);
            return;
        }

        if (!assistantUserId.equals(request.assistantUserId)) {
            SendMessage msg = new SendMessage();
            msg.setChatId(assistantChatId.toString());
            msg.setText("❌ Этот ticket назначен другому специалисту.");
            try {
                execute(msg);
            } catch (TelegramApiException e) {
                logger.error("Ошибка при отправке сообщения об ошибке ассистенту", e);
            }
            return;
        }

        if (answerText == null || answerText.isBlank()) {
            SendMessage msg = new SendMessage();
            msg.setChatId(assistantChatId.toString());
            msg.setText("❗ Ответ не может быть пустым.");
            try {
                execute(msg);
            } catch (TelegramApiException e) {
                logger.error("Ошибка при отправке сообщения о пустом ответе", e);
            }
            return;
        }

        SendMessage toUser = new SendMessage();
        toUser.setChatId(request.userId.toString());
        toUser.setText("💼 Ответ специалиста:\n\n" + answerText);
        try {
            execute(toUser);
        } catch (TelegramApiException e) {
            logger.error("Ошибка при отправке ответа пользователю", e);
        }

        SendMessage confirm = new SendMessage();
        confirm.setChatId(assistantChatId.toString());
        String targetUsername = userService.getUser(request.userId)
            .map(u -> u.getUsername())
            .orElse(null);
        String displayUsername = (targetUsername != null && !targetUsername.isBlank())
            ? (targetUsername.startsWith("@") ? targetUsername : "@" + targetUsername)
            : "id=" + request.userId;
        confirm.setText("Ответ отправлен пользователю " + displayUsername);
        try {
            execute(confirm);
        } catch (TelegramApiException e) {
            logger.error("Ошибка при отправке подтверждения ассистенту", e);
        }

        if ("question".equals(request.mode)) {
            humanExpertRequests.remove(ticketId);
            activeTicketByAssistantUserId.remove(assistantUserId);
            assistantPromptContextService.clear(request.userId);
        }
    }

    private void clearActiveMeetingState(Long userId) {
        if (userId == null) {
            return;
        }
        String ticketId = activeMeetingTicketByUser.remove(userId);
        if (ticketId != null) {
            humanExpertRequests.remove(ticketId);
        }
    }

    private void clearActiveQuestionState(Long userId) {
        if (userId == null) {
            return;
        }
        // В question-сценарии ticket создается сразу при сообщении пользователя,
        // поэтому здесь чистим только контекст режима.
        assistantPromptContextService.clear(userId);
    }

    private void activeTicketByAssistantUserUserIdCleanup(Long assistantUserId) {
        if (assistantUserId == null) {
            return;
        }
        activeTicketByAssistantUserId.remove(assistantUserId);
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

