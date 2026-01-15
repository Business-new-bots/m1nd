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
            // ВАЖНО: Удаляем вебхук ПЕРЕД регистрацией бота
            log.info("Удаление вебхука перед регистрацией бота...");
            DeleteWebhook deleteWebhook = new DeleteWebhook();
            deleteWebhook.setDropPendingUpdates(true);
            bot.execute(deleteWebhook);
            log.info("Вебхук успешно удален. Используется long polling.");
            
            // Небольшая задержка для гарантии, что вебхук удален на стороне Telegram
            Thread.sleep(1000);
            
            // Регистрация бота с обработкой ошибки 409 (конфликт)
            int maxRetries = 3;
            int retryCount = 0;
            
            while (retryCount < maxRetries) {
                try {
                    TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
                    botsApi.registerBot(bot);
                    log.info("Бот успешно зарегистрирован в TelegramBotsApi");
                    return botsApi;
                } catch (TelegramApiException e) {
                    String errorMessage = e.getMessage() != null ? e.getMessage() : "";
                    if (errorMessage.contains("409") || errorMessage.contains("Conflict")) {
                        retryCount++;
                        if (retryCount < maxRetries) {
                            log.warn("Конфликт 409 при регистрации бота. Попытка {}/{}. Повтор через 2 секунды...", 
                                retryCount, maxRetries);
                            try {
                                Thread.sleep(2000);
                                // Повторно удаляем вебхук
                                DeleteWebhook retryDeleteWebhook = new DeleteWebhook();
                                retryDeleteWebhook.setDropPendingUpdates(true);
                                bot.execute(retryDeleteWebhook);
                                log.info("Вебхук повторно удален перед попыткой {}", retryCount + 1);
                            } catch (Exception ex) {
                                log.error("Ошибка при повторном удалении вебхука", ex);
                            }
                        } else {
                            log.error("Не удалось зарегистрировать бота после {} попыток. Возможно, запущено несколько экземпляров приложения.", maxRetries);
                            throw new RuntimeException("Не удалось зарегистрировать бота после " + maxRetries + " попыток. Убедитесь, что запущен только один экземпляр приложения.", e);
                        }
                    } else {
                        // Другая ошибка - не повторяем
                        log.error("Ошибка при регистрации бота", e);
                        throw new RuntimeException("Не удалось зарегистрировать бота", e);
                    }
                }
            }
            
            // Этот код не должен выполниться, но на всякий случай
            throw new RuntimeException("Не удалось зарегистрировать бота");
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Прервана задержка при удалении вебхука", e);
            throw new RuntimeException("Не удалось зарегистрировать бота", e);
        } catch (TelegramApiException e) {
            log.error("Ошибка при удалении вебхука", e);
            // Продолжаем регистрацию даже если не удалось удалить вебхук
            log.warn("Продолжаем регистрацию бота несмотря на ошибку удаления вебхука");
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

