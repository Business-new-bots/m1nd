package com.example.m1nd.service.tools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSearchTool implements Tool {
    
    private final WebClient.Builder webClientBuilder;
    
    @Override
    public String getName() {
        return "web_search";
    }
    
    @Override
    public String getDescription() {
        return "Ищет актуальную информацию в интернете. Используй этот инструмент для поиска информации о текущих событиях, существовании групп, актуальных данных, текстах песен и другой информации, которая может быть устаревшей в твоих знаниях.";
    }
    
    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> queryParam = new HashMap<>();
        queryParam.put("type", "string");
        queryParam.put("description", "Поисковый запрос на русском или английском языке");
        properties.put("query", queryParam);
        
        schema.put("properties", properties);
        schema.put("required", java.util.Arrays.asList("query"));
        
        return schema;
    }
    
    @Override
    public String execute(Map<String, Object> parameters) {
        String query = (String) parameters.get("query");
        if (query == null || query.isEmpty()) {
            return "Ошибка: не указан поисковый запрос";
        }
        
        log.info("Выполняю поиск в интернете: {}", query);
        
        try {
            // Используем DuckDuckGo для поиска
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://html.duckduckgo.com/html/?q=" + encodedQuery;
            
            WebClient webClient = webClientBuilder.build();
            
            String html = webClient.get()
                .uri(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(10));
            
            if (html == null || html.isEmpty()) {
                return "Не удалось получить результаты поиска";
            }
            
            // Парсим результаты
            List<SearchResult> results = parseSearchResults(html);
            
            if (results.isEmpty()) {
                return "Результаты поиска не найдены. Попробуйте другой запрос.";
            }
            
            // Форматируем результаты для LLM
            StringBuilder sb = new StringBuilder("Результаты поиска в интернете:\n\n");
            for (int i = 0; i < Math.min(results.size(), 5); i++) {
                SearchResult result = results.get(i);
                sb.append(String.format("%d. %s\n", i + 1, result.title));
                sb.append(String.format("   URL: %s\n", result.url));
                if (!result.snippet.isEmpty()) {
                    sb.append(String.format("   Описание: %s\n", result.snippet));
                }
                sb.append("\n");
            }
            
            return sb.toString();
            
        } catch (Exception e) {
            log.error("Ошибка при выполнении поиска", e);
            return "Ошибка при поиске информации в интернете: " + e.getMessage();
        }
    }
    
    private List<SearchResult> parseSearchResults(String html) {
        List<SearchResult> results = new ArrayList<>();
        
        try {
            // Парсим результаты DuckDuckGo
            // Ищем блоки с результатами
            Pattern resultPattern = Pattern.compile(
                "<div class=\"result[^\"]*\">(.*?)</div>\\s*</div>", 
                Pattern.DOTALL
            );
            Matcher matcher = resultPattern.matcher(html);
            
            while (matcher.find() && results.size() < 5) {
                String resultBlock = matcher.group(1);
                
                // Извлекаем заголовок
                String title = extractValue(resultBlock, "a", "class", "result__a");
                if (title.isEmpty()) {
                    title = extractByTag(resultBlock, "a", "class", "result__a");
                }
                
                // Извлекаем URL
                String url = extractAttribute(resultBlock, "a", "href");
                if (url.isEmpty()) {
                    url = extractAttributeByClass(resultBlock, "a", "result__a", "href");
                }
                
                // Извлекаем описание
                String snippet = extractValue(resultBlock, "a", "class", "result__snippet");
                if (snippet.isEmpty()) {
                    snippet = extractByTag(resultBlock, "a", "class", "result__snippet");
                }
                
                title = cleanHTML(title);
                snippet = cleanHTML(snippet);
                
                if (!title.isEmpty() && !url.isEmpty()) {
                    results.add(new SearchResult(title, url, snippet));
                }
            }
        } catch (Exception e) {
            log.error("Ошибка при парсинге результатов поиска", e);
        }
        
        return results;
    }
    
    private String extractValue(String html, String tag, String attr, String attrValue) {
        String pattern = String.format("<%s[^>]*%s=\"[^\"]*%s[^\"]*\"[^>]*>(.*?)</%s>", 
            tag, attr, attrValue, tag);
        Pattern p = Pattern.compile(pattern, Pattern.DOTALL);
        Matcher m = p.matcher(html);
        if (m.find()) {
            return m.group(1).trim();
        }
        return "";
    }
    
    private String extractByTag(String html, String tag, String attr, String attrValue) {
        String pattern = String.format("<%s[^>]*%s=\"[^\"]*%s[^\"]*\"[^>]*>([^<]+)", 
            tag, attr, attrValue);
        Pattern p = Pattern.compile(pattern, Pattern.DOTALL);
        Matcher m = p.matcher(html);
        if (m.find()) {
            return m.group(1).trim();
        }
        return "";
    }
    
    private String extractAttribute(String html, String tag, String attribute) {
        String pattern = String.format("<%s[^>]*%s=\"([^\"]+)\"", tag, attribute);
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(html);
        if (m.find()) {
            return m.group(1).trim();
        }
        return "";
    }
    
    private String extractAttributeByClass(String html, String tag, String className, String attribute) {
        String pattern = String.format("<%s[^>]*class=\"[^\"]*%s[^\"]*\"[^>]*%s=\"([^\"]+)\"", 
            tag, className, attribute);
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(html);
        if (m.find()) {
            return m.group(1).trim();
        }
        return "";
    }
    
    private String cleanHTML(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        return html.replaceAll("<[^>]+>", "")
                  .replace("&amp;", "&")
                  .replace("&lt;", "<")
                  .replace("&gt;", ">")
                  .replace("&quot;", "\"")
                  .replace("&#39;", "'")
                  .replace("&nbsp;", " ")
                  .trim();
    }
    
    private static class SearchResult {
        final String title;
        final String url;
        final String snippet;
        
        SearchResult(String title, String url, String snippet) {
            this.title = title;
            this.url = url;
            this.snippet = snippet;
        }
    }
}

