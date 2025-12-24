package com.example.blitzfox.repository;

import com.example.blitzfox.entity.GambleStatEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface GambleStatRepository extends JpaRepository<GambleStatEntity, Long> {
    Optional<GambleStatEntity> findByChatId(Long chatId);
}
