package com.example.m1nd.service;

import com.example.m1nd.model.Admin;
import com.example.m1nd.util.JsonFileUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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
    
    /**
     * Проверяет, является ли пользователь администратором по username
     */
    public boolean isAdmin(String username) {
        if (username == null || username.isEmpty()) {
            return false;
        }
        
        // Убираем @ если есть
        String cleanUsername = username.startsWith("@") ? username.substring(1) : username;
        
        try {
            List<Admin> admins = jsonFileUtil.readFromFile(adminsFile, Admin.class);
            return admins.stream()
                .anyMatch(admin -> {
                    String adminUsername = admin.getUsername();
                    if (adminUsername == null) return false;
                    String cleanAdminUsername = adminUsername.startsWith("@") 
                        ? adminUsername.substring(1) 
                        : adminUsername;
                    return cleanAdminUsername.equalsIgnoreCase(cleanUsername);
                });
        } catch (IOException e) {
            log.error("Ошибка при проверке администратора", e);
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
