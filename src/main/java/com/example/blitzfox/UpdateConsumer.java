package com.example.blitzfox;

import jakarta.annotation.PostConstruct;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.springframework.beans.factory.annotation.Value;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class UpdateConsumer implements LongPollingSingleThreadUpdateConsumer {

    @Value("${TELEGRAM_BOT_TOKEN}")
    private String botToken;

    private TelegramClient telegramClient;

    @PostConstruct
    public void init() {
        this.telegramClient = new OkHttpTelegramClient(botToken);
    }

    private final Map<Long, GambleStats> statsMap = new ConcurrentHashMap<>();
    static class GambleStats {
        int turns = 0;
        int jackpots = 0;
    }

    private final Map<Long, Integer> lastMessageIdMap = new ConcurrentHashMap<>();

    @Override
    public void consume(Update update) {
        if (update.hasMessage()) {
            String messageText = update.getMessage().getText();
            Long chatId = update.getMessage().getChatId();

            if (messageText.equals("/start")) {
                sendMainMenu(chatId);
            } else {
                sendMessage(chatId, "Sorry, I couldn't understand your message. Try again.");
            }
        } else if (update.hasCallbackQuery()) {
            handleCallbackQuery(update.getCallbackQuery());
        }
    }

    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        var data = callbackQuery.getData();
        var chatId = callbackQuery.getMessage().getChatId();
        var user = callbackQuery.getFrom();

        switch (data) {
            case "time" -> sendTime(chatId);
            case "rnd_number" -> sendRandom(chatId);
            case "my_name" -> sendMyName(chatId, user);
            case "gamble_again" -> sendRandom(chatId);
            case "back" -> sendMainMenuRun(chatId);
            case "check" -> sendStats(chatId);
            default -> sendMessage(chatId, "Unknown command.");
        }
    }

    private void sendMessage(Long chatId, String messageText) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(messageText)
                .build();
        sendMessageWithCleanup(chatId, message);
    }

    private void sendMyName(Long chatId, User user) {
        String text;
        String premium_text;
        if(user.getIsPremium() != null && user.getIsPremium()) {
            premium_text = "\n\nIs Premium User ⭐";
        } else {
            premium_text = "Not a Premium User";
        }

        if (user.getLastName() != null && user.getUserName() != null) {
            text = "👤 My Info \nYour name is: %s\n\nYour nick: @%s".formatted(user.getFirstName() + " " + user.getLastName(), user.getUserName());
            text += premium_text;
        } else if (user.getUserName() == null) {
            text = "👤 My Info \nYour name is: %s\n\nYour nick: %s".formatted(user.getFirstName() + " " + (user.getLastName() != null ? user.getLastName() : ""), "Unknown");
            text += premium_text;
        } else if (user.getLastName() == null) {
            text = "👤 My Info \nYour name is: %s\n\nYour nick: @%s".formatted(user.getFirstName(), user.getUserName());
            text += premium_text;
        } else {
            text = "👤 My Info \nYour name is: %s\n\nYour nick: %s".formatted(user.getFirstName(), "Unknown");
            text += premium_text;
        }

        InlineKeyboardButton backBtn = InlineKeyboardButton.builder()
                .text("◀️ Back")
                .callbackData("back")
                .build();
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(List.of(new InlineKeyboardRow(backBtn)));

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .replyMarkup(markup)
                .build();

        sendMessageWithCleanup(chatId, message);
    }

    private void sendStats(Long chatId) {
        GambleStats stats = statsMap.get(chatId);

        if (stats == null) {
            sendMessage(chatId, "No games played yet.");
            return;
        }

        String text = "📊 Gambling Stats\n\n🎰 Turns played: " + stats.turns + "\n🎉 Jackpots hit: " + stats.jackpots;
        InlineKeyboardButton backBtn = InlineKeyboardButton.builder()
                .text("◀️ Back")
                .callbackData("back")
                .build();
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(List.of(new InlineKeyboardRow(backBtn)));

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .replyMarkup(markup)
                .build();

        sendMessageWithCleanup(chatId, message);
    }

    private void sendRandom(Long chatId) {
        GambleStats stats = statsMap.computeIfAbsent(chatId, id -> new GambleStats());
        stats.turns++;

        String[] symbols = {"🍒", "🍋", "7️⃣"};
        int i1 = ThreadLocalRandom.current().nextInt(symbols.length);
        int i2 = ThreadLocalRandom.current().nextInt(symbols.length);
        int i3 = ThreadLocalRandom.current().nextInt(symbols.length);

        String s1 = symbols[i1];
        String s2 = symbols[i2];
        String s3 = symbols[i3];

        String result = "[" + s1 + "] [" + s2 + "] [" + s3 + "]📍";
        String text = (s1.equals(s2) && s2.equals(s3)) ?
                "🎉 NVCasino Gamble\n\nJACKPOT!\n\n" + result :
                "🎰 NVCasino Gamble\n\n" + result;

        if (s1.equals(s2) && s2.equals(s3)) stats.jackpots++;

        InlineKeyboardButton againBtn = InlineKeyboardButton.builder()
                .text("🔄 Again")
                .callbackData("gamble_again")
                .build();
        InlineKeyboardButton checkBtn = InlineKeyboardButton.builder()
                .text("📝 Check List")
                .callbackData("check")
                .build();
        InlineKeyboardButton backBtn = InlineKeyboardButton.builder()
                .text("◀️ Back")
                .callbackData("back")
                .build();

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(List.of(
                new InlineKeyboardRow(againBtn, checkBtn),
                new InlineKeyboardRow(backBtn)
        ));

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .replyMarkup(markup)
                .build();

        sendMessageWithCleanup(chatId, message);
    }

    private void sendTime(Long chatId) {
        LocalDateTime now = LocalDateTime.now();
        String dayOfWeek = now.format(DateTimeFormatter.ofPattern("EEEE"));
        int dayOfMonth = now.getDayOfMonth();
        String month = now.format(DateTimeFormatter.ofPattern("MMMM"));
        String ordinal = getDayOrdinal(dayOfMonth);
        String time = now.format(DateTimeFormatter.ofPattern("hh:mm a"));

        String text = "🕔 Time: " + time + "\n\n📓 Day: " + dayOfWeek + ", " + dayOfMonth + ordinal + " of " + month;

        InlineKeyboardButton backBtn = InlineKeyboardButton.builder()
                .text("◀️ Back")
                .callbackData("back")
                .build();
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(List.of(new InlineKeyboardRow(backBtn)));

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .replyMarkup(markup)
                .build();

        sendMessageWithCleanup(chatId, message);
    }

    private String getDayOrdinal(int day) {
        if (day >= 11 && day <= 13) return "th";
        return switch (day % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
    }

    private void sendMainMenuRun(Long chatId) {
        var button1 = InlineKeyboardButton.builder().text("🕔 Time").callbackData("time").build();
        var button2 = InlineKeyboardButton.builder().text("🎰 Gambling").callbackData("rnd_number").build();
        var button3 = InlineKeyboardButton.builder().text("👤 My Info").callbackData("my_name").build();

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(List.of(
                new InlineKeyboardRow(button1),
                new InlineKeyboardRow(button2),
                new InlineKeyboardRow(button3)
        ));

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text("\uD83D\uDD3D Please select action:")
                .replyMarkup(markup)
                .build();

        sendMessageWithCleanup(chatId, message);
    }

    private void sendMainMenu(Long chatId) {
        var button1 = InlineKeyboardButton.builder().text("🕔 Time").callbackData("time").build();
        var button2 = InlineKeyboardButton.builder().text("🎰 Gambling").callbackData("rnd_number").build();
        var button3 = InlineKeyboardButton.builder().text("👤 My Info").callbackData("my_name").build();

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(List.of(
                new InlineKeyboardRow(button1),
                new InlineKeyboardRow(button2),
                new InlineKeyboardRow(button3)
        ));

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text("👋 Welcome! Please select action:")
                .replyMarkup(markup)
                .build();

        sendMessageWithCleanup(chatId, message);
    }

    private void sendMessageWithCleanup(Long chatId, SendMessage message) {
        Integer lastMessageId = lastMessageIdMap.get(chatId);

        // Delete last message if exists
        if (lastMessageId != null) {
            try {
                telegramClient.execute(DeleteMessage.builder()
                        .chatId(chatId)
                        .messageId(lastMessageId)
                        .build());
            } catch (Exception ignored) {}
        }

        try {
            var sentMessage = telegramClient.execute(message);
            if (sentMessage != null) lastMessageIdMap.put(chatId, sentMessage.getMessageId());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
