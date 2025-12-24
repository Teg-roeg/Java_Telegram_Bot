package com.example.blitzfox.repository;

import com.example.blitzfox.entity.TaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TaskRepository extends JpaRepository<TaskEntity, Long> {
    List<TaskEntity> findByChatId(Long chatId);
}
