package com.example.m1nd.bot;

import com.example.m1nd.model.FactTopic;
import com.example.m1nd.model.Task;
import com.example.m1nd.service.AdminService;
import com.example.m1nd.service.FeedbackService;
import com.example.m1nd.service.StatisticsService;
import com.example.m1nd.service.SummaryService;
import com.example.m1nd.service.TaskService;
import com.example.m1nd.service.UserService;
import com.example.m1nd.service.FactTopicService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AdminMenuService {

    private static final Logger log = LoggerFactory.getLogger(AdminMenuService.class);

    private final AdminService adminService;
    private final UserService userService;
    private final FeedbackService feedbackService;
    private final StatisticsService statisticsService;
    private final SummaryService summaryService;
    private final FactTopicService factTopicService;
    private final TaskService taskService;

    private final Map<Long, Boolean> waitingForAdminUsername = new ConcurrentHashMap<>();
    private final Map<Long, Boolean> waitingForRemoveAdminUsername = new ConcurrentHashMap<>();

    private enum AdminEditorState {
        ADD_FACT_TOPIC,
        ADD_TASK
    }

    private final Map<Long, AdminEditorState> adminEditorState = new ConcurrentHashMap<>();

    public InlineKeyboardMarkup createAdminKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();

        InlineKeyboardButton statsButton = new InlineKeyboardButton();
        statsButton.setText("📊 Статистика");
        statsButton.setCallbackData("stats");

        InlineKeyboardButton adminButton = new InlineKeyboardButton();
        adminButton.setText("👤 Админ");
        adminButton.setCallbackData("admin_menu");

        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(statsButton);
        row.add(adminButton);

        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        keyboard.add(row);

        markup.setKeyboard(keyboard);
        return markup;
    }

    public InlineKeyboardMarkup createAdminMenuKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();

        InlineKeyboardButton addAdminButton = new InlineKeyboardButton();
        addAdminButton.setText("➕ Добавить админа");
        addAdminButton.setCallbackData("add_admin_prompt");

        InlineKeyboardButton listAdminsButton = new InlineKeyboardButton();
        listAdminsButton.setText("📋 Список админов");
        listAdminsButton.setCallbackData("list_admins");

        InlineKeyboardButton removeAdminButton = new InlineKeyboardButton();
        removeAdminButton.setText("➖ Удалить админа");
        removeAdminButton.setCallbackData("remove_admin_prompt");

        InlineKeyboardButton feedbacksButton = new InlineKeyboardButton();
        feedbacksButton.setText("📝 Опросы");
        feedbacksButton.setCallbackData("view_feedbacks");

        InlineKeyboardButton activityButton = new InlineKeyboardButton();
        activityButton.setText("📈 Активность");
        activityButton.setCallbackData("admin_activity");

        InlineKeyboardButton menuEditorButton = new InlineKeyboardButton();
        menuEditorButton.setText("🛠 Редактор меню");
        menuEditorButton.setCallbackData("admin_menu_editor");

        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("◀️ Назад");
        backButton.setCallbackData("back_to_main");

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(addAdminButton);

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(listAdminsButton);

        List<InlineKeyboardButton> row3 = new ArrayList<>();
        row3.add(removeAdminButton);

        List<InlineKeyboardButton> row4 = new ArrayList<>();
        row4.add(feedbacksButton);

        List<InlineKeyboardButton> row5 = new ArrayList<>();
        row5.add(activityButton);

        List<InlineKeyboardButton> row6 = new ArrayList<>();
        row6.add(menuEditorButton);

        List<InlineKeyboardButton> row7 = new ArrayList<>();
        row7.add(backButton);

        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        keyboard.add(row1);
        keyboard.add(row2);
        keyboard.add(row3);
        keyboard.add(row4);
        keyboard.add(row5);
        keyboard.add(row6);
        keyboard.add(row7);

        markup.setKeyboard(keyboard);
        return markup;
    }

    public InlineKeyboardMarkup createMenuEditorKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton addFactTopic = new InlineKeyboardButton();
        addFactTopic.setText("➕ Добавить тему фактов");
        addFactTopic.setCallbackData("menu_add_fact_topic");
        row1.add(addFactTopic);

        InlineKeyboardButton deleteFactTopic = new InlineKeyboardButton();
        deleteFactTopic.setText("➖ Удалить тему фактов");
        deleteFactTopic.setCallbackData("menu_delete_fact_topic");
        row1.add(deleteFactTopic);

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton addTask = new InlineKeyboardButton();
        addTask.setText("➕ Добавить задание");
        addTask.setCallbackData("menu_add_task");
        row2.add(addTask);

        InlineKeyboardButton deleteTask = new InlineKeyboardButton();
        deleteTask.setText("➖ Удалить задание");
        deleteTask.setCallbackData("menu_delete_task");
        row2.add(deleteTask);

        List<InlineKeyboardButton> row3 = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("◀️ Назад");
        backButton.setCallbackData("admin_menu");
        row3.add(backButton);

        keyboard.add(row1);
        keyboard.add(row2);
        keyboard.add(row3);
        markup.setKeyboard(keyboard);

        return markup;
    }

    public boolean canHandleCallback(String data) {
        return data != null && (
            "stats".equals(data)
                || "admin_menu".equals(data)
                || "back_to_main".equals(data)
                || "add_admin_prompt".equals(data)
                || "list_admins".equals(data)
                || "view_feedbacks".equals(data)
                || "admin_activity".equals(data)
                || data.startsWith("activity_date:")
                || data.startsWith("activity_user:")
                || "remove_admin_prompt".equals(data)
                || data.startsWith("add_admin:")
                || "admin_menu_editor".equals(data)
                || "menu_add_fact_topic".equals(data)
                || "menu_add_task".equals(data)
                || "menu_delete_fact_topic".equals(data)
                || data.startsWith("menu_delete_fact_topic_confirm:")
                || "menu_delete_task".equals(data)
                || data.startsWith("menu_delete_task_confirm:")
        );
    }

    public AdminMenuResult handleCallback(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        Long userId = callbackQuery.getFrom().getId();
        String username = callbackQuery.getFrom().getUserName();
        Long chatId = callbackQuery.getMessage().getChatId();

        List<SendMessage> messages = new ArrayList<>();
        String callbackAnswer = "✅";

        if (username == null || !adminService.isAdmin(username)) {
            callbackAnswer = "❌ У вас нет доступа к этой функции.";
            return new AdminMenuResult(messages, callbackAnswer);
        }

        if ("stats".equals(data)) {
            userService.trackUserActivity(userId);
            messages.addAll(buildStatisticsMessages(chatId));
            callbackAnswer = "✅ Статистика отправлена";
        } else if ("admin_menu".equals(data)) {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("👤 Меню администратора\n\nВыберите действие:");
            message.setReplyMarkup(createAdminMenuKeyboard());
            messages.add(message);
            callbackAnswer = "✅ Меню открыто";
        } else if ("back_to_main".equals(data)) {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("Главное меню");
            message.setReplyMarkup(createAdminKeyboard());
            messages.add(message);
            callbackAnswer = "✅";
        } else if ("add_admin_prompt".equals(data)) {
            waitingForAdminUsername.put(userId, true);

            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("📝 Отправьте username пользователя, которого хотите добавить как администратора.\n\n" +
                "Формат: @username или просто username\n\n" +
                "Пример: @puh2012 или puh2012");
            message.setReplyMarkup(createAdminMenuKeyboard());
            messages.add(message);
            callbackAnswer = "✅ Введите username";
        } else if ("list_admins".equals(data)) {
            messages.add(buildListAdminsMessage(chatId));
            callbackAnswer = "✅ Список отправлен";
        } else if ("view_feedbacks".equals(data)) {
            messages.addAll(buildFeedbacksMessages(chatId));
            callbackAnswer = "✅ Опросы отправлены";
        } else if ("admin_activity".equals(data)) {
            messages.add(buildActivityDateSelectionMessage(chatId));
            callbackAnswer = "✅ Выберите дату";
        } else if (data != null && data.startsWith("activity_date:")) {
            String dateStr = data.substring("activity_date:".length());
            messages.addAll(buildActivityUsersForDateMessages(chatId, dateStr));
            callbackAnswer = "✅";
        } else if (data != null && data.startsWith("activity_user:")) {
            String[] parts = data.substring("activity_user:".length()).split(":");
            if (parts.length == 2) {
                String dateStr = parts[0];
                Long targetUserId = Long.parseLong(parts[1]);
                messages.addAll(buildActivityUserSummariesMessages(chatId, dateStr, targetUserId));
                callbackAnswer = "✅";
            }
        } else if ("remove_admin_prompt".equals(data)) {
            waitingForRemoveAdminUsername.put(userId, true);

            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("📝 Отправьте username администратора, которого хотите удалить.\n\n" +
                "Формат: @username или просто username\n\n" +
                "Пример: @puh2012 или puh2012");
            message.setReplyMarkup(createAdminMenuKeyboard());
            messages.add(message);
            callbackAnswer = "✅ Введите username";
        } else if (data != null && data.startsWith("add_admin:")) {
            String targetUsername = data.substring("add_admin:".length());
            messages.add(buildAddAdminCallbackMessage(chatId, username, targetUsername));
            callbackAnswer = "✅ Администратор добавлен";
        } else if ("admin_menu_editor".equals(data)) {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("🛠 Редактор главного меню\n\nВыбери действие:");
            message.setReplyMarkup(createMenuEditorKeyboard());
            messages.add(message);
            callbackAnswer = "✅ Редактор меню";
        } else if ("menu_add_fact_topic".equals(data)) {
            adminEditorState.put(userId, AdminEditorState.ADD_FACT_TOPIC);

            SendMessage msg = new SendMessage();
            msg.setChatId(chatId.toString());
            msg.setText("🧠 Введите название новой темы фактов.\n\nНапример: \"Факты о мозге\".");
            msg.setReplyMarkup(createMenuEditorKeyboard());
            messages.add(msg);
            callbackAnswer = "✅ Введите название темы";
        } else if ("menu_add_task".equals(data)) {
            adminEditorState.put(userId, AdminEditorState.ADD_TASK);

            SendMessage msg = new SendMessage();
            msg.setChatId(chatId.toString());
            msg.setText("🎯 Введите текст задания одной строкой.");
            msg.setReplyMarkup(createMenuEditorKeyboard());
            messages.add(msg);
            callbackAnswer = "✅ Введите текст задания";
        } else if ("menu_delete_fact_topic".equals(data)) {
            messages.add(buildDeleteFactTopicsMessage(chatId));
            callbackAnswer = "✅";
        } else if (data != null && data.startsWith("menu_delete_fact_topic_confirm:")) {
            Long topicId = Long.parseLong(data.substring("menu_delete_fact_topic_confirm:".length()));
            factTopicService.deleteById(topicId);

            SendMessage msg = new SendMessage();
            msg.setChatId(chatId.toString());
            msg.setText("✅ Тема фактов удалена.");
            msg.setReplyMarkup(createMenuEditorKeyboard());
            messages.add(msg);
            callbackAnswer = "✅ Удалено";
        } else if ("menu_delete_task".equals(data)) {
            messages.add(buildDeleteTasksMessage(chatId));
            callbackAnswer = "✅";
        } else if (data != null && data.startsWith("menu_delete_task_confirm:")) {
            Long taskId = Long.parseLong(data.substring("menu_delete_task_confirm:".length()));
            taskService.deleteTask(taskId);

            SendMessage msg = new SendMessage();
            msg.setChatId(chatId.toString());
            msg.setText("✅ Задание удалено.");
            msg.setReplyMarkup(createMenuEditorKeyboard());
            messages.add(msg);
            callbackAnswer = "✅ Удалено";
        } else {
            callbackAnswer = "❌ Неизвестная команда";
        }

        return new AdminMenuResult(messages, callbackAnswer);
    }

    public AdminTextResult handleAdminText(Update update, String messageText) {
        if (update.getMessage() == null) {
            return AdminTextResult.notHandled();
        }

        Long userId = update.getMessage().getFrom().getId();
        String username = update.getMessage().getFrom().getUserName();
        Long chatId = update.getMessage().getChatId();

        if (username == null || !adminService.isAdmin(username)) {
            return AdminTextResult.notHandled();
        }

        List<SendMessage> messages = new ArrayList<>();

        if (waitingForAdminUsername.getOrDefault(userId, false)) {
            messages.add(buildAddAdminUsernameMessage(chatId, username, messageText));
            waitingForAdminUsername.remove(userId);
            return AdminTextResult.handled(messages);
        }

        if (waitingForRemoveAdminUsername.getOrDefault(userId, false)) {
            messages.add(buildRemoveAdminUsernameMessage(chatId, username, messageText));
            waitingForRemoveAdminUsername.remove(userId);
            return AdminTextResult.handled(messages);
        }

        if (adminEditorState.containsKey(userId)) {
            AdminEditorState state = adminEditorState.get(userId);
            switch (state) {
                case ADD_FACT_TOPIC -> {
                    factTopicService.createFromTitle(messageText.trim(), userId);
                    adminEditorState.remove(userId);

                    SendMessage msg = new SendMessage();
                    msg.setChatId(chatId.toString());
                    msg.setText("✅ Тема фактов добавлена: " + messageText.trim());
                    msg.setReplyMarkup(createAdminMenuKeyboard());
                    messages.add(msg);
                }
                case ADD_TASK -> {
                    taskService.createTask(messageText.trim(), "daily", userId);
                    adminEditorState.remove(userId);

                    SendMessage msg = new SendMessage();
                    msg.setChatId(chatId.toString());
                    msg.setText("✅ Задание добавлено.");
                    msg.setReplyMarkup(createAdminMenuKeyboard());
                    messages.add(msg);
                }
            }
            return AdminTextResult.handled(messages);
        }

        return AdminTextResult.notHandled();
    }

    public List<SendMessage> buildStatisticsMessages(Long chatId) {
        String statistics = statisticsService.formatStatistics();
        final int MAX_LENGTH = 4000;

        List<SendMessage> result = new ArrayList<>();

        if (statistics.length() <= MAX_LENGTH) {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("```\n" + statistics + "\n```");
            message.setParseMode("Markdown");
            message.setReplyMarkup(createAdminKeyboard());
            result.add(message);
            return result;
        }

        int offset = 0;
        while (offset < statistics.length()) {
            int endIndex = Math.min(offset + MAX_LENGTH, statistics.length());
            String part = statistics.substring(offset, endIndex);

            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("```\n" + part + "\n```");
            message.setParseMode("Markdown");

            if (endIndex >= statistics.length()) {
                message.setReplyMarkup(createAdminKeyboard());
            }

            result.add(message);
            offset = endIndex;
        }

        return result;
    }

    private SendMessage buildListAdminsMessage(Long chatId) {
        List<com.example.m1nd.model.Admin> admins = adminService.getAllAdmins();

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());

        if (admins.isEmpty()) {
            message.setText("📋 Список администраторов пуст.");
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("📋 Список администраторов (").append(admins.size()).append("):\n\n");

            for (int i = 0; i < admins.size(); i++) {
                com.example.m1nd.model.Admin admin = admins.get(i);
                sb.append(i + 1).append(". ").append(admin.getUsername());
                if (admin.getAddedAt() != null) {
                    sb.append("\n   Добавлен: ").append(admin.getAddedAt().toLocalDate());
                }
                if (admin.getAddedBy() != null && !admin.getAddedBy().equals("system")) {
                    sb.append("\n   Добавил: ").append(admin.getAddedBy());
                }
                sb.append("\n\n");
            }

            message.setText(sb.toString());
        }

        message.setReplyMarkup(createAdminMenuKeyboard());
        return message;
    }

    private List<SendMessage> buildFeedbacksMessages(Long chatId) {
        List<com.example.m1nd.model.Feedback> feedbacks = feedbackService.getRecentFeedbacks(30);
        List<SendMessage> result = new ArrayList<>();

        if (feedbacks.isEmpty()) {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("📝 Опросов пока нет.");
            message.setReplyMarkup(createAdminMenuKeyboard());
            result.add(message);
            return result;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📝 Опросы пользователей (последние 30 дней):\n\n");

        for (com.example.m1nd.model.Feedback feedback : feedbacks) {
            sb.append("👤 ").append(feedback.getUsername() != null ? feedback.getUsername() : feedback.getFirstName())
              .append(" (").append(feedback.getUserId()).append(")\n");

            if (feedback.getRating() != null) {
                sb.append("⭐ Оценка: ").append(feedback.getRating()).append("/10\n");
            }

            if (feedback.getWasUseful() != null) {
                sb.append("💡 Полезно: ").append(feedback.getWasUseful() ? "Да" : "Нет").append("\n");
            }

            if (feedback.getComment() != null && !feedback.getComment().isEmpty()) {
                sb.append("💭 Комментарий: ").append(feedback.getComment()).append("\n");
            }

            if (feedback.getQuestion() != null && !feedback.getQuestion().isEmpty()) {
                String questionPreview = feedback.getQuestion().length() > 50
                    ? feedback.getQuestion().substring(0, 50) + "..."
                    : feedback.getQuestion();
                sb.append("❓ Вопрос: ").append(questionPreview).append("\n");
            }

            sb.append("📅 ").append(feedback.getCreatedAt().toLocalDate()).append("\n\n");
        }

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(sb.toString());
        message.setReplyMarkup(createAdminMenuKeyboard());
        result.add(message);

        return result;
    }

    private SendMessage buildActivityDateSelectionMessage(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("📅 Выберите дату для просмотра активности:");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        java.time.LocalDate today = java.time.LocalDate.now();
        for (int i = 0; i < 7; i++) {
            java.time.LocalDate date = today.minusDays(i);
            String dateStr = date.toString();
            String displayText = i == 0 ? "Сегодня" :
                i == 1 ? "Вчера" :
                    date.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM"));

            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(displayText);
            button.setCallbackData("activity_date:" + dateStr);

            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(button);
            keyboard.add(row);
        }

        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("◀️ Назад");
        backButton.setCallbackData("admin_menu");
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        backRow.add(backButton);
        keyboard.add(backRow);

        markup.setKeyboard(keyboard);
        message.setReplyMarkup(markup);

        return message;
    }

    private List<SendMessage> buildActivityUsersForDateMessages(Long chatId, String dateStr) {
        List<SendMessage> result = new ArrayList<>();
        try {
            java.time.LocalDate date = java.time.LocalDate.parse(dateStr);
            List<SummaryService.UserActivityInfo> users = summaryService.getActiveUsersByDate(date);

            if (users.isEmpty()) {
                SendMessage message = new SendMessage();
                message.setChatId(chatId.toString());
                message.setText("❌ На выбранную дату (" + dateStr + ") нет активности.");
                message.setReplyMarkup(createAdminMenuKeyboard());
                result.add(message);
                return result;
            }

            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("👥 Активные пользователи на " + dateStr + ":\n\nВыберите пользователя:");

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

            for (SummaryService.UserActivityInfo user : users) {
                InlineKeyboardButton button = new InlineKeyboardButton();
                String displayName = user.username != null ? "@" + user.username : "ID: " + user.userId;
                button.setText(displayName);
                button.setCallbackData("activity_user:" + dateStr + ":" + user.userId);

                List<InlineKeyboardButton> row = new ArrayList<>();
                row.add(button);
                keyboard.add(row);
            }

            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("◀️ Назад");
            backButton.setCallbackData("admin_activity");
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            backRow.add(backButton);
            keyboard.add(backRow);

            markup.setKeyboard(keyboard);
            message.setReplyMarkup(markup);

            result.add(message);
        } catch (Exception e) {
            log.error("Ошибка при обработке выбранной даты", e);
            SendMessage errorMessage = new SendMessage();
            errorMessage.setChatId(chatId.toString());
            errorMessage.setText("❌ Ошибка: неверный формат даты. Используйте ГГГГ-ММ-ДД");
            result.add(errorMessage);
        }

        return result;
    }

    private List<SendMessage> buildActivityUserSummariesMessages(Long chatId, String dateStr, Long userId) {
        List<SendMessage> result = new ArrayList<>();
        try {
            java.time.LocalDate date = java.time.LocalDate.parse(dateStr);
            List<com.example.m1nd.model.UserSessionSummary> summaries =
                summaryService.getSummariesByUserAndDate(userId, date);

            if (summaries.isEmpty()) {
                SendMessage message = new SendMessage();
                message.setChatId(chatId.toString());
                message.setText("❌ Для выбранного пользователя на эту дату нет сводок.");
                message.setReplyMarkup(createAdminMenuKeyboard());
                result.add(message);
                return result;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("📊 Сводки активности пользователя ");
            if (summaries.get(0).getUsername() != null) {
                sb.append("@").append(summaries.get(0).getUsername());
            } else {
                sb.append("ID: ").append(userId);
            }
            sb.append(" за ").append(dateStr).append(":\n\n");

            for (int i = 0; i < summaries.size(); i++) {
                com.example.m1nd.model.UserSessionSummary summary = summaries.get(i);
                sb.append("━━━━━━━━━━━━━━━━━━━━\n");
                sb.append("📝 Сводка #").append(i + 1).append("\n\n");
                sb.append("❓ ВОПРОС:\n");
                sb.append(summary.getSummaryQuestion()).append("\n\n");
                sb.append("💬 ОТВЕТ:\n");
                sb.append(summary.getSummaryAnswer()).append("\n\n");
            }

            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(sb.toString());
            message.setReplyMarkup(createAdminMenuKeyboard());
            result.add(message);
        } catch (Exception e) {
            log.error("Ошибка при показе сводок пользователя", e);
            SendMessage errorMessage = new SendMessage();
            errorMessage.setChatId(chatId.toString());
            errorMessage.setText("❌ Ошибка при получении сводок: " + e.getMessage());
            result.add(errorMessage);
        }

        return result;
    }

    private SendMessage buildAddAdminCallbackMessage(Long chatId, String currentUsername, String targetUsername) {
        boolean added = adminService.addAdmin(targetUsername, currentUsername);

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());

        if (added) {
            message.setText("✅ Администратор @" + targetUsername.replace("@", "") + " успешно добавлен!");
        } else {
            message.setText("❌ Не удалось добавить администратора. Возможно, он уже является администратором.");
        }

        return message;
    }

    private SendMessage buildAddAdminUsernameMessage(Long chatId, String currentUsername, String messageText) {
        String targetUsername = messageText.trim();
        boolean added = adminService.addAdmin(targetUsername, currentUsername);

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());

        if (added) {
            message.setText("✅ Администратор @" + targetUsername.replace("@", "") + " успешно добавлен!");
        } else {
            message.setText("❌ Не удалось добавить администратора. Возможно, он уже является администратором.");
        }

        message.setReplyMarkup(createAdminKeyboard());
        return message;
    }

    private SendMessage buildRemoveAdminUsernameMessage(Long chatId, String currentUsername, String messageText) {
        String targetUsername = messageText.trim();

        String cleanTargetUsername = targetUsername.startsWith("@") ? targetUsername.substring(1) : targetUsername;
        String cleanCurrentUsername = currentUsername != null && currentUsername.startsWith("@")
            ? currentUsername.substring(1)
            : currentUsername;

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());

        if (cleanTargetUsername.equalsIgnoreCase(cleanCurrentUsername)) {
            message.setText("❌ Вы не можете удалить самого себя из администраторов.");
            message.setReplyMarkup(createAdminMenuKeyboard());
            return message;
        }

        boolean removed = adminService.removeAdmin(targetUsername);

        if (removed) {
            message.setText("✅ Администратор @" + targetUsername.replace("@", "") + " успешно удален!");
        } else {
            message.setText("❌ Не удалось удалить администратора. Возможно, он не найден в списке.");
        }

        message.setReplyMarkup(createAdminKeyboard());
        return message;
    }

    private SendMessage buildDeleteFactTopicsMessage(Long chatId) {
        List<FactTopic> topics = factTopicService.findAll();

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());

        if (topics.isEmpty()) {
            msg.setText("Тем фактов пока нет.");
            msg.setReplyMarkup(createMenuEditorKeyboard());
        } else {
            msg.setText("Выберите тему фактов для удаления:");
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

            for (FactTopic topic : topics) {
                InlineKeyboardButton b = new InlineKeyboardButton();
                b.setText(topic.getTitle());
                b.setCallbackData("menu_delete_fact_topic_confirm:" + topic.getId());

                List<InlineKeyboardButton> row = new ArrayList<>();
                row.add(b);
                keyboard.add(row);
            }

            markup.setKeyboard(keyboard);
            msg.setReplyMarkup(markup);
        }

        return msg;
    }

    private SendMessage buildDeleteTasksMessage(Long chatId) {
        List<Task> tasks = taskService.findAll();

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());

        if (tasks.isEmpty()) {
            msg.setText("Заданий пока нет.");
            msg.setReplyMarkup(createMenuEditorKeyboard());
        } else {
            msg.setText("Выберите задание для удаления:");
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

            for (Task task : tasks) {
                String preview = task.getText();
                if (preview.length() > 40) {
                    preview = preview.substring(0, 40) + "...";
                }

                InlineKeyboardButton b = new InlineKeyboardButton();
                b.setText(task.getId() + ": " + preview);
                b.setCallbackData("menu_delete_task_confirm:" + task.getId());

                List<InlineKeyboardButton> row = new ArrayList<>();
                row.add(b);
                keyboard.add(row);
            }

            markup.setKeyboard(keyboard);
            msg.setReplyMarkup(markup);
        }

        return msg;
    }

    public static class AdminMenuResult {
        private final List<SendMessage> messages;
        private final String callbackAnswer;

        public AdminMenuResult(List<SendMessage> messages, String callbackAnswer) {
            this.messages = messages;
            this.callbackAnswer = callbackAnswer;
        }

        public List<SendMessage> getMessages() {
            return messages;
        }

        public String getCallbackAnswer() {
            return callbackAnswer;
        }
    }

    public static class AdminTextResult {
        private final boolean handled;
        private final List<SendMessage> messages;

        private AdminTextResult(boolean handled, List<SendMessage> messages) {
            this.handled = handled;
            this.messages = messages;
        }

        public static AdminTextResult handled(List<SendMessage> messages) {
            return new AdminTextResult(true, messages);
        }

        public static AdminTextResult notHandled() {
            return new AdminTextResult(false, List.of());
        }

        public boolean isHandled() {
            return handled;
        }

        public List<SendMessage> getMessages() {
            return messages;
        }
    }
}

