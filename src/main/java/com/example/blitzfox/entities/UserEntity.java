package com.example.blitzfox.entities;

import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "users")
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private Long telegramId;

    private String username;
    private String firstName;
    private String lastName;
    private Boolean premium;

    private Integer gamblingTurns = 0;
    private Integer jackpots = 0;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TaskEntity> tasks;

    // Getters and setters
    public Long getId() { return id; }
    public Long getTelegramId() { return telegramId; }
    public void setTelegramId(Long telegramId) { this.telegramId = telegramId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public Boolean getPremium() { return premium; }
    public void setPremium(Boolean premium) { this.premium = premium; }
    public Integer getGamblingTurns() { return gamblingTurns; }
    public void setGamblingTurns(Integer gamblingTurns) { this.gamblingTurns = gamblingTurns; }
    public Integer getJackpots() { return jackpots; }
    public void setJackpots(Integer jackpots) { this.jackpots = jackpots; }
    public List<TaskEntity> getTasks() { return tasks; }
    public void setTasks(List<TaskEntity> tasks) { this.tasks = tasks; }
}
