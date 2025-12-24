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

import java.util.ArrayList;

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

    private final Map<Long, List<Task>> userTasks = new ConcurrentHashMap<>();

    static class Task {
        String description;
        boolean completed;

        Task(String desc) {
            this.description = desc;
            this.completed = false;
        }
    }

    @Override
    public void consume(Update update) {
        if (update.hasMessage()) {
            String messageText = update.getMessage().getText();
            Long chatId = update.getMessage().getChatId();

            if (messageText.equals("/start")) {
                sendMainMenu(chatId);
            } else if (messageText.equalsIgnoreCase("/help")) {
                sendHelpMenu(chatId);
            } else if (messageText.equalsIgnoreCase("/time")) {
                sendTime(chatId);
            }
            else if (messageText.equalsIgnoreCase("/gamb")) {
                sendRandom(chatId);
            }
            else if (messageText.equalsIgnoreCase("/myinfo")) {
                sendMyName(chatId, update.getMessage().getFrom());
            }
            else if (messageText.startsWith("/add ")) {
                String taskText = messageText.substring(5);
                addTask(chatId, taskText);
            } else if (messageText.equals("/tasks")) {
                listTasks(chatId);
            } else if (messageText.startsWith("/done ")) {
                markDone(chatId, messageText.substring(6).trim());
            } else if (messageText.startsWith("/delete ")) {
                deleteTask(chatId, messageText.substring(8).trim());
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

        deleteCallbackMessage(callbackQuery);

        switch (data) {
            case "tasks_menu" -> sendTasksMenu(chatId);
            case "help" -> sendHelpMenu(chatId);
            case "time" -> sendTime(chatId);
            case "rnd_number" -> sendRandom(chatId);
            case "my_name" -> sendMyName(chatId, user);
            case "gamble_again" -> sendRandom(chatId);
            case "back" -> sendMainMenuRun(chatId);
            case "check" -> sendStats(chatId);
            case "tasks_add" -> sendMessage(chatId, "\uD83D\uDCDD Task Manager \n\nUse /add <task> to add a new task.");
            case "tasks_list" -> listTasks(chatId);
            default -> sendMessage(chatId, "Unknown command.");
        }
    }

    private void sendHelpMenu(Long chatId) {
        String text;

        text = "⚙\uFE0F Help\n\nHere are the list of commands:\n\n" + "/start - Start bot\n\n" + "/time - Shows current time and date\n\n" +
                "/myinfo - Shows user's name and id\n\n" + "/gamb - Gambling slot\n\n"
                + "/add <task_here> - Add task to list\n" + "/tasks - List all tasks\n" + "/done [ i ] - Selected task marked as Done\n" +
                "/delete [ i ] - Removes task from the task list\n\n"+ "/help - List commands\n";

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

    private void addTask(Long chatId, String description) {
        Task task = new Task(description);
        userTasks.computeIfAbsent(chatId, k -> new ArrayList<>()).add(task);
        sendMessageWithBack(chatId, "✅ Task added: " + description);
    }

    private void listTasks(Long chatId) {
        List<Task> tasks = userTasks.get(chatId);
        if (tasks == null || tasks.isEmpty()) {
            sendMessageWithBack(chatId, "📋 No tasks yet.");
            return;
        }
        StringBuilder sb = new StringBuilder("📋 Your Tasks:\n");
        int i = 1;
        for (Task t : tasks) {
            sb.append(i).append(". ").append(t.description)
                    .append(t.completed ? " ✅" : "")
                    .append("\n");
            i++;
        }
        sendMessageWithBack(chatId, sb.toString());
    }

    private void markDone(Long chatId, String indexStr) {
        try {
            int index = Integer.parseInt(indexStr) - 1;
            List<Task> tasks = userTasks.get(chatId);
            if (tasks != null && index >= 0 && index < tasks.size()) {
                tasks.get(index).completed = true;
                sendMessageWithBack(chatId, "✅ Task marked as done: " + tasks.get(index).description);
            } else sendMessageWithBack(chatId, "Invalid task number.");
        } catch (Exception e) {
            sendMessageWithBack(chatId, "Invalid task number.");
        }
    }

    private void deleteTask(Long chatId, String indexStr) {
        try {
            int index = Integer.parseInt(indexStr) - 1;
            List<Task> tasks = userTasks.get(chatId);
            if (tasks != null && index >= 0 && index < tasks.size()) {
                sendMessageWithBack(chatId, "🗑️ Task deleted: " + tasks.get(index).description);
                tasks.remove(index);
            } else sendMessageWithBack(chatId, "Invalid task number.");
        } catch (Exception e) {
            sendMessageWithBack(chatId, "Invalid task number.");
        }
    }

    private void sendMessageWithBack(Long chatId, String text) {
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
    private void sendTasksMenu(Long chatId) {
        InlineKeyboardButton addBtn = InlineKeyboardButton.builder()
                .text("➕ Add Task")
                .callbackData("tasks_add")
                .build();

        InlineKeyboardButton listBtn = InlineKeyboardButton.builder()
                .text("📋 List Tasks")
                .callbackData("tasks_list")
                .build();

        InlineKeyboardButton backBtn = InlineKeyboardButton.builder()
                .text("◀️ Back")
                .callbackData("back")
                .build();

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(List.of(
                new InlineKeyboardRow(addBtn),
                new InlineKeyboardRow(listBtn),
                new InlineKeyboardRow(backBtn)
        ));

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text("📝 Task Manager \n\nChoose an action:")
                .replyMarkup(markup)
                .build();

        sendMessageWithCleanup(chatId, message);
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
            premium_text = "\n\nNot a Premium User";
        }

        if (user.getLastName() != null && user.getUserName() != null) {
            text = "👤 My Info \n\nYour name is: %s\n\nYour nick: @%s".formatted(user.getFirstName() + " " + user.getLastName(), user.getUserName());
            text += premium_text;
        } else if (user.getUserName() == null) {
            text = "👤 My Info \n\nYour name is: %s\n\nYour nick: %s".formatted(user.getFirstName() + " " + (user.getLastName() != null ? user.getLastName() : ""), "Unknown");
            text += premium_text;
        } else if (user.getLastName() == null) {
            text = "👤 My Info \n\nYour name is: %s\n\nYour nick: @%s".formatted(user.getFirstName(), user.getUserName());
            text += premium_text;
        } else {
            text = "👤 My Info \n\nYour name is: %s\n\nYour nick: %s".formatted(user.getFirstName(), "Unknown");
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
        var button4 = InlineKeyboardButton.builder().text("📝 Tasks").callbackData("tasks_menu").build();
        var button5 = InlineKeyboardButton.builder().text("⚙\uFE0F Help").callbackData("help").build();

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(List.of(
                new InlineKeyboardRow(button1,button2),
                new InlineKeyboardRow(button3,button4),
                new InlineKeyboardRow(button5)
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
        var button4 = InlineKeyboardButton.builder().text("📝 Tasks").callbackData("tasks_menu").build();
        var button5 = InlineKeyboardButton.builder().text("⚙\uFE0F Help").callbackData("help").build();

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(List.of(
                new InlineKeyboardRow(button1,button2),
                new InlineKeyboardRow(button3,button4),
                new InlineKeyboardRow(button5)
        ));

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text("👋 Welcome! Please select action:")
                .replyMarkup(markup)
                .build();

        sendMessageWithCleanup(chatId, message);
    }

    private void deleteCallbackMessage(CallbackQuery callbackQuery) {
        try {
            telegramClient.execute(
                    DeleteMessage.builder()
                            .chatId(callbackQuery.getMessage().getChatId())
                            .messageId(callbackQuery.getMessage().getMessageId())
                            .build()
            );
        } catch (Exception ignored) {}
    }

    private void sendMessageWithCleanup(Long chatId, SendMessage message) {
        /*
        Integer lastMessageId = lastMessageIdMap.get(chatId);
        if (lastMessageId != null) {
            try {
                telegramClient.execute(DeleteMessage.builder()
                        .chatId(chatId)
                        .messageId(lastMessageId)
                        .build());
            } catch (Exception ignored) {}
        }
        */

        try {
            telegramClient.execute(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
