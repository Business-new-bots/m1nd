package com.example.m1nd.service;

import com.example.m1nd.model.Admin;
import com.example.m1nd.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {
    
    private final AdminRepository adminRepository;
    
    @PostConstruct
    public void init() {
        log.info("AdminService инициализирован. Используется БД (PostgreSQL)");
        long count = adminRepository.count();
        log.info("В БД загружено {} администраторов", count);
        if (count == 0) {
            log.warn("Список администраторов в БД пуст! Добавьте администраторов через команду /addadmin");
        } else {
            adminRepository.findAll().forEach(admin -> log.info("Админ в БД: {}", admin.getUsername()));
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
            // Ищем с @ и без @
            boolean withAt = adminRepository.existsByUsername("@" + cleanUsername);
            boolean withoutAt = adminRepository.existsByUsername(cleanUsername);
            
            boolean isAdmin = withAt || withoutAt;
            log.info("Результат проверки админа в БД для {}: {}", cleanUsername, isAdmin);
            return isAdmin;
        } catch (Exception e) {
            log.error("Ошибка при проверке администратора в БД", e);
            return false;
        }
    }
    
    /**
     * Добавляет нового администратора
     */
    @Transactional
    public boolean addAdmin(String username, String addedBy) {
        if (username == null || username.isEmpty()) {
            log.warn("Попытка добавить администратора с пустым username");
            return false;
        }
        
        // Убираем @ если есть
        String cleanUsername = username.startsWith("@") ? username.substring(1) : username;
        
        try {
            // Проверяем, не является ли уже администратором
            boolean alreadyAdmin = adminRepository.existsByUsername("@" + cleanUsername) ||
                                   adminRepository.existsByUsername(cleanUsername);
            
            if (alreadyAdmin) {
                log.info("Пользователь {} уже является администратором в БД", cleanUsername);
                return false;
            }
            
            // Добавляем нового администратора
            Admin newAdmin = new Admin();
            newAdmin.setUsername("@" + cleanUsername);
            newAdmin.setAddedAt(LocalDateTime.now());
            newAdmin.setAddedBy(addedBy != null ? addedBy : "system");
            
            adminRepository.save(newAdmin);
            
            log.info("Добавлен новый администратор в БД: {} (добавил: {})", cleanUsername, addedBy);
            return true;
        } catch (Exception e) {
            log.error("Ошибка при добавлении администратора в БД", e);
            return false;
        }
    }
    
    /**
     * Получает список всех администраторов
     */
    public List<Admin> getAllAdmins() {
        return adminRepository.findAll();
    }
    
    /**
     * Удаляет администратора
     *
     */
    @Transactional
    public boolean removeAdmin(String username) {
        if (username == null || username.isEmpty()) {
            return false;
        }
        
        String cleanUsername = username.startsWith("@") ? username.substring(1) : username;
        
        try {
            // Ищем с @ и без @
            Optional<Admin> adminWithAt = adminRepository.findByUsername("@" + cleanUsername);
            Optional<Admin> adminWithoutAt = adminRepository.findByUsername(cleanUsername);
            
            if (adminWithAt.isPresent()) {
                adminRepository.delete(adminWithAt.get());
                log.info("Удален администратор из БД: {}", cleanUsername);
                return true;
            } else if (adminWithoutAt.isPresent()) {
                adminRepository.delete(adminWithoutAt.get());
                log.info("Удален администратор из БД: {}", cleanUsername);
                return true;
            }
            
            return false;
        } catch (Exception e) {
            log.error("Ошибка при удалении администратора из БД", e);
            return false;
        }
    }
}
