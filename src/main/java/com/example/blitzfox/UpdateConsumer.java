package com.example.blitzfox;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import com.example.blitzfox.repository.TaskRepository;
import com.example.blitzfox.repository.UserRepository;
import com.example.blitzfox.entity.TaskEntity;
import com.example.blitzfox.entity.UserEntity;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.objects.InputFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;



@Component
public class UpdateConsumer implements LongPollingSingleThreadUpdateConsumer {

    @Value("${admin.chat-id}")
    private Long adminChatId;

    @Value("${TELEGRAM_BOT_TOKEN}")
    private String botToken;

    private TelegramClient telegramClient;

    @PostConstruct
    public void init() {
        this.telegramClient = new OkHttpTelegramClient(botToken);
    }

    private final ConcurrentHashMap<Long, Boolean> waitingForTask = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Long, Integer> lastMessageIdMap = new ConcurrentHashMap<>();

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    @Override
    public void consume(Update update) {

        if (update.hasMessage()) {
            User tgUser = update.getMessage().getFrom();
            Long chatId = update.getMessage().getChatId();

            saveOrUpdateUser(tgUser, chatId);

            String messageText = update.getMessage().getText();

            if (waitingForTask.getOrDefault(chatId, false)) {
                if (messageText == null || messageText.trim().isEmpty()) {
                    sendMessage(chatId, "⚠️ Task cannot be empty. Please send a valid task:");
                    return;
                }
                addTask(chatId, messageText.trim());
                waitingForTask.remove(chatId);
            } else {

                if ("/start".equals(messageText)) sendMainMenu(chatId);
                else if ("/help".equalsIgnoreCase(messageText)) sendHelpMenu(chatId);
                else if (messageText.equals("/export")) exportDatabase(chatId);
                else if ("/time".equalsIgnoreCase(messageText)) sendTime(chatId);
                else if ("/gamb".equalsIgnoreCase(messageText)) sendRandom(chatId);
                else if ("/myinfo".equalsIgnoreCase(messageText)) {
                    UserEntity dbUser = userRepository.findById(chatId)
                            .orElseThrow(() -> new IllegalStateException("User not found"));
                    sendMyName(chatId, dbUser);
                }
                else if ("/add".equalsIgnoreCase(messageText)) {
                    waitingForTask.put(chatId, true);
                    sendMessage(chatId, "✏️ Please send the task text:");
                } else if ("/tasks".equals(messageText)) listTasks(chatId);
                else if (messageText.startsWith("/done ")) markDone(chatId, messageText.substring(6).trim());
                else if (messageText.startsWith("/delete ")) deleteTask(chatId, messageText.substring(8).trim());
                else sendMessage(chatId, "Sorry, I couldn't understand your message.");
            }
        } else if (update.hasCallbackQuery()) {
            CallbackQuery cq = update.getCallbackQuery();
            User tgUser = cq.getFrom();
            Long chatId = cq.getMessage().getChatId();

            saveOrUpdateUser(tgUser, chatId);
            handleCallbackQuery(cq);
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
            case "my_name" -> {
                UserEntity dbUser = userRepository.findById(chatId)
                        .orElseGet(() -> {
                            // fallback empty user if somehow not in DB
                            UserEntity empty = new UserEntity();
                            empty.setChatId(chatId);
                            empty.setFirstName(" ");
                            empty.setLastName(" ");
                            empty.setUserName(" ");
                            empty.setFirstSeen(LocalDateTime.now());
                            empty.setLastSeen(LocalDateTime.now());
                            return empty;
                        });
                sendMyName(chatId, dbUser);
            }
            case "gamble_again" -> sendRandom(chatId);
            case "back" -> sendMainMenuRun(chatId);
            case "check" -> sendStats(chatId);
            case "tasks_add" -> {
                waitingForTask.put(chatId, true);
                sendMessage(chatId, "✏️ Please send the task text:");
            }
            case "tasks_list" -> listTasks(chatId);
            default -> sendMessage(chatId, "Unknown command.");

        }
    }

    private void addTask(Long chatId, String description) {
        TaskEntity task = new TaskEntity();
        task.setChatId(chatId);
        task.setDescription(description);
        task.setCompleted(false);
        taskRepository.save(task);
        sendMessageWithBack(chatId, "✅ Task added: " + description);
    }

    private void listTasks(Long chatId) {
        List<TaskEntity> tasks = taskRepository.findByChatId(chatId);
        if (tasks.isEmpty()) {
            sendMessageWithBack(chatId, "📋 No tasks yet.");
            return;
        }
        StringBuilder sb = new StringBuilder("📋 Your Tasks:\n");
        int i = 1;
        for (TaskEntity t : tasks) {
            sb.append(i).append(". ").append(t.getDescription())
                    .append(t.isCompleted() ? " ✅" : "")
                    .append("\n");
            i++;
        }
        sendMessageWithBack(chatId, sb.toString());
    }

    private void markDone(Long chatId, String indexStr) {
        try {
            int index = Integer.parseInt(indexStr) - 1;
            List<TaskEntity> tasks = taskRepository.findByChatId(chatId);
            if (index >= 0 && index < tasks.size()) {
                TaskEntity task = tasks.get(index);
                task.setCompleted(true);
                taskRepository.save(task);
                sendMessageWithBack(chatId, "✅ Task marked as done: " + task.getDescription());
            } else sendMessageWithBack(chatId, "Invalid task number.");
        } catch (Exception e) {
            sendMessageWithBack(chatId, "Invalid task number.");
        }
    }

    private void deleteTask(Long chatId, String indexStr) {
        try {
            int index = Integer.parseInt(indexStr) - 1;

            List<TaskEntity> tasks = taskRepository.findByChatIdOrderByIdAsc((chatId));

            if (index >= 0 && index < tasks.size()) {
                TaskEntity task = tasks.get(index);
                taskRepository.delete(task);
                sendMessageWithBack(chatId, "🗑️ Task deleted: " + task.getDescription());
            } else {
                sendMessageWithBack(chatId, "Invalid task number.");
            }
        } catch (Exception e) {
            sendMessageWithBack(chatId, "Invalid task number.");
        }
    }

    private void exportDatabase(Long chatId) {

        if (!chatId.equals(adminChatId)) {
            sendMessage(chatId, "⛔ Access denied.");
            return;
        }

        StringBuilder sb = new StringBuilder();

        sb.append("===== USERS =====\n");
        List<UserEntity> users = userRepository.findAll();
        for (UserEntity u : users) {
            sb.append("ChatId: ").append(u.getChatId())
                    .append(" | Name: ").append(u.getFirstName()).append(" ").append(u.getLastName())
                    .append(" | Username: @").append(u.getUserName())
                    .append(" | Premium: ").append(u.isPremium())
                    .append(" | Turns: ").append(u.getTurns())
                    .append(" | Jackpots: ").append(u.getJackpots())
                    .append("\n");
        }

        sb.append("\n===== TASKS =====\n");
        List<TaskEntity> tasks = taskRepository.findAll();
        for (TaskEntity t : tasks) {
            sb.append("ID: ").append(t.getId())
                    .append(" | ChatId: ").append(t.getChatId())
                    .append(" | Task: ").append(t.getDescription())
                    .append(" | Done: ").append(t.isCompleted())
                    .append("\n");
        }

        byte[] fileBytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(fileBytes);

        SendDocument document = SendDocument.builder()
                .chatId(chatId)
                .document(new InputFile(inputStream, "database_export.txt"))
                .caption("📦 Database export")
                .build();

        try {
            telegramClient.execute(document);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendMessage(Long chatId, String messageText) {
        SendMessage message = SendMessage.builder().chatId(chatId).text(messageText).build();
        sendMessageWithCleanup(chatId, message);
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

    private void sendMessageWithCleanup(Long chatId, SendMessage message) {
        try {
            var sentMessage = telegramClient.execute(message);
            if (sentMessage != null) lastMessageIdMap.put(chatId, sentMessage.getMessageId());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void deleteCallbackMessage(CallbackQuery callbackQuery) {
        try {
            telegramClient.execute(DeleteMessage.builder()
                    .chatId(callbackQuery.getMessage().getChatId())
                    .messageId(callbackQuery.getMessage().getMessageId())
                    .build());
        } catch (Exception ignored) {}
    }

    private void sendHelpMenu(Long chatId) {
        String text = "⚙\uFE0F Help\n\nHere are the list of commands:\n\n" +
                "/start - Start bot\n" +
                "/time - Shows current time\n" +
                "/myinfo - Shows user's info\n" +
                "/gamb - Gambling slot\n" +
                "/add <task_here> - Add task\n" +
                "/tasks - List tasks\n" +
                "/done [i] - Mark done\n" +
                "/delete [i] - Delete task\n" +
                "/help - List commands";

        InlineKeyboardButton backBtn = InlineKeyboardButton.builder()
                .text("◀️ Back")
                .callbackData("back")
                .build();

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(List.of(new InlineKeyboardRow(backBtn)));

        SendMessage message = SendMessage.builder().chatId(chatId).text(text).replyMarkup(markup).build();
        sendMessageWithCleanup(chatId, message);
    }

    private void sendMainMenu(Long chatId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(List.of(
                new InlineKeyboardRow(
                        InlineKeyboardButton.builder().text("🕔 Time").callbackData("time").build(),
                        InlineKeyboardButton.builder().text("🎰 Gambling").callbackData("rnd_number").build()
                ),
                new InlineKeyboardRow(
                        InlineKeyboardButton.builder().text("👤 My Info").callbackData("my_name").build(),
                        InlineKeyboardButton.builder().text("📝 Tasks").callbackData("tasks_menu").build()
                ),
                new InlineKeyboardRow(
                        InlineKeyboardButton.builder().text("⚙\uFE0F Help").callbackData("help").build()
                )
        ));

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text("👋 Welcome! Please select action:")
                .replyMarkup(markup)
                .build();
        sendMessageWithCleanup(chatId, message);
    }

    private void sendMainMenuRun(Long chatId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(List.of(
                new InlineKeyboardRow(
                        InlineKeyboardButton.builder().text("🕔 Time").callbackData("time").build(),
                        InlineKeyboardButton.builder().text("🎰 Gambling").callbackData("rnd_number").build()
                ),
                new InlineKeyboardRow(
                        InlineKeyboardButton.builder().text("👤 My Info").callbackData("my_name").build(),
                        InlineKeyboardButton.builder().text("📝 Tasks").callbackData("tasks_menu").build()
                ),
                new InlineKeyboardRow(
                        InlineKeyboardButton.builder().text("⚙\uFE0F Help").callbackData("help").build()
                )
        ));

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text("\uD83D\uDD3D Please select action:")
                .replyMarkup(markup)
                .build();
        sendMessageWithCleanup(chatId, message);
    }

    private void sendTasksMenu(Long chatId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(List.of(
                new InlineKeyboardRow(InlineKeyboardButton.builder().text("➕ Add Task").callbackData("tasks_add").build()),
                new InlineKeyboardRow(InlineKeyboardButton.builder().text("📋 List Tasks").callbackData("tasks_list").build()),
                new InlineKeyboardRow(InlineKeyboardButton.builder().text("◀️ Back").callbackData("back").build())
        ));

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text("📝 Task Manager \n\nChoose an action:")
                .replyMarkup(markup)
                .build();
        sendMessageWithCleanup(chatId, message);
    }

    private UserEntity saveOrUpdateUser(User tgUser, Long chatId) {
        return userRepository.findById(chatId)
                .map(user -> {
                    user.setFirstName(tgUser.getFirstName() != null ? tgUser.getFirstName() : " ");
                    user.setLastName(tgUser.getLastName() != null ? tgUser.getLastName() : " ");
                    user.setUserName(tgUser.getUserName() != null ? tgUser.getUserName() : " ");
                    user.setPremium(Boolean.TRUE.equals(tgUser.getIsPremium()));
                    user.setLastSeen(LocalDateTime.now());
                    return userRepository.save(user);
                })
                .orElseGet(() -> {
                    UserEntity user = new UserEntity();
                    user.setChatId(chatId);
                    user.setFirstName(tgUser.getFirstName() != null ? tgUser.getFirstName() : " ");
                    user.setLastName(tgUser.getLastName() != null ? tgUser.getLastName() : " ");
                    user.setUserName(tgUser.getUserName() != null ? tgUser.getUserName() : " ");
                    user.setPremium(Boolean.TRUE.equals(tgUser.getIsPremium()));
                    user.setTurns(0);
                    user.setJackpots(0);
                    LocalDateTime now = LocalDateTime.now();
                    user.setFirstSeen(now);
                    user.setLastSeen(now);
                    return userRepository.save(user);
                });
    }

    private void sendMyName(Long chatId, UserEntity user) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

        String firstName = user.getFirstName() != null ? user.getFirstName() : " ";
        String lastName = user.getLastName() != null ? user.getLastName() : " ";
        String username = user.getUserName() != null ? user.getUserName() : " ";

        String firstSeen = user.getFirstSeen() != null ? user.getFirstSeen().format(fmt) : "-";
        String lastSeen = user.getLastSeen() != null ? user.getLastSeen().format(fmt) : "-";

        String text =
                "👤 My Info\n\n" +
                        "Name: " + firstName + " " + lastName + "\n" +
                        "Username: @" + username + "\n" +
                        "Premium: " + (user.isPremium() ? "⭐ Yes" : "No") + "\n\n" +
                        "🎰 Gambling Stats\n" +
                        "Turns: " + user.getTurns() + "\n" +
                        "Jackpots: " + user.getJackpots() + "\n\n" +
                        "⏰ Activity\n" +
                        "First seen: " + firstSeen + "\n" +
                        "Last seen: " + lastSeen;

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
        UserEntity user = userRepository.findById(chatId)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        String text = "📊 Gambling Stats\n\n🎰 Turns played: " + user.getTurns() +
                "\n🎉 Jackpots hit: " + user.getJackpots();

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
        UserEntity user = userRepository.findById(chatId)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        user.setTurns(user.getTurns() + 1);

        String[] symbols = {"🍒", "🍋", "7️⃣"};
        int i1 = ThreadLocalRandom.current().nextInt(symbols.length);
        int i2 = ThreadLocalRandom.current().nextInt(symbols.length);
        int i3 = ThreadLocalRandom.current().nextInt(symbols.length);

        String s1 = symbols[i1];
        String s2 = symbols[i2];
        String s3 = symbols[i3];

        String result = "[" + s1 + "] [" + s2 + "] [" + s3 + "]📍";
        String text = (s1.equals(s2) && s2.equals(s3)) ? "🎉 NVCasino Gamble\n\nJACKPOT!\n\n" + result
                : "🎰 NVCasino Gamble\n\n" + result;

        if (s1.equals(s2) && s2.equals(s3)) {
            user.setJackpots(user.getJackpots() + 1);
        }

        userRepository.save(user);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(List.of(
                new InlineKeyboardRow(
                        InlineKeyboardButton.builder().text("🔄 Again").callbackData("gamble_again").build(),
                        InlineKeyboardButton.builder().text("📝 Check Stats").callbackData("check").build()
                ),
                new InlineKeyboardRow(
                        InlineKeyboardButton.builder().text("◀️ Back").callbackData("back").build()
                )
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

        InlineKeyboardButton backBtn = InlineKeyboardButton.builder().text("◀️ Back").callbackData("back").build();
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(List.of(new InlineKeyboardRow(backBtn)));

        SendMessage message = SendMessage.builder().chatId(chatId).text(text).replyMarkup(markup).build();
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
}
