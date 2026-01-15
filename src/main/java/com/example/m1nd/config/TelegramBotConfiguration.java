package com.example.m1nd.config;

import com.example.m1nd.bot.M1ndTelegramBot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.updates.DeleteWebhook;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Slf4j
@Configuration
public class TelegramBotConfiguration {
    
    @Bean
    public TelegramBotsApi telegramBotsApi(M1ndTelegramBot bot) {
        try {
            // ВАЖНО: Удаляем вебхук ПЕРЕД регистрацией бота (несколько попыток)
            log.info("Удаление вебхука перед регистрацией бота...");
            int webhookDeleteAttempts = 3;
            for (int i = 0; i < webhookDeleteAttempts; i++) {
                try {
                    DeleteWebhook deleteWebhook = new DeleteWebhook();
                    deleteWebhook.setDropPendingUpdates(true);
                    bot.execute(deleteWebhook);
                    log.info("Вебхук успешно удален (попытка {}). Используется long polling.", i + 1);
                    break;
                } catch (TelegramApiException e) {
                    if (i < webhookDeleteAttempts - 1) {
                        log.warn("Не удалось удалить вебхук (попытка {}). Повтор через 2 секунды...", i + 1);
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    } else {
                        log.warn("Не удалось удалить вебхук после {} попыток: {}", webhookDeleteAttempts, e.getMessage());
                    }
                }
            }
            
            // Увеличиваем задержку для гарантии, что вебхук удален на стороне Telegram
            log.info("Ожидание 5 секунд для применения изменений на стороне Telegram...");
            Thread.sleep(5000);
            
            // Регистрация бота
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(bot);
            log.info("Бот успешно зарегистрирован в TelegramBotsApi");
            return botsApi;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Прервана задержка при удалении вебхука", e);
            throw new RuntimeException("Не удалось зарегистрировать бота", e);
        } catch (TelegramApiException e) {
            String errorMessage = e.getMessage() != null ? e.getMessage() : "";
            if (errorMessage.contains("409") || errorMessage.contains("Conflict")) {
                log.error("Конфликт 409 при регистрации бота. Возможно, запущен другой экземпляр.");
                log.error("Убедитесь, что запущен только один экземпляр приложения.");
                log.error("Попытка продолжить работу несмотря на конфликт...");
                // Пытаемся все равно зарегистрировать - возможно, другой экземпляр скоро остановится
                try {
                    Thread.sleep(5000);  // Ждем 5 секунд
                    TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
                    botsApi.registerBot(bot);
                    log.warn("Бот зарегистрирован несмотря на конфликт. Может работать некорректно.");
                    return botsApi;
                } catch (TelegramApiException | InterruptedException ex) {
                    if (ex instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    log.error("Не удалось зарегистрировать бота даже после попытки обхода конфликта", ex);
                    throw new RuntimeException("Не удалось зарегистрировать бота из-за конфликта 409. Убедитесь, что запущен только один экземпляр.", ex);
                }
            } else {
                // Другая ошибка при удалении вебхука - продолжаем регистрацию
                log.warn("Ошибка при удалении вебхука: {}. Продолжаем регистрацию...", e.getMessage());
                try {
                    TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
                    botsApi.registerBot(bot);
                    log.info("Бот успешно зарегистрирован в TelegramBotsApi (вебхук не был удален)");
                    return botsApi;
                } catch (TelegramApiException ex) {
                    log.error("Ошибка при регистрации бота", ex);
                    throw new RuntimeException("Не удалось зарегистрировать бота", ex);
                }
            }
        }
    }
}

