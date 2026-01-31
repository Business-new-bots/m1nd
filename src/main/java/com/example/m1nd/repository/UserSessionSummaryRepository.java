package com.example.m1nd.repository;

import com.example.m1nd.model.UserSessionSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface UserSessionSummaryRepository extends JpaRepository<UserSessionSummary, Long> {
    
    /**
     * Находит все сводки для пользователя за указанную дату
     */
    @Query("SELECT s FROM UserSessionSummary s WHERE s.userId = :userId " +
           "AND DATE(s.createdAt) = :date ORDER BY s.createdAt DESC")
    List<UserSessionSummary> findByUserIdAndDate(@Param("userId") Long userId, 
                                                  @Param("date") LocalDate date);
    
    /**
     * Находит всех уникальных пользователей, у которых есть сводки за указанную дату
     */
    @Query("SELECT DISTINCT s.userId, s.username FROM UserSessionSummary s " +
           "WHERE DATE(s.createdAt) = :date ORDER BY s.username NULLS LAST, s.userId")
    List<Object[]> findDistinctUsersByDate(@Param("date") LocalDate date);
    
    /**
     * Находит все сводки за указанную дату
     */
    @Query("SELECT s FROM UserSessionSummary s WHERE DATE(s.createdAt) = :date " +
           "ORDER BY s.username NULLS LAST, s.createdAt DESC")
    List<UserSessionSummary> findByDate(@Param("date") LocalDate date);
}
