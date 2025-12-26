package com.example.m1nd.bot;

import com.example.m1nd.config.TelegramBotConfig;
import com.example.m1nd.service.LLMService;
import com.example.m1nd.service.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import jakarta.annotation.PostConstruct;

@Component
@RequiredArgsConstructor
public class M1ndTelegramBot extends TelegramLongPollingBot {
    
    private static final Logger logger = LoggerFactory.getLogger(M1ndTelegramBot.class);
    
    private final TelegramBotConfig botConfig;
    private final UserService userService;
    private final LLMService llmService;
    
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
        logger.info("Бот готов к получению обновлений. Ожидаю команды /start...");
    }
    
    @Override
    public void onUpdateReceived(Update update) {
        logger.info("Получено обновление: {}", update);
        
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
            } else {
                // Обработка обычных сообщений (вопросов)
                logger.info("Обработка вопроса: {}", messageText);
                handleQuestion(update, messageText);
            }
        } else {
            logger.warn("Обновление не содержит текстового сообщения: {}", update);
        }
    }
    
    private void handleStartCommand(Update update) {
        // Регистрируем пользователя
        userService.registerUser(update);
        
        Long chatId = update.getMessage().getChatId();
        
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Привет! Это ♾\uFE0F пространство для тех, кто ищет ресурсы — знания, ответы, поддержку. Для роста, масштабирования и гармонии. Спрашивай, о чем угодно! Помогу с ответами.");
        
        try {
            execute(message);
        } catch (TelegramApiException e) {
            logger.error("Ошибка при отправке сообщения", e);
        }
    }
    
    private void handleQuestion(Update update, String messageText) {
        Long chatId = update.getMessage().getChatId();
        Long userId = update.getMessage().getFrom().getId();
        
        // Отправляем сообщение о том, что обрабатываем запрос
        SendMessage processingMessage = new SendMessage();
        processingMessage.setChatId(chatId.toString());
        processingMessage.setText("Обрабатываю ваш вопрос...");
        
        try {
            execute(processingMessage);
        } catch (TelegramApiException e) {
            logger.error("Ошибка при отправке сообщения", e);
        }
        
        // Получаем ответ от LLM
        llmService.getAnswer(messageText, userId)
            .subscribe(
                answer -> {
                    // Разбиваем длинный ответ на части и отправляем
                    sendLongMessage(chatId, answer);
                    userService.incrementQuestionsCount(userId);
                    logger.info("Ответ отправлен пользователю {}: {}", userId, answer.substring(0, Math.min(50, answer.length())));
                },
                error -> {
                    logger.error("Ошибка при получении ответа от LLM", error);
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
        final int MAX_MESSAGE_LENGTH = 4096;
        final int PREFIX_LENGTH = 20; // Примерная длина префикса "(X/Y)\n\n"
        
        if (text.length() <= MAX_MESSAGE_LENGTH) {
            // Если сообщение короткое, отправляем как есть
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(text);
            
            try {
                execute(message);
            } catch (TelegramApiException e) {
                logger.error("Ошибка при отправке сообщения", e);
            }
        } else {
            // Разбиваем на части
            int offset = 0;
            int partNumber = 1;
            // Учитываем длину префикса при расчете максимальной длины части
            int maxPartLength = MAX_MESSAGE_LENGTH - PREFIX_LENGTH;
            int totalParts = (int) Math.ceil((double) text.length() / maxPartLength);
            
            while (offset < text.length()) {
                int endIndex = Math.min(offset + maxPartLength, text.length());
                String part = text.substring(offset, endIndex);
                
                // Если это не последняя часть и разрыв происходит не на границе слова,
                // пытаемся найти ближайший перенос строки или пробел
                if (endIndex < text.length() && partNumber < totalParts) {
                    int lastNewline = part.lastIndexOf('\n');
                    int lastSpace = part.lastIndexOf(' ');
                    
                    // Предпочитаем перенос строки, если он не слишком далеко от конца
                    // Оставляем запас для префикса
                    int searchStart = maxPartLength - 200;
                    if (lastNewline > searchStart) {
                        part = text.substring(offset, offset + lastNewline + 1);
                        endIndex = offset + lastNewline + 1;
                    } else if (lastSpace > searchStart) {
                        part = text.substring(offset, offset + lastSpace);
                        endIndex = offset + lastSpace;
                    }
                }
                
                SendMessage message = new SendMessage();
                message.setChatId(chatId.toString());
                
                // Добавляем номер части, если сообщение разбито на несколько
                String prefix = String.format("(%d/%d)\n\n", partNumber, totalParts);
                String messageText = prefix + part;
                
                // Дополнительная проверка на случай, если префикс + часть все равно превышают лимит
                if (messageText.length() > MAX_MESSAGE_LENGTH) {
                    // Обрезаем часть, чтобы поместиться с префиксом
                    int availableLength = MAX_MESSAGE_LENGTH - prefix.length();
                    part = part.substring(0, Math.min(availableLength, part.length()));
                    messageText = prefix + part;
                }
                
                message.setText(messageText);
                
                try {
                    execute(message);
                    logger.info("Отправлена часть {}/{} (длина: {})", partNumber, totalParts, messageText.length());
                    // Небольшая задержка между сообщениями, чтобы не превысить rate limit
                    Thread.sleep(100);
                } catch (TelegramApiException e) {
                    logger.error("Ошибка при отправке части сообщения {}/{}", partNumber, totalParts, e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("Прервана отправка сообщения", e);
                }
                
                offset = endIndex;
                partNumber++;
            }
        }
    }
}

