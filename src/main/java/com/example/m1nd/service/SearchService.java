package com.example.m1nd.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {
    
    private final WebClient.Builder webClientBuilder;
    
    @Value("${search.enabled:true}")
    private boolean searchEnabled;
    
    /**
     * Ищет информацию в DuckDuckGo и возвращает результаты поиска
     * DuckDuckGo не требует регистрации и API ключей
     */
    public Mono<List<SearchResult>> search(String query) {
        if (!searchEnabled) {
            log.debug("Поиск отключен");
            return Mono.just(new ArrayList<SearchResult>());
        }
        
        return searchWithDuckDuckGo(query);
    }
    
    /**
     * Поиск через DuckDuckGo (без регистрации, без API ключей)
     */
    private Mono<List<SearchResult>> searchWithDuckDuckGo(String query) {
        WebClient webClient = webClientBuilder.build();
        
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://html.duckduckgo.com/html/?q=" + encodedQuery;
            
            log.info("Выполняю поиск в DuckDuckGo: {}", query);
            
            return webClient.get()
                .uri(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                .retrieve()
                .bodyToMono(String.class)
                .map(html -> {
                    try {
                        // Парсим HTML ответ от DuckDuckGo
                        List<SearchResult> results = parseDuckDuckGoHTML(html);
                        log.info("Найдено результатов: {}", results.size());
                        return results;
                    } catch (Exception e) {
                        log.error("Ошибка при парсинге ответа от DuckDuckGo", e);
                        return new ArrayList<SearchResult>();
                    }
                })
                .doOnError(error -> log.error("Ошибка при запросе к DuckDuckGo", error))
                .onErrorReturn(new ArrayList<SearchResult>());
        } catch (Exception e) {
            log.error("Ошибка при формировании запроса к DuckDuckGo", e);
            return Mono.just(new ArrayList<SearchResult>());
        }
    }
    
    /**
     * Парсит HTML ответ от DuckDuckGo
     */
    private List<SearchResult> parseDuckDuckGoHTML(String html) {
        List<SearchResult> results = new ArrayList<>();
        
        try {
            // DuckDuckGo использует классы для результатов поиска
            // Ищем блоки с результатами поиска
            String resultPattern = "<div class=\"result[^\"]*\">(.*?)</div>\\s*</div>";
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(resultPattern, java.util.regex.Pattern.DOTALL);
            java.util.regex.Matcher matcher = pattern.matcher(html);
            
            while (matcher.find() && results.size() < 5) {
                String resultBlock = matcher.group(1);
                
                // Извлекаем заголовок (обычно в ссылке)
                String title = extractHTMLValue(resultBlock, "a", "class", "result__a");
                if (title.isEmpty()) {
                    // Альтернативный способ извлечения заголовка
                    title = extractHTMLByTag(resultBlock, "a", "class", "result__a");
                }
                
                // Извлекаем URL
                String url = extractHTMLAttribute(resultBlock, "a", "href");
                if (url.isEmpty()) {
                    url = extractHTMLAttribute(resultBlock, "a", "class", "result__a", "href");
                }
                
                // Извлекаем описание (snippet)
                String snippet = extractHTMLValue(resultBlock, "a", "class", "result__snippet");
                if (snippet.isEmpty()) {
                    snippet = extractHTMLByTag(resultBlock, "a", "class", "result__snippet");
                }
                
                // Очищаем HTML теги из текста
                title = cleanHTML(title);
                snippet = cleanHTML(snippet);
                
                if (!title.isEmpty() && !url.isEmpty()) {
                    results.add(new SearchResult(title, url, snippet));
                }
            }
            
            // Если не нашли результаты в новом формате, пробуем старый формат
            if (results.isEmpty()) {
                results = parseDuckDuckGoHTMLOldFormat(html);
            }
        } catch (Exception e) {
            log.error("Ошибка при парсинге HTML ответа от DuckDuckGo", e);
        }
        
        return results;
    }
    
    /**
     * Альтернативный парсинг для старого формата DuckDuckGo
     */
    private List<SearchResult> parseDuckDuckGoHTMLOldFormat(String html) {
        List<SearchResult> results = new ArrayList<>();
        
        try {
            // Ищем ссылки на результаты
            String linkPattern = "<a[^>]*class=\"[^\"]*result__a[^\"]*\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>";
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(linkPattern, java.util.regex.Pattern.DOTALL);
            java.util.regex.Matcher matcher = pattern.matcher(html);
            
            while (matcher.find() && results.size() < 5) {
                String url = matcher.group(1);
                String titleHtml = matcher.group(2);
                String title = cleanHTML(titleHtml);
                
                // Ищем snippet после ссылки
                int linkEnd = matcher.end();
                String afterLink = html.substring(Math.min(linkEnd, html.length()));
                String snippet = extractHTMLValue(afterLink, "a", "class", "result__snippet");
                snippet = cleanHTML(snippet);
                
                if (!title.isEmpty() && !url.isEmpty()) {
                    results.add(new SearchResult(title, url, snippet));
                }
            }
        } catch (Exception e) {
            log.error("Ошибка при альтернативном парсинге HTML", e);
        }
        
        return results;
    }
    
    /**
     * Извлекает значение из HTML элемента по тегу и классу
     */
    private String extractHTMLValue(String html, String tag, String attribute, String attributeValue) {
        String pattern = String.format("<%s[^>]*%s=\"[^\"]*%s[^\"]*\"[^>]*>(.*?)</%s>", 
            tag, attribute, attributeValue, tag);
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher m = p.matcher(html);
        if (m.find()) {
            return m.group(1).trim();
        }
        return "";
    }
    
    /**
     * Извлекает значение из HTML элемента по тегу
     */
    private String extractHTMLByTag(String html, String tag, String attribute, String attributeValue) {
        String pattern = String.format("<%s[^>]*%s=\"[^\"]*%s[^\"]*\"[^>]*>([^<]+)", 
            tag, attribute, attributeValue);
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher m = p.matcher(html);
        if (m.find()) {
            return m.group(1).trim();
        }
        return "";
    }
    
    /**
     * Извлекает атрибут из HTML элемента
     */
    private String extractHTMLAttribute(String html, String tag, String attribute) {
        String pattern = String.format("<%s[^>]*%s=\"([^\"]+)\"", tag, attribute);
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(html);
        if (m.find()) {
            return m.group(1).trim();
        }
        return "";
    }
    
    /**
     * Извлекает атрибут из HTML элемента по классу
     */
    private String extractHTMLAttribute(String html, String tag, String classAttr, String classValue, String attribute) {
        String pattern = String.format("<%s[^>]*%s=\"[^\"]*%s[^\"]*\"[^>]*%s=\"([^\"]+)\"", 
            tag, classAttr, classValue, attribute);
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(html);
        if (m.find()) {
            return m.group(1).trim();
        }
        return "";
    }
    
    /**
     * Очищает HTML теги из текста
     */
    private String cleanHTML(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        // Удаляем HTML теги
        String cleaned = html.replaceAll("<[^>]+>", "");
        // Декодируем HTML entities
        cleaned = cleaned.replace("&amp;", "&")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .replace("&quot;", "\"")
                        .replace("&#39;", "'")
                        .replace("&nbsp;", " ");
        return cleaned.trim();
    }
    
    /**
     * Форматирует результаты поиска для добавления в контекст LLM
     */
    public String formatSearchResults(List<SearchResult> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder("\n\nРезультаты поиска в интернете:\n");
        for (int i = 0; i < results.size(); i++) {
            SearchResult result = results.get(i);
            sb.append(String.format("%d. %s\n", i + 1, result.title));
            sb.append(String.format("   Ссылка: %s\n", result.link));
            sb.append(String.format("   Описание: %s\n\n", result.snippet));
        }
        
        return sb.toString();
    }
    
    /**
     * Класс для хранения результата поиска
     */
    public static class SearchResult {
        public final String title;
        public final String link;
        public final String snippet;
        
        public SearchResult(String title, String link, String snippet) {
            this.title = title;
            this.link = link;
            this.snippet = snippet;
        }
    }
}

