package com.example.blitzfox;

import com.example.blitzfox.entities.TaskEntity;
import com.example.blitzfox.entities.UserEntity;
import com.example.blitzfox.repositories.TaskRepository;
import com.example.blitzfox.repositories.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class UpdateConsumer implements LongPollingSingleThreadUpdateConsumer {

    @Value("${TELEGRAM_BOT_TOKEN}")
    private String botToken;

    private TelegramClient telegramClient;

    private final UserRepository userRepository;
    private final TaskRepository taskRepository;

    public UpdateConsumer(UserRepository userRepository, TaskRepository taskRepository) {
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
    }

    @PostConstruct
    public void init() {
        this.telegramClient = new OkHttpTelegramClient(botToken);
    }

    @Override
    public void consume(Update update) {
        if (update.hasMessage()) {
            Long chatId = update.getMessage().getChatId();
            String messageText = update.getMessage().getText();
            User from = update.getMessage().getFrom();

            // Ensure user exists
            UserEntity userEntity = userRepository.findByTelegramId(chatId);
            if (userEntity == null) {
                userEntity = new UserEntity();
                userEntity.setTelegramId(chatId);
                userEntity.setFirstName(from.getFirstName());
                userEntity.setLastName(from.getLastName());
                userEntity.setUsername(from.getUserName());
                userEntity.setPremium(from.getIsPremium() != null ? from.getIsPremium() : false);
                userRepository.save(userEntity);
            }

            // Commands
            switch (messageText.split(" ")[0]) {
                case "/start" -> sendMainMenu(chatId);
                case "/help" -> sendHelpMenu(chatId);
                case "/time" -> sendTime(chatId);
                case "/gamb" -> sendRandom(chatId, userEntity);
                case "/myinfo" -> sendMyInfo(chatId, userEntity);
                case "/add" -> {
                    if (messageText.length() > 5) {
                        addTask(chatId, userEntity, messageText.substring(5).trim());
                    }
                }
                case "/tasks" -> listTasks(chatId, userEntity);
                case "/done" -> {
                    if (messageText.length() > 6) markDone(chatId, userEntity, messageText.substring(6).trim());
                }
                case "/delete" -> {
                    if (messageText.length() > 8) deleteTask(chatId, userEntity, messageText.substring(8).trim());
                }
                default -> sendMessage(chatId, "Unknown command. Type /help for commands.");
            }
        } else if (update.hasCallbackQuery()) {
            handleCallback(update.getCallbackQuery());
        }
    }

    private void addTask(Long chatId, UserEntity user, String description) {
        TaskEntity task = new TaskEntity();
        task.setDescription(description);
        task.setCompleted(false);
        task.setUser(user);
        taskRepository.save(task);
        sendMessage(chatId, "✅ Task added: " + description);
    }

    private void listTasks(Long chatId, UserEntity user) {
        List<TaskEntity> tasks = taskRepository.findByUser(user);
        if (tasks.isEmpty()) {
            sendMessage(chatId, "📋 No tasks yet.");
            return;
        }
        StringBuilder sb = new StringBuilder("📋 Your Tasks:\n");
        int i = 1;
        for (TaskEntity t : tasks) {
            sb.append(i).append(". ").append(t.getDescription())
                    .append(t.getCompleted() ? " ✅" : "")
                    .append("\n");
            i++;
        }
        sendMessage(chatId, sb.toString());
    }

    private void markDone(Long chatId, UserEntity user, String indexStr) {
        try {
            int index = Integer.parseInt(indexStr) - 1;
            List<TaskEntity> tasks = taskRepository.findByUser(user);
            if (index >= 0 && index < tasks.size()) {
                TaskEntity task = tasks.get(index);
                task.setCompleted(true);
                taskRepository.save(task);
                sendMessage(chatId, "✅ Task marked as done: " + task.getDescription());
            } else sendMessage(chatId, "Invalid task number.");
        } catch (Exception e) {
            sendMessage(chatId, "Invalid task number.");
        }
    }

    private void deleteTask(Long chatId, UserEntity user, String indexStr) {
        try {
            int index = Integer.parseInt(indexStr) - 1;
            List<TaskEntity> tasks = taskRepository.findByUser(user);
            if (index >= 0 && index < tasks.size()) {
                TaskEntity task = tasks.get(index);
                taskRepository.delete(task);
                sendMessage(chatId, "🗑️ Task deleted: " + task.getDescription());
            } else sendMessage(chatId, "Invalid task number.");
        } catch (Exception e) {
            sendMessage(chatId, "Invalid task number.");
        }
    }

    private void sendRandom(Long chatId, UserEntity user) {
        user.setGamblingTurns(user.getGamblingTurns() + 1);

        String[] symbols = {"🍒", "🍋", "7️⃣"};
        int i1 = ThreadLocalRandom.current().nextInt(symbols.length);
        int i2 = ThreadLocalRandom.current().nextInt(symbols.length);
        int i3 = ThreadLocalRandom.current().nextInt(symbols.length);

        String result = "[" + symbols[i1] + "] [" + symbols[i2] + "] [" + symbols[i3] + "]📍";

        String text = (symbols[i1].equals(symbols[i2]) && symbols[i2].equals(symbols[i3])) ?
                "🎉 JACKPOT!\n\n" + result : "🎰 Result:\n" + result;

        if (symbols[i1].equals(symbols[i2]) && symbols[i2].equals(symbols[i3])) {
            user.setJackpots(user.getJackpots() + 1);
        }
        userRepository.save(user);

        sendMessage(chatId, text + "\n\nTurns played: " + user.getGamblingTurns() + " | Jackpots: " + user.getJackpots());
    }

    private void sendMyInfo(Long chatId, UserEntity user) {
        String premium = user.getPremium() ? "⭐ Premium" : "Not Premium";
        String text = "👤 My Info\n\nName: " + user.getFirstName() + " " + (user.getLastName() != null ? user.getLastName() : "")
                + "\nUsername: " + (user.getUsername() != null ? "@" + user.getUsername() : "Unknown")
                + "\nStatus: " + premium;
        sendMessage(chatId, text);
    }

    private void sendTime(Long chatId) {
        LocalDateTime now = LocalDateTime.now();
        String dayOfWeek = now.format(DateTimeFormatter.ofPattern("EEEE"));
        int day = now.getDayOfMonth();
        String month = now.format(DateTimeFormatter.ofPattern("MMMM"));
        String ordinal = getDayOrdinal(day);
        String time = now.format(DateTimeFormatter.ofPattern("hh:mm a"));

        sendMessage(chatId, "🕔 Time: " + time + "\n📓 Day: " + dayOfWeek + ", " + day + ordinal + " of " + month);
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

    private void sendMainMenu(Long chatId) {
        sendMessage(chatId,
                "👋 Welcome! Commands:\n/start /help /time /myinfo /gamb /add /tasks /done /delete");
    }

    private void sendHelpMenu(Long chatId) {
        sendMessage(chatId,
                "⚙ Help\n\n/start - Start bot\n/time - Current time\n/myinfo - Show info\n/gamb - Gamble\n" +
                        "/add <task> - Add task\n/tasks - List tasks\n/done <i> - Mark done\n/delete <i> - Delete task\n/help - Show this");
    }

    private void handleCallback(CallbackQuery query) {
        // Optional: handle inline buttons here if needed
    }

    private void sendMessage(Long chatId, String text) {
        try {
            telegramClient.execute(SendMessage.builder().chatId(chatId).text(text).build());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
