package com.example.m1nd.bot;

import com.example.m1nd.model.AssistantType;
import com.example.m1nd.service.AdminService;
import com.example.m1nd.service.AssistantService;
import com.example.m1nd.service.FeedbackService;
import com.example.m1nd.service.I18nService;
import com.example.m1nd.service.StatisticsService;
import com.example.m1nd.service.SummaryService;
import com.example.m1nd.service.UserService;
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
    private final AssistantService assistantService;
    private final UserService userService;
    private final FeedbackService feedbackService;
    private final StatisticsService statisticsService;
    private final SummaryService summaryService;
    private final I18nService i18nService;
    private final Map<Long, Boolean> waitingForAdminUsername = new ConcurrentHashMap<>();
    private final Map<Long, Boolean> waitingForRemoveAdminUsername = new ConcurrentHashMap<>();
    private final Map<Long, Boolean> waitingForAssistantUsername = new ConcurrentHashMap<>();
    private final Map<Long, String> pendingAssistantUsername = new ConcurrentHashMap<>();
    private final Map<Long, Boolean> waitingForRemoveAssistantUsername = new ConcurrentHashMap<>();
    public InlineKeyboardMarkup createAdminKeyboard() {
        return createAdminKeyboard("ru");
    }

    public InlineKeyboardMarkup createAdminKeyboard(String languageCode) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();

        InlineKeyboardButton statsButton = new InlineKeyboardButton();
        statsButton.setText(i18nService.get(languageCode, "admin.button.stats"));
        statsButton.setCallbackData("stats");

        InlineKeyboardButton adminButton = new InlineKeyboardButton();
        adminButton.setText(i18nService.get(languageCode, "admin.button.admin"));
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
        return createAdminMenuKeyboard("ru");
    }

    public InlineKeyboardMarkup createAdminMenuKeyboard(String languageCode) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();

        InlineKeyboardButton addAdminButton = new InlineKeyboardButton();
        addAdminButton.setText(i18nService.get(languageCode, "admin.menu.add_admin"));
        addAdminButton.setCallbackData("add_admin_prompt");

        InlineKeyboardButton listAdminsButton = new InlineKeyboardButton();
        listAdminsButton.setText(i18nService.get(languageCode, "admin.menu.list_admins"));
        listAdminsButton.setCallbackData("list_admins");

        InlineKeyboardButton removeAdminButton = new InlineKeyboardButton();
        removeAdminButton.setText(i18nService.get(languageCode, "admin.menu.remove_admin"));
        removeAdminButton.setCallbackData("remove_admin_prompt");

        InlineKeyboardButton feedbacksButton = new InlineKeyboardButton();
        feedbacksButton.setText(i18nService.get(languageCode, "admin.menu.feedbacks"));
        feedbacksButton.setCallbackData("view_feedbacks");

        InlineKeyboardButton activityButton = new InlineKeyboardButton();
        activityButton.setText(i18nService.get(languageCode, "admin.menu.activity"));
        activityButton.setCallbackData("admin_activity");

        InlineKeyboardButton addAssistantButton = new InlineKeyboardButton();
        addAssistantButton.setText(i18nService.get(languageCode, "admin.menu.add_assistant"));
        addAssistantButton.setCallbackData("add_business_assistant_prompt");

        InlineKeyboardButton listAssistantsButton = new InlineKeyboardButton();
        listAssistantsButton.setText(i18nService.get(languageCode, "admin.menu.list_assistants"));
        listAssistantsButton.setCallbackData("list_business_assistants");

        InlineKeyboardButton removeAssistantButton = new InlineKeyboardButton();
        removeAssistantButton.setText(i18nService.get(languageCode, "admin.menu.remove_assistant"));
        removeAssistantButton.setCallbackData("remove_business_assistant_prompt");

        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText(i18nService.get(languageCode, "menu.option.back"));
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

        List<InlineKeyboardButton> row8 = new ArrayList<>();
        row8.add(addAssistantButton);

        List<InlineKeyboardButton> row9 = new ArrayList<>();
        row9.add(listAssistantsButton);

        List<InlineKeyboardButton> row10 = new ArrayList<>();
        row10.add(removeAssistantButton);

        List<InlineKeyboardButton> row7 = new ArrayList<>();
        row7.add(backButton);

        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        keyboard.add(row1);
        keyboard.add(row2);
        keyboard.add(row3);
        keyboard.add(row4);
        keyboard.add(row5);
        keyboard.add(row8);
        keyboard.add(row9);
        keyboard.add(row10);
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
        InlineKeyboardButton addIdeaTopic = new InlineKeyboardButton();
        addIdeaTopic.setText("➕ Добавить тему для идеи");
        addIdeaTopic.setCallbackData("menu_add_idea_topic");
        row2.add(addIdeaTopic);

        InlineKeyboardButton deleteIdeaTopic = new InlineKeyboardButton();
        deleteIdeaTopic.setText("➖ Удалить тему идеи");
        deleteIdeaTopic.setCallbackData("menu_delete_idea_topic");
        row2.add(deleteIdeaTopic);

        List<InlineKeyboardButton> row3 = new ArrayList<>();
        InlineKeyboardButton addMotivationTopic = new InlineKeyboardButton();
        addMotivationTopic.setText("➕ Добавить тему мотивации");
        addMotivationTopic.setCallbackData("menu_add_motivation_topic");
        row3.add(addMotivationTopic);

        InlineKeyboardButton deleteMotivationTopic = new InlineKeyboardButton();
        deleteMotivationTopic.setText("➖ Удалить тему мотивации");
        deleteMotivationTopic.setCallbackData("menu_delete_motivation_topic");
        row3.add(deleteMotivationTopic);

        List<InlineKeyboardButton> row4 = new ArrayList<>();
        InlineKeyboardButton addGame = new InlineKeyboardButton();
        addGame.setText("➕ Добавить игру");
        addGame.setCallbackData("menu_add_game");
        row4.add(addGame);

        InlineKeyboardButton deleteGame = new InlineKeyboardButton();
        deleteGame.setText("➖ Удалить игру");
        deleteGame.setCallbackData("menu_delete_game");
        row4.add(deleteGame);

        List<InlineKeyboardButton> row5 = new ArrayList<>();
        InlineKeyboardButton addTask = new InlineKeyboardButton();
        addTask.setText("➕ Добавить задание");
        addTask.setCallbackData("menu_add_task");
        row5.add(addTask);

        InlineKeyboardButton deleteTask = new InlineKeyboardButton();
        deleteTask.setText("➖ Удалить задание");
        deleteTask.setCallbackData("menu_delete_task");
        row5.add(deleteTask);

        List<InlineKeyboardButton> row6 = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("◀️ Назад");
        backButton.setCallbackData("admin_menu");
        row6.add(backButton);

        keyboard.add(row1);
        keyboard.add(row2);
        keyboard.add(row3);
        keyboard.add(row4);
        keyboard.add(row5);
        keyboard.add(row6);
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
                || "add_business_assistant_prompt".equals(data)
                || "list_business_assistants".equals(data)
                || "remove_business_assistant_prompt".equals(data)
                || data.startsWith("assistant_type:")
        );
    }

    public AdminMenuResult handleCallback(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        Long userId = callbackQuery.getFrom().getId();
        String username = callbackQuery.getFrom().getUserName();
        Long chatId = callbackQuery.getMessage().getChatId();
        String languageCode = callbackQuery.getFrom().getLanguageCode();

        List<SendMessage> messages = new ArrayList<>();
        String callbackAnswer = "✅";

        if (username == null || !adminService.isAdmin(username)) {
            callbackAnswer = i18nService.get(languageCode, "admin.error.no_access");
            return new AdminMenuResult(messages, callbackAnswer);
        }

        if ("stats".equals(data)) {
            userService.trackUserActivity(userId);
            messages.addAll(buildStatisticsMessages(chatId, languageCode));
            callbackAnswer = i18nService.get(languageCode, "admin.callback.stats_sent");
        } else if ("admin_menu".equals(data)) {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(i18nService.get(languageCode, "admin.menu.title"));
            message.setReplyMarkup(createAdminMenuKeyboard(languageCode));
            messages.add(message);
            callbackAnswer = i18nService.get(languageCode, "admin.callback.menu_opened");
        } else if ("back_to_main".equals(data)) {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(i18nService.get(languageCode, "menu.main.title"));
            message.setReplyMarkup(createAdminKeyboard(languageCode));
            messages.add(message);
            callbackAnswer = "✅";
        } else if ("add_admin_prompt".equals(data)) {
            waitingForAdminUsername.put(userId, true);

            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(i18nService.get(languageCode, "admin.prompt.add_admin"));
            message.setReplyMarkup(createAdminMenuKeyboard(languageCode));
            messages.add(message);
            callbackAnswer = i18nService.get(languageCode, "admin.callback.enter_username");
        } else if ("list_admins".equals(data)) {
            messages.add(buildListAdminsMessage(chatId, languageCode));
            callbackAnswer = i18nService.get(languageCode, "admin.callback.list_sent");
        } else if ("view_feedbacks".equals(data)) {
            messages.addAll(buildFeedbacksMessages(chatId, languageCode));
            callbackAnswer = i18nService.get(languageCode, "admin.callback.feedbacks_sent");
        } else if ("admin_activity".equals(data)) {
            messages.add(buildActivityDateSelectionMessage(chatId, languageCode));
            callbackAnswer = i18nService.get(languageCode, "admin.callback.choose_date");
        } else if (data != null && data.startsWith("activity_date:")) {
            String dateStr = data.substring("activity_date:".length());
            messages.addAll(buildActivityUsersForDateMessages(chatId, dateStr, languageCode));
            callbackAnswer = "✅";
        } else if (data != null && data.startsWith("activity_user:")) {
            String[] parts = data.substring("activity_user:".length()).split(":");
            if (parts.length == 2) {
                String dateStr = parts[0];
                Long targetUserId = Long.parseLong(parts[1]);
                messages.addAll(buildActivityUserSummariesMessages(chatId, dateStr, targetUserId, languageCode));
                callbackAnswer = "✅";
            }
        } else if ("remove_admin_prompt".equals(data)) {
            waitingForRemoveAdminUsername.put(userId, true);

            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(i18nService.get(languageCode, "admin.prompt.remove_admin"));
            message.setReplyMarkup(createAdminMenuKeyboard(languageCode));
            messages.add(message);
            callbackAnswer = i18nService.get(languageCode, "admin.callback.enter_username");
        } else if ("add_business_assistant_prompt".equals(data)) {
            waitingForAssistantUsername.put(userId, true);
            pendingAssistantUsername.remove(userId);

            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(i18nService.get(languageCode, "admin.prompt.add_assistant"));
            message.setReplyMarkup(createAdminMenuKeyboard(languageCode));
            messages.add(message);
            callbackAnswer = i18nService.get(languageCode, "admin.callback.enter_assistant_username");
        } else if ("list_business_assistants".equals(data)) {
            messages.add(buildListAssistantsMessage(chatId, languageCode));
            callbackAnswer = i18nService.get(languageCode, "admin.callback.assistants_sent");
        } else if ("remove_business_assistant_prompt".equals(data)) {
            waitingForRemoveAssistantUsername.put(userId, true);
            pendingAssistantUsername.remove(userId);

            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(i18nService.get(languageCode, "admin.prompt.remove_assistant"));
            message.setReplyMarkup(createAdminMenuKeyboard(languageCode));
            messages.add(message);
            callbackAnswer = i18nService.get(languageCode, "admin.callback.enter_assistant_username");
        } else if (data != null && data.startsWith("assistant_type:")) {
            String typeCode = data.substring("assistant_type:".length());
            String targetUsername = pendingAssistantUsername.remove(userId);

            if (targetUsername == null || targetUsername.isBlank()) {
                SendMessage message = new SendMessage();
                message.setChatId(chatId.toString());
                message.setText(i18nService.get(languageCode, "admin.error.assistant_username_first"));
                message.setReplyMarkup(createAdminMenuKeyboard(languageCode));
                messages.add(message);
                callbackAnswer = i18nService.get(languageCode, "admin.callback.no_username");
            } else {
                AssistantType type = parseAssistantType(typeCode);
                if (type == null) {
                    SendMessage message = new SendMessage();
                    message.setChatId(chatId.toString());
                    message.setText(i18nService.get(languageCode, "admin.error.assistant_type_invalid"));
                    message.setReplyMarkup(createAdminMenuKeyboard(languageCode));
                    messages.add(message);
                    callbackAnswer = i18nService.get(languageCode, "admin.callback.invalid_type");
                } else {
                    messages.add(buildAddAssistantMessage(chatId, targetUsername, type, languageCode));
                    callbackAnswer = i18nService.get(languageCode, "admin.callback.assistant_added");
                }
            }
        } else if (data != null && data.startsWith("add_admin:")) {
            String targetUsername = data.substring("add_admin:".length());
            messages.add(buildAddAdminCallbackMessage(chatId, username, targetUsername, languageCode));
            callbackAnswer = i18nService.get(languageCode, "admin.callback.admin_added");
        } else {
            callbackAnswer = i18nService.get(languageCode, "common.unknown_command");
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
        String languageCode = update.getMessage().getFrom().getLanguageCode();

        if (username == null || !adminService.isAdmin(username)) {
            return AdminTextResult.notHandled();
        }

        List<SendMessage> messages = new ArrayList<>();

        if (waitingForAdminUsername.getOrDefault(userId, false)) {
            messages.add(buildAddAdminUsernameMessage(chatId, username, messageText, languageCode));
            waitingForAdminUsername.remove(userId);
            return AdminTextResult.handled(messages);
        }

        if (waitingForRemoveAdminUsername.getOrDefault(userId, false)) {
            messages.add(buildRemoveAdminUsernameMessage(chatId, username, messageText, languageCode));
            waitingForRemoveAdminUsername.remove(userId);
            return AdminTextResult.handled(messages);
        }

        if (waitingForAssistantUsername.getOrDefault(userId, false)) {
            String targetUsername = messageText.trim();
            pendingAssistantUsername.put(userId, targetUsername);
            messages.add(buildAssistantTypeSelectionMessage(chatId, targetUsername, languageCode));
            waitingForAssistantUsername.remove(userId);
            return AdminTextResult.handled(messages);
        }

        if (waitingForRemoveAssistantUsername.getOrDefault(userId, false)) {
            messages.add(buildRemoveAssistantUsernameMessage(chatId, messageText, languageCode));
            waitingForRemoveAssistantUsername.remove(userId);
            return AdminTextResult.handled(messages);
        }

        return AdminTextResult.notHandled();
    }

    public List<SendMessage> buildStatisticsMessages(Long chatId) {
        return buildStatisticsMessages(chatId, "ru");
    }

    public List<SendMessage> buildStatisticsMessages(Long chatId, String languageCode) {
        String statistics = statisticsService.formatStatistics();
        final int MAX_LENGTH = 4000;

        List<SendMessage> result = new ArrayList<>();

        if (statistics.length() <= MAX_LENGTH) {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("```\n" + statistics + "\n```");
            message.setParseMode("Markdown");
            message.setReplyMarkup(createAdminKeyboard(languageCode));
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
                message.setReplyMarkup(createAdminKeyboard(languageCode));
            }

            result.add(message);
            offset = endIndex;
        }

        return result;
    }

    private SendMessage buildListAdminsMessage(Long chatId, String languageCode) {
        List<com.example.m1nd.model.Admin> admins = adminService.getAllAdmins();

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());

        if (admins.isEmpty()) {
            message.setText(i18nService.get(languageCode, "admin.list.admins.empty"));
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(i18nService.get(languageCode, "admin.list.admins.title", admins.size())).append("\n\n");

            for (int i = 0; i < admins.size(); i++) {
                com.example.m1nd.model.Admin admin = admins.get(i);
                sb.append(i + 1).append(". ").append(admin.getUsername());
                if (admin.getAddedAt() != null) {
                    sb.append("\n   ").append(i18nService.get(languageCode, "admin.list.added")).append(": ").append(admin.getAddedAt().toLocalDate());
                }
                if (admin.getAddedBy() != null && !admin.getAddedBy().equals("system")) {
                    sb.append("\n   ").append(i18nService.get(languageCode, "admin.list.added_by")).append(": ").append(admin.getAddedBy());
                }
                sb.append("\n\n");
            }

            message.setText(sb.toString());
        }

        message.setReplyMarkup(createAdminMenuKeyboard(languageCode));
        return message;
    }

    private SendMessage buildListAssistantsMessage(Long chatId, String languageCode) {
        List<com.example.m1nd.model.Assistant> assistants = assistantService.getActiveAssistants();

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());

        if (assistants.isEmpty()) {
            message.setText(i18nService.get(languageCode, "admin.list.assistants.empty"));
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(i18nService.get(languageCode, "admin.list.assistants.title", assistants.size())).append("\n\n");

            for (int i = 0; i < assistants.size(); i++) {
                com.example.m1nd.model.Assistant a = assistants.get(i);
                sb.append(i + 1).append(". ");
                if (a.getUsername() != null && !a.getUsername().isBlank()) {
                    sb.append(a.getUsername());
                } else {
                    sb.append("ID: ").append(a.getTelegramUserId());
                }
                sb.append("\n   ").append(i18nService.get(languageCode, "admin.list.type")).append(": ").append(typeTitle(a.getType(), languageCode)).append("\n");
            }

            message.setText(sb.toString());
        }

        message.setReplyMarkup(createAdminMenuKeyboard(languageCode));
        return message;
    }

    private List<SendMessage> buildFeedbacksMessages(Long chatId, String languageCode) {
        List<com.example.m1nd.model.Feedback> feedbacks = feedbackService.getRecentFeedbacks(30);
        List<SendMessage> result = new ArrayList<>();

        if (feedbacks.isEmpty()) {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(i18nService.get(languageCode, "admin.feedbacks.empty"));
            message.setReplyMarkup(createAdminMenuKeyboard(languageCode));
            result.add(message);
            return result;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(i18nService.get(languageCode, "admin.feedbacks.title")).append("\n\n");

        for (com.example.m1nd.model.Feedback feedback : feedbacks) {
            sb.append("👤 ").append(feedback.getUsername() != null ? feedback.getUsername() : feedback.getFirstName())
              .append(" (").append(feedback.getUserId()).append(")\n");

            if (feedback.getRating() != null) {
                sb.append(i18nService.get(languageCode, "admin.feedbacks.rating")).append(": ").append(feedback.getRating()).append("/10\n");
            }

            if (feedback.getWasUseful() != null) {
                sb.append(i18nService.get(languageCode, "admin.feedbacks.useful")).append(": ")
                    .append(feedback.getWasUseful() ? i18nService.get(languageCode, "common.yes") : i18nService.get(languageCode, "common.no"))
                    .append("\n");
            }

            if (feedback.getComment() != null && !feedback.getComment().isEmpty()) {
                sb.append(i18nService.get(languageCode, "admin.feedbacks.comment")).append(": ").append(feedback.getComment()).append("\n");
            }

            if (feedback.getQuestion() != null && !feedback.getQuestion().isEmpty()) {
                String questionPreview = feedback.getQuestion().length() > 50
                    ? feedback.getQuestion().substring(0, 50) + "..."
                    : feedback.getQuestion();
                sb.append(i18nService.get(languageCode, "admin.feedbacks.question")).append(": ").append(questionPreview).append("\n");
            }

            sb.append("📅 ").append(feedback.getCreatedAt().toLocalDate()).append("\n\n");
        }

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(sb.toString());
        message.setReplyMarkup(createAdminMenuKeyboard(languageCode));
        result.add(message);

        return result;
    }

    private SendMessage buildActivityDateSelectionMessage(Long chatId, String languageCode) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(i18nService.get(languageCode, "admin.activity.choose_date"));

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        java.time.LocalDate today = java.time.LocalDate.now();
        for (int i = 0; i < 7; i++) {
            java.time.LocalDate date = today.minusDays(i);
            String dateStr = date.toString();
            String displayText = i == 0 ? i18nService.get(languageCode, "admin.activity.today") :
                i == 1 ? i18nService.get(languageCode, "admin.activity.yesterday") :
                    date.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM"));

            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(displayText);
            button.setCallbackData("activity_date:" + dateStr);

            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(button);
            keyboard.add(row);
        }

        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText(i18nService.get(languageCode, "menu.option.back"));
        backButton.setCallbackData("admin_menu");
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        backRow.add(backButton);
        keyboard.add(backRow);

        markup.setKeyboard(keyboard);
        message.setReplyMarkup(markup);

        return message;
    }

    private List<SendMessage> buildActivityUsersForDateMessages(Long chatId, String dateStr, String languageCode) {
        List<SendMessage> result = new ArrayList<>();
        try {
            java.time.LocalDate date = java.time.LocalDate.parse(dateStr);
            List<SummaryService.UserActivityInfo> users = summaryService.getActiveUsersByDate(date);

            if (users.isEmpty()) {
                SendMessage message = new SendMessage();
                message.setChatId(chatId.toString());
                message.setText(i18nService.get(languageCode, "admin.activity.no_data_for_date", dateStr));
                message.setReplyMarkup(createAdminMenuKeyboard(languageCode));
                result.add(message);
                return result;
            }

            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(i18nService.get(languageCode, "admin.activity.users_for_date", dateStr));

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
            backButton.setText(i18nService.get(languageCode, "menu.option.back"));
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
            errorMessage.setText(i18nService.get(languageCode, "admin.activity.invalid_date"));
            result.add(errorMessage);
        }

        return result;
    }

    private List<SendMessage> buildActivityUserSummariesMessages(Long chatId, String dateStr, Long userId, String languageCode) {
        List<SendMessage> result = new ArrayList<>();
        try {
            java.time.LocalDate date = java.time.LocalDate.parse(dateStr);
            List<com.example.m1nd.model.UserSessionSummary> summaries =
                summaryService.getSummariesByUserAndDate(userId, date);

            if (summaries.isEmpty()) {
                SendMessage message = new SendMessage();
                message.setChatId(chatId.toString());
                message.setText(i18nService.get(languageCode, "admin.activity.no_summaries"));
                message.setReplyMarkup(createAdminMenuKeyboard(languageCode));
                result.add(message);
                return result;
            }

            StringBuilder sb = new StringBuilder();
            sb.append(i18nService.get(languageCode, "admin.activity.summaries_title"));
            if (summaries.get(0).getUsername() != null) {
                sb.append("@").append(summaries.get(0).getUsername());
            } else {
                sb.append("ID: ").append(userId);
            }
            sb.append(" за ").append(dateStr).append(":\n\n");

            for (int i = 0; i < summaries.size(); i++) {
                com.example.m1nd.model.UserSessionSummary summary = summaries.get(i);
                sb.append("━━━━━━━━━━━━━━━━━━━━\n");
                sb.append(i18nService.get(languageCode, "admin.activity.summary_item", i + 1)).append("\n\n");
                sb.append(i18nService.get(languageCode, "admin.activity.question_label")).append("\n");
                sb.append(summary.getSummaryQuestion()).append("\n\n");
                sb.append(i18nService.get(languageCode, "admin.activity.answer_label")).append("\n");
                sb.append(summary.getSummaryAnswer()).append("\n\n");
            }

            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(sb.toString());
            message.setReplyMarkup(createAdminMenuKeyboard(languageCode));
            result.add(message);
        } catch (Exception e) {
            log.error("Ошибка при показе сводок пользователя", e);
            SendMessage errorMessage = new SendMessage();
            errorMessage.setChatId(chatId.toString());
            errorMessage.setText(i18nService.get(languageCode, "admin.activity.summaries_error", e.getMessage()));
            result.add(errorMessage);
        }

        return result;
    }

    private SendMessage buildAddAdminCallbackMessage(Long chatId, String currentUsername, String targetUsername, String languageCode) {
        boolean added = adminService.addAdmin(targetUsername, currentUsername);

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());

        if (added) {
            message.setText(i18nService.get(languageCode, "admin.add.success", targetUsername.replace("@", "")));
        } else {
            message.setText(i18nService.get(languageCode, "admin.add.error"));
        }

        return message;
    }

    private SendMessage buildAddAdminUsernameMessage(Long chatId, String currentUsername, String messageText, String languageCode) {
        String targetUsername = messageText.trim();
        boolean added = adminService.addAdmin(targetUsername, currentUsername);

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());

        if (added) {
            message.setText(i18nService.get(languageCode, "admin.add.success", targetUsername.replace("@", "")));
        } else {
            message.setText(i18nService.get(languageCode, "admin.add.error"));
        }

        message.setReplyMarkup(createAdminKeyboard(languageCode));
        return message;
    }

    private SendMessage buildAddAssistantMessage(Long chatId, String targetUsername, AssistantType type, String languageCode) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());

        var assistantOpt = assistantService.addAssistantByUsername(targetUsername, type);

        if (assistantOpt.isPresent()) {
            message.setText(i18nService.get(languageCode, "admin.assistant.add.success",
                targetUsername.replace("@", ""), typeTitle(type, languageCode)));
        } else {
            message.setText(i18nService.get(languageCode, "admin.assistant.add.error"));
        }

        message.setReplyMarkup(createAdminMenuKeyboard(languageCode));
        return message;
    }

    private SendMessage buildAssistantTypeSelectionMessage(Long chatId, String targetUsername, String languageCode) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(i18nService.get(languageCode, "admin.assistant.choose_type", targetUsername.replace("@", "")));
        message.setReplyMarkup(createAssistantTypeKeyboard(languageCode));
        return message;
    }

    private InlineKeyboardMarkup createAssistantTypeKeyboard(String languageCode) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        InlineKeyboardButton messageType = new InlineKeyboardButton();
        messageType.setText(i18nService.get(languageCode, "admin.assistant.type.message"));
        messageType.setCallbackData("assistant_type:MESSAGE");

        InlineKeyboardButton meetingType = new InlineKeyboardButton();
        meetingType.setText(i18nService.get(languageCode, "admin.assistant.type.meeting"));
        meetingType.setCallbackData("assistant_type:MEETING");

        keyboard.add(List.of(messageType));
        keyboard.add(List.of(meetingType));
        markup.setKeyboard(keyboard);
        return markup;
    }

    private AssistantType parseAssistantType(String code) {
        try {
            return AssistantType.valueOf(code);
        } catch (Exception e) {
            return null;
        }
    }

    private String typeTitle(AssistantType type, String languageCode) {
        if (type == null) {
            return i18nService.get(languageCode, "admin.assistant.type.unknown");
        }
        return switch (type) {
            case MESSAGE -> i18nService.get(languageCode, "admin.assistant.type.message");
            case MEETING -> i18nService.get(languageCode, "admin.assistant.type.meeting");
        };
    }

    private SendMessage buildRemoveAdminUsernameMessage(Long chatId, String currentUsername, String messageText, String languageCode) {
        String targetUsername = messageText.trim();

        String cleanTargetUsername = targetUsername.startsWith("@") ? targetUsername.substring(1) : targetUsername;
        String cleanCurrentUsername = currentUsername != null && currentUsername.startsWith("@")
            ? currentUsername.substring(1)
            : currentUsername;

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());

        if (cleanTargetUsername.equalsIgnoreCase(cleanCurrentUsername)) {
            message.setText(i18nService.get(languageCode, "admin.remove.self_error"));
            message.setReplyMarkup(createAdminMenuKeyboard(languageCode));
            return message;
        }

        boolean removed = adminService.removeAdmin(targetUsername);

        if (removed) {
            message.setText(i18nService.get(languageCode, "admin.remove.success", targetUsername.replace("@", "")));
        } else {
            message.setText(i18nService.get(languageCode, "admin.remove.error"));
        }

        message.setReplyMarkup(createAdminKeyboard(languageCode));
        return message;
    }

    private SendMessage buildRemoveAssistantUsernameMessage(Long chatId, String messageText, String languageCode) {
        String targetUsername = messageText.trim();

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());

        boolean removed = assistantService.deactivateAssistantByUsername(targetUsername);

        if (removed) {
            message.setText(i18nService.get(languageCode, "admin.assistant.remove.success", targetUsername.replace("@", "")));
        } else {
            message.setText(i18nService.get(languageCode, "admin.assistant.remove.error"));
        }

        message.setReplyMarkup(createAdminMenuKeyboard(languageCode));
        return message;
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

