package com.example.blitzfox.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
public class UserEntity {

    @Id
    private Long chatId;

    private String firstName;
    private String lastName;
    private String userName;
    private boolean isPremium;


    private int turns = 0;
    private int jackpots = 0;

    private LocalDateTime firstSeen;
    private LocalDateTime lastSeen;


    public LocalDateTime getFirstSeen() { return firstSeen; }
    public void setFirstSeen(LocalDateTime firstSeen) { this.firstSeen = firstSeen; }

    public LocalDateTime getLastSeen() { return lastSeen; }
    public void setLastSeen(LocalDateTime lastSeen) { this.lastSeen = lastSeen; }

    public int getTurns() { return turns; }
    public void setTurns(int turns) { this.turns = turns; }

    public int getJackpots() { return jackpots; }
    public void setJackpots(int jackpots) { this.jackpots = jackpots; }

    public Long getChatId() { return chatId; }
    public void setChatId(Long chatId) { this.chatId = chatId; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public boolean isPremium() { return isPremium; }
    public void setPremium(boolean premium) { isPremium = premium; }
}