package com.example.m1nd.bot;

import com.example.m1nd.config.TelegramBotConfig;
import com.example.m1nd.service.AdminService;
import com.example.m1nd.service.LLMService;
import com.example.m1nd.service.StatisticsService;
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
    
    @Value("${llm.api.use-llm-service:true}")
    private boolean useLlmService;
    
    // Храним состояние ожидания username для добавления админа
    private final java.util.Map<Long, Boolean> waitingForAdminUsername = new java.util.concurrent.ConcurrentHashMap<>();
    
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
        
        // Вебхук теперь удаляется в TelegramBotConfiguration перед регистрацией бота
        logger.info("Бот готов к получению обновлений. Ожидаю команды /start...");
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
        } else if (normalizedText.startsWith("/addadmin")) {
            // Обработка команды /addadmin (только для администраторов)
            logger.info("Обработка команды /addadmin");
            handleAddAdminCommand(update, messageText);
        } else {
            // Проверяем, ожидаем ли мы username для добавления админа
            Long userId = update.getMessage().getFrom().getId();
            String username = update.getMessage().getFrom().getUserName();
            
            if (waitingForAdminUsername.getOrDefault(userId, false) && 
                username != null && adminService.isAdmin(username)) {
                // Обрабатываем username для добавления админа
                handleAddAdminUsername(update, messageText);
                waitingForAdminUsername.remove(userId);
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
        if (username != null && adminService.isAdmin(username)) {
            message.setReplyMarkup(createAdminKeyboard());
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
        } else if (data != null && data.startsWith("add_admin:")) {
            // Обработка добавления админа (будет реализовано ниже)
            String targetUsername = data.substring("add_admin:".length());
            handleAddAdminCallback(callbackQuery, targetUsername);
        } else if ("add_admin_prompt".equals(data)) {
            // Запрос на добавление админа - устанавливаем флаг ожидания
            waitingForAdminUsername.put(userId, true);
            
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("📝 Отправьте username пользователя, которого хотите добавить как администратора.\n\n" +
                "Формат: @username или просто username\n\n" +
                "Пример: @puh2012 или puh2012");
            
            try {
                execute(message);
                sendCallbackAnswer(callbackQuery.getId(), "✅ Введите username");
            } catch (TelegramApiException e) {
                logger.error("Ошибка при отправке сообщения", e);
                sendCallbackAnswer(callbackQuery.getId(), "❌ Ошибка");
                waitingForAdminUsername.remove(userId);
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
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        
        InlineKeyboardButton statsButton = new InlineKeyboardButton();
        statsButton.setText("📊 Статистика");
        statsButton.setCallbackData("stats");
        
        InlineKeyboardButton addAdminButton = new InlineKeyboardButton();
        addAdminButton.setText("➕ Добавить админа");
        addAdminButton.setCallbackData("add_admin_prompt");
        
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(statsButton);
        row.add(addAdminButton);
        
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        keyboard.add(row);
        
        markup.setKeyboard(keyboard);
        return markup;
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
}

