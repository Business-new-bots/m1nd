package com.example.m1nd.config;

import com.example.m1nd.bot.M1ndTelegramBot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Slf4j
@Configuration
public class TelegramBotConfiguration {
    
    @Bean
    public TelegramBotsApi telegramBotsApi(M1ndTelegramBot bot) {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(bot);
            log.info("Бот успешно зарегистрирован в TelegramBotsApi");
            return botsApi;
        } catch (TelegramApiException e) {
            log.error("Ошибка при регистрации бота", e);
            throw new RuntimeException("Не удалось зарегистрировать бота", e);
        }
    }
}

