package com.example.blitzfox;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;

@Component
public class MyBotTG implements SpringLongPollingBot {

    private final UpdateConsumer updateConsumer;

    public MyBotTG(UpdateConsumer updateConsumer) {
        this.updateConsumer = updateConsumer;
    }

    @Override
    public String getBotToken() {
        return "8595821322:AAE7mtQZ5CuUXM3gkY0nE5qIE3iKl-q_Uss";
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return updateConsumer;
    }
}
