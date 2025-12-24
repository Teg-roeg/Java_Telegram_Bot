package com.example.blitzfox.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
public class UserEntity {

    @Id
    private Long chatId;

    private String firstName;
    private String lastName;
    private String userName;
    private boolean isPremium;

    // New fields for gambling
    private int turns = 0;
    private int jackpots = 0;

    // getters & setters for all fields
    public int getTurns() { return turns; }
    public void setTurns(int turns) { this.turns = turns; }

    public int getJackpots() { return jackpots; }
    public void setJackpots(int jackpots) { this.jackpots = jackpots; }

    // Getters & Setters
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