package com.example.m1nd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.example.m1nd.config.TelegramBotConfig;

@SpringBootApplication
@EnableConfigurationProperties(TelegramBotConfig.class)
public class M1ndApplication {

	public static void main(String[] args) {
		SpringApplication.run(M1ndApplication.class, args);
	}

}
