package com.example.blitzfox;

import lombok.SneakyThrows;
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
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;



@Component
public class UpdateConsumer implements LongPollingSingleThreadUpdateConsumer {

    private final Map<Long, GambleStats> statsMap = new ConcurrentHashMap<>();
    static class GambleStats {
        int turns = 0;
        int jackpots = 0;
    }

    private final TelegramClient telegramClient;

    public UpdateConsumer() {
        this.telegramClient = new OkHttpTelegramClient("8595821322:AAE7mtQZ5CuUXM3gkY0nE5qIE3iKl-q_Uss");
    }

    @SneakyThrows
    @Override
    public void consume(Update update) {
        if (update.hasMessage()) {
            String messageText = update.getMessage().getText();
            Long chatId = update.getMessage().getChatId();

            if(messageText.equals("/start")) {
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
        var chatId = callbackQuery.getFrom().getId();
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


    @SneakyThrows
    private void sendMessage(Long chatId, String messageText) {

        SendMessage message = SendMessage.builder()
                .text(messageText)
                .chatId(chatId)
                .build();

        sendMessageWithCleanup(chatId, message);

    }
    String text;
    private void sendMyName(Long chatId, User user) {
        if(user.getLastName() != null && user.getUserName() != null) {
            text = "\uD83D\uDC64 Your name is: %s\nYour nick: @%s"
                    .formatted(user.getFirstName() + " " + user.getLastName() , user.getUserName());
        } else if (user.getUserName() == null) {
            text = "\uD83D\uDC64 My Info \nYour name is: %s\nYour nick: %s"
                    .formatted(user.getFirstName() + " " + user.getLastName(),
                            "Unknown"
                    );
        } else if (user.getLastName() == null) {
            text = "\uD83D\uDC64 My Info \nYour name is: %s\nYour nick: @%s"
                    .formatted(user.getFirstName(),
                            user.getUserName()
                    );
        } else {
            text = "\uD83D\uDC64 My Info \nYour name is: %s\nYour nick: %s"
                    .formatted(user.getFirstName(),
                            "Unknown"
                    );
        }

        InlineKeyboardButton backBtn = InlineKeyboardButton.builder()
                .text("◀\uFE0F Back")
                .callbackData("back")
                .build();

        List<InlineKeyboardRow> rows = List.of(
                new InlineKeyboardRow(backBtn)
        );

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(rows);

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .replyMarkup(markup)
                .build();

        sendMessageWithCleanup(chatId, message);

    }

    @SneakyThrows
    private void sendStats(Long chatId) {
        GambleStats stats = statsMap.get(chatId);

        if (stats == null) {
            sendMessage(chatId, "No games played yet.");
            return;
        }

        String text =
                "\uD83D\uDCCA Gambling Stats\n\n" +
                        "\uD83C\uDFB0 Turns played: " + stats.turns + "\n" +
                        "\uD83C\uDF89 Jackpots hit: " + stats.jackpots;

        InlineKeyboardButton backBtn = InlineKeyboardButton.builder()
                .text("◀\uFE0F Back")
                .callbackData("back")
                .build();

        List<InlineKeyboardRow> rows = List.of(
                new InlineKeyboardRow(backBtn)
        );

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(rows);

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .replyMarkup(markup)
                .build();

        sendMessageWithCleanup(chatId, message);
    }

    @SneakyThrows
    private void sendRandom(Long chatId) {
        GambleStats stats = statsMap.computeIfAbsent(chatId, id -> new GambleStats()); stats.turns++;

        String[] symbols = {"\uD83C\uDF52", "\uD83C\uDF4B", "7\uFE0F⃣"};

        int i1 = ThreadLocalRandom.current().nextInt(symbols.length);
        int i2 = ThreadLocalRandom.current().nextInt(symbols.length);
        int i3 = ThreadLocalRandom.current().nextInt(symbols.length);

        String s1 = symbols[i1];
        String s2 = symbols[i2];
        String s3 = symbols[i3];

        String result = "[" + s1 + "] " + "[" + s2 + "] " + "[" + s3 + "]" + "📍";


        String text;
        if (s1.equals(s2) && s2.equals(s3)) {
            text = "\uD83C\uDF89 NVCasino Gamble\n\n JACKPOT!\n\n " + result;
            stats.jackpots++;
        } else {
            text = "\uD83C\uDFB0 NVCasino Gamble\n\n " + result;
        }

        InlineKeyboardButton againBtn = InlineKeyboardButton.builder()
                .text("\uD83D\uDD04 Again")
                .callbackData("gamble_again")
                .build();

        InlineKeyboardButton checkBtn = InlineKeyboardButton.builder()
                .text("\uD83D\uDCDD Check List")
                .callbackData("check")
                .build();

        InlineKeyboardButton backBtn = InlineKeyboardButton.builder()
                .text("◀\uFE0F Back")
                .callbackData("back")
                .build();

        List<InlineKeyboardRow> rows = List.of(
                new InlineKeyboardRow(againBtn, checkBtn),
                new InlineKeyboardRow(backBtn)
        );

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(rows);

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .replyMarkup(markup)
                .build();
        sendMessageWithCleanup(chatId, message);
    }

    @SneakyThrows
    private void sendTime(Long chatId) {
        LocalDateTime now = LocalDateTime.now();

        String dayOfWeek = now.format(DateTimeFormatter.ofPattern("EEEE"));
        int dayOfMonth = now.getDayOfMonth();
        String month = now.format(DateTimeFormatter.ofPattern("MMMM"));
        String ordinal = getDayOrdinal(dayOfMonth);

        String time = now.format(DateTimeFormatter.ofPattern("hh:mm a"));

        String text =
                "\uD83D\uDDD3 Day: " + dayOfWeek + ", " + dayOfMonth + ordinal + " of " + month + "\n\n" +
                        "\uD83D\uDD54 Time: " + time;

        InlineKeyboardButton backBtn = InlineKeyboardButton.builder()
                .text("◀\uFE0F Back")
                .callbackData("back")
                .build();

        List<InlineKeyboardRow> rows = List.of(
                new InlineKeyboardRow(backBtn)
        );

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(rows);

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .replyMarkup(markup)
                .build();

        sendMessageWithCleanup(chatId, message);

    }

    private String getDayOrdinal(int day) {
        if (day >= 11 && day <= 13) {
            return "th";
        }
        return switch (day % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
    }

    private final Map<Long, List<Integer>> allMessageIdsMap = new ConcurrentHashMap<>();

    @SneakyThrows
    private void sendMessageWithCleanup(Long chatId, SendMessage message) {
        // Delete all previous messages
        List<Integer> previousIds = allMessageIdsMap.get(chatId);
        if (previousIds != null) {
            for (Integer messageId : previousIds) {
                try {
                    telegramClient.execute(DeleteMessage.builder()
                            .chatId(chatId)
                            .messageId(messageId)
                            .build());
                } catch (Exception ignored) {} // ignore if already deleted
            }
        }

        // Send new message
        var sentMessage = telegramClient.execute(message);

        // Save new message ID in the map
        allMessageIdsMap.put(chatId, List.of(sentMessage.getMessageId()));
    }


    @SneakyThrows
    private void sendMainMenuRun(Long chatId) {

        var button1 = InlineKeyboardButton.builder()
                .text("\uD83D\uDD54 Time")
                .callbackData("time")
                .build();

        var button2 = InlineKeyboardButton.builder()
                .text("\uD83C\uDFB0 Gambling")
                .callbackData("rnd_number")
                .build();

        var button3 = InlineKeyboardButton.builder()
                .text("\uD83D\uDC64 My Info")
                .callbackData("my_name")
                .build();

        List<InlineKeyboardRow> keyboardRows = List.of(
                new InlineKeyboardRow(button1),
                new InlineKeyboardRow(button2),
                new InlineKeyboardRow(button3)
        );

        new InlineKeyboardRow();

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(keyboardRows);
        SendMessage message = SendMessage.builder()
                .text("\uD83D\uDD3D Please select action:")
                .chatId(chatId)
                .build();

        message.setReplyMarkup(markup);

        sendMessageWithCleanup(chatId, message);

    }


    @SneakyThrows
    private void sendMainMenu(Long chatId) {

        var button1 = InlineKeyboardButton.builder()
                .text("\uD83D\uDD54 Time")
                .callbackData("time")
                .build();

        var button2 = InlineKeyboardButton.builder()
                .text("\uD83C\uDFB0 Gambling")
                .callbackData("rnd_number")
                .build();

        var button3 = InlineKeyboardButton.builder()
                .text("\uD83D\uDC64 My Info")
                .callbackData("my_name")
                .build();

        List<InlineKeyboardRow> keyboardRows = List.of(
                new InlineKeyboardRow(button1),
                new InlineKeyboardRow(button2),
                new InlineKeyboardRow(button3)
        );

        new InlineKeyboardRow();

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(keyboardRows);

        SendMessage message = SendMessage.builder()
                .text("\uD83D\uDC4B Welcome! Please select action:")
                .chatId(chatId)
                .build();
        message.setReplyMarkup(markup);
        sendMessageWithCleanup(chatId, message);
    }
}
