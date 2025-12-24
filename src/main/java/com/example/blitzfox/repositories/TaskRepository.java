package com.example.blitzfox.repositories;

import com.example.blitzfox.entities.TaskEntity;
import com.example.blitzfox.entities.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TaskRepository extends JpaRepository<TaskEntity, Long> {
    List<TaskEntity> findByUser(UserEntity user);
}
