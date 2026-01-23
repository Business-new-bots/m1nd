package com.example.m1nd.service;

import com.example.m1nd.model.Admin;
import com.example.m1nd.util.JsonFileUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {
    
    private final JsonFileUtil jsonFileUtil;
    
    @Value("${app.data.admins-file:admins.json}")
    private String adminsFile;
    
    @PostConstruct
    public void init() {
        log.info("AdminService инициализирован. Файл админов: {}", adminsFile);
        
        // Проверяем абсолютный путь к файлу
        try {
            java.io.File file = new java.io.File(adminsFile);
            log.info("Абсолютный путь к файлу админов: {}", file.getAbsolutePath());
            log.info("Файл существует: {}", file.exists());
            
            // Если файл не существует, создаем его с начальным админом из resources
            if (!file.exists()) {
                log.info("Файл {} не существует, пытаемся создать из resources", adminsFile);
                try {
                    // Создаем директорию, если её нет
                    if (file.getParentFile() != null) {
                        file.getParentFile().mkdirs();
                    }
                    // Пытаемся скопировать из resources
                    java.io.InputStream resourceStream = getClass().getClassLoader()
                        .getResourceAsStream("admins.json");
                    if (resourceStream != null) {
                        String content = new String(resourceStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                        java.nio.file.Files.write(java.nio.file.Paths.get(adminsFile), content.getBytes());
                        log.info("Файл {} создан из resources", adminsFile);
                    } else {
                        // Создаем пустой файл
                        List<Admin> emptyList = new ArrayList<>();
                        jsonFileUtil.writeToFile(adminsFile, emptyList);
                        log.info("Создан пустой файл {}", adminsFile);
                    }
                } catch (Exception e) {
                    log.warn("Не удалось создать файл из resources: {}", e.getMessage());
                    // Создаем пустой файл
                    try {
                        if (file.getParentFile() != null) {
                            file.getParentFile().mkdirs();
                        }
                        List<Admin> emptyList = new ArrayList<>();
                        jsonFileUtil.writeToFile(adminsFile, emptyList);
                        log.info("Создан пустой файл {}", adminsFile);
                    } catch (IOException ioException) {
                        log.error("Не удалось создать файл {}", adminsFile, ioException);
                    }
                }
            } else {
                log.info("Файл {} уже существует, используем существующий файл", adminsFile);
            }
        } catch (Exception e) {
            log.warn("Ошибка при проверке пути к файлу: {}", e.getMessage());
        }
        
        try {
            List<Admin> admins = jsonFileUtil.readFromFile(adminsFile, Admin.class);
            log.info("Загружено {} администраторов", admins.size());
            if (admins.isEmpty()) {
                log.warn("Список администраторов пуст! Файл: {}", adminsFile);
                log.warn("Добавьте администраторов вручную в файл {} или используйте команду /addadmin", adminsFile);
            } else {
                admins.forEach(admin -> log.info("Админ: {}", admin.getUsername()));
            }
        } catch (Exception e) {
            log.warn("Не удалось загрузить админов при инициализации: {}", e.getMessage());
        }
    }
    
    /**
     * Проверяет, является ли пользователь администратором по username
     */
    public boolean isAdmin(String username) {
        if (username == null || username.isEmpty()) {
            log.debug("Username пустой или null");
            return false;
        }
        
        // Убираем @ если есть
        String cleanUsername = username.startsWith("@") ? username.substring(1) : username;
        log.debug("Проверка админа для username: {} (очищенный: {})", username, cleanUsername);
        
        try {
            List<Admin> admins = jsonFileUtil.readFromFile(adminsFile, Admin.class);
            log.debug("Загружено {} администраторов из файла {}", admins.size(), adminsFile);
            
            // Логируем всех админов для отладки
            admins.forEach(admin -> log.debug("Админ в файле: {}", admin.getUsername()));
            
            boolean isAdmin = admins.stream()
                .anyMatch(admin -> {
                    String adminUsername = admin.getUsername();
                    if (adminUsername == null) return false;
                    String cleanAdminUsername = adminUsername.startsWith("@") 
                        ? adminUsername.substring(1) 
                        : adminUsername;
                    boolean matches = cleanAdminUsername.equalsIgnoreCase(cleanUsername);
                    if (matches) {
                        log.info("Найден совпадающий админ: {} == {}", cleanAdminUsername, cleanUsername);
                    }
                    return matches;
                });
            
            log.info("Результат проверки админа для {}: {}", cleanUsername, isAdmin);
            return isAdmin;
        } catch (IOException e) {
            log.error("Ошибка при проверке администратора. Файл: {}", adminsFile, e);
            return false;
        }
    }
    
    /**
     * Добавляет нового администратора
     */
    public boolean addAdmin(String username, String addedBy) {
        if (username == null || username.isEmpty()) {
            log.warn("Попытка добавить администратора с пустым username");
            return false;
        }
        
        // Убираем @ если есть
        String cleanUsername = username.startsWith("@") ? username.substring(1) : username;
        
        try {
            List<Admin> admins = jsonFileUtil.readFromFile(adminsFile, Admin.class);
            
            // Проверяем, не является ли уже администратором
            boolean alreadyAdmin = admins.stream()
                .anyMatch(admin -> {
                    String adminUsername = admin.getUsername();
                    if (adminUsername == null) return false;
                    String cleanAdminUsername = adminUsername.startsWith("@") 
                        ? adminUsername.substring(1) 
                        : adminUsername;
                    return cleanAdminUsername.equalsIgnoreCase(cleanUsername);
                });
            
            if (alreadyAdmin) {
                log.info("Пользователь {} уже является администратором", cleanUsername);
                return false;
            }
            
            // Добавляем нового администратора
            Admin newAdmin = new Admin();
            newAdmin.setUsername("@" + cleanUsername);
            newAdmin.setAddedAt(LocalDateTime.now());
            newAdmin.setAddedBy(addedBy != null ? addedBy : "system");
            
            admins.add(newAdmin);
            jsonFileUtil.writeToFile(adminsFile, admins);
            
            log.info("Добавлен новый администратор: {} (добавил: {})", cleanUsername, addedBy);
            return true;
        } catch (IOException e) {
            log.error("Ошибка при добавлении администратора", e);
            return false;
        }
    }
    
    /**
     * Получает список всех администраторов
     */
    public List<Admin> getAllAdmins() {
        try {
            return jsonFileUtil.readFromFile(adminsFile, Admin.class);
        } catch (IOException e) {
            log.error("Ошибка при получении списка администраторов", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Удаляет администратора (опционально, для будущего использования)
     */
    public boolean removeAdmin(String username) {
        if (username == null || username.isEmpty()) {
            return false;
        }
        
        String cleanUsername = username.startsWith("@") ? username.substring(1) : username;
        
        try {
            List<Admin> admins = jsonFileUtil.readFromFile(adminsFile, Admin.class);
            boolean removed = admins.removeIf(admin -> {
                String adminUsername = admin.getUsername();
                if (adminUsername == null) return false;
                String cleanAdminUsername = adminUsername.startsWith("@") 
                    ? adminUsername.substring(1) 
                    : adminUsername;
                return cleanAdminUsername.equalsIgnoreCase(cleanUsername);
            });
            
            if (removed) {
                jsonFileUtil.writeToFile(adminsFile, admins);
                log.info("Удален администратор: {}", cleanUsername);
            }
            
            return removed;
        } catch (IOException e) {
            log.error("Ошибка при удалении администратора", e);
            return false;
        }
    }
}
