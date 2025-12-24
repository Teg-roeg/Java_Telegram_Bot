package com.example.blitzfox.repositories;

import com.example.blitzfox.entities.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    UserEntity findByTelegramId(Long telegramId);
}
