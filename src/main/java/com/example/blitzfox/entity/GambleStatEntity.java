package com.example.blitzfox.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "gamble_stats")
public class GambleStatEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long chatId;
    private int turns;
    private int jackpots;

    public GambleStatEntity() {}

    public GambleStatEntity(Long chatId) {
        this.chatId = chatId;
        this.turns = 0;
        this.jackpots = 0;
    }

    // Getters and setters
    public Long getId() { return id; }
    public Long getChatId() { return chatId; }
    public void setChatId(Long chatId) { this.chatId = chatId; }
    public int getTurns() { return turns; }
    public void setTurns(int turns) { this.turns = turns; }
    public int getJackpots() { return jackpots; }
    public void setJackpots(int jackpots) { this.jackpots = jackpots; }
}