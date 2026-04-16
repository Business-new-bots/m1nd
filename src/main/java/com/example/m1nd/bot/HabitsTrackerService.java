package com.example.m1nd.bot;

import com.example.m1nd.model.HabitDailyTask;
import com.example.m1nd.model.HabitTrackerEntry;
import com.example.m1nd.repository.HabitDailyTaskRepository;
import com.example.m1nd.repository.HabitTrackerEntryRepository;
import com.example.m1nd.service.I18nService;
import com.example.m1nd.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class HabitsTrackerService {
    private final HabitTrackerEntryRepository habitTrackerEntryRepository;
    private final HabitDailyTaskRepository habitDailyTaskRepository;
    private final I18nService i18nService;
    private final UserService userService;

    private final Map<Long, HabitDraft> habitDraftByUser = new ConcurrentHashMap<>();

    public boolean canHandleCallback(String data) {
        return data != null && (
            "main_habits_tracker".equals(data)
                || "habits_add".equals(data)
                || "habits_delete".equals(data)
                || "habits_back_main".equals(data)
                || data.startsWith("habits_category:")
                || data.startsWith("habits_delete_entry:")
                || data.startsWith("habits_task_reminder:")
        );
    }

    public CallbackResult handleCallback(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        Long userId = callbackQuery.getFrom().getId();
        Long chatId = callbackQuery.getMessage().getChatId();
        String languageCode = userService.resolveLanguage(userId, callbackQuery.getFrom().getLanguageCode());

        if ("main_habits_tracker".equals(data)) {
            return openHabitsTracker(chatId, userId, languageCode);
        }
        if ("habits_add".equals(data)) {
            habitDraftByUser.remove(userId);
            return CallbackResult.single(buildAreaSelectionMessage(chatId, languageCode), "✅");
        }
        if ("habits_delete".equals(data)) {
            return CallbackResult.single(buildDeleteMenuMessage(chatId, userId, languageCode), "✅");
        }
        if ("habits_back_main".equals(data)) {
            habitDraftByUser.remove(userId);
            return CallbackResult.single(buildMainMenuMessage(chatId, languageCode), "✅");
        }
        if (data.startsWith("habits_category:")) {
            return onCategorySelected(chatId, userId, data.substring("habits_category:".length()), languageCode);
        }
        if (data.startsWith("habits_delete_entry:")) {
            return onDeleteEntry(chatId, userId, data.substring("habits_delete_entry:".length()), languageCode);
        }
        if (data.startsWith("habits_task_reminder:")) {
            return onTaskReminderSelected(chatId, userId, data.substring("habits_task_reminder:".length()), languageCode);
        }

        return CallbackResult.single(simpleMessage(chatId, i18nService.get(languageCode, "common.in_development")), "✅");
    }

    public TextResult handleText(Long chatId, Long userId, String messageText) {
        HabitDraft draft = habitDraftByUser.get(userId);
        if (draft == null) {
            return TextResult.notHandled();
        }

        if (draft.stage == Stage.WAIT_CUSTOM_NAME) {
            draft.habitName = messageText;
            draft.stage = Stage.WAIT_DURATION;
            return TextResult.handled(singleMessage(
                chatId,
                i18nService.get(draft.languageCode, "habits.question.duration")
            ));
        }

        if (draft.stage == Stage.WAIT_DURATION) {
            draft.durationPlan = messageText;
            draft.stage = Stage.WAIT_FREQUENCY;
            return TextResult.handled(singleMessage(
                chatId,
                i18nService.get(draft.languageCode, "habits.question.frequency")
            ));
        }

        if (draft.stage == Stage.WAIT_FREQUENCY) {
            draft.frequencyPlan = messageText;
            draft.savedEntryId = saveDraft(userId, draft);
            draft.stage = Stage.WAIT_TODAY_TASK;

            return TextResult.handled(singleMessage(
                chatId,
                i18nService.get(draft.languageCode, "habits.saved.ask_task")
            ));
        }

        if (draft.stage == Stage.WAIT_TODAY_TASK) {
            draft.todayTask = messageText;
            draft.stage = Stage.WAIT_REMINDER_CHOICE;
            return TextResult.handled(singleMessageWithMarkup(
                chatId,
                i18nService.get(draft.languageCode, "habits.question.reminder"),
                createReminderChoiceKeyboard(draft.languageCode)
            ));
        }

        return TextResult.notHandled();
    }

    private CallbackResult openHabitsTracker(Long chatId, Long userId, String languageCode) {
        habitDraftByUser.remove(userId);
        cleanupExpiredTasks();
        List<HabitTrackerEntry> habits = habitTrackerEntryRepository.findByUserIdOrderByCreatedAtDesc(userId);
        if (habits.isEmpty()) {
            return CallbackResult.single(buildAreaSelectionMessage(chatId, languageCode), "✅");
        }
        return CallbackResult.single(buildHabitsOverviewMessage(chatId, userId, languageCode), "✅");
    }

    private CallbackResult onCategorySelected(Long chatId, Long userId, String categoryCode, String languageCode) {
        HabitDraft draft = new HabitDraft();
        draft.languageCode = languageCode;
        draft.habitArea = switch (categoryCode) {
            case "health" -> i18nService.get(languageCode, "habits.area.health");
            case "productivity" -> i18nService.get(languageCode, "habits.area.productivity");
            case "mental" -> i18nService.get(languageCode, "habits.area.mental");
            case "study" -> i18nService.get(languageCode, "habits.area.study");
            case "finance" -> i18nService.get(languageCode, "habits.area.finance");
            case "custom" -> i18nService.get(languageCode, "habits.area.custom");
            default -> i18nService.get(languageCode, "habits.area.custom");
        };

        if ("custom".equals(categoryCode)) {
            draft.stage = Stage.WAIT_CUSTOM_NAME;
            habitDraftByUser.put(userId, draft);
            return CallbackResult.single(
                simpleMessage(chatId, i18nService.get(languageCode, "habits.question.custom_name")),
                "✅"
            );
        }

        draft.habitName = draft.habitArea;
        draft.stage = Stage.WAIT_DURATION;
        habitDraftByUser.put(userId, draft);
        return CallbackResult.single(
            simpleMessage(chatId, i18nService.get(languageCode, "habits.question.duration")),
            "✅"
        );
    }

    private CallbackResult onDeleteEntry(Long chatId, Long userId, String idPart, String languageCode) {
        try {
            Long id = Long.parseLong(idPart);
            Optional<HabitTrackerEntry> entryOpt = habitTrackerEntryRepository.findById(id);
            if (entryOpt.isPresent() && userId.equals(entryOpt.get().getUserId())) {
                habitTrackerEntryRepository.deleteById(id);
                return CallbackResult.single(buildHabitsOverviewMessage(chatId, userId, languageCode), "✅");
            }
            return CallbackResult.single(simpleMessage(chatId, i18nService.get(languageCode, "habits.not_found")), "⚠️");
        } catch (NumberFormatException ex) {
            return CallbackResult.single(simpleMessage(chatId, i18nService.get(languageCode, "habits.invalid_id")), "⚠️");
        }
    }

    private CallbackResult onTaskReminderSelected(Long chatId, Long userId, String answerCode, String languageCode) {
        HabitDraft draft = habitDraftByUser.get(userId);
        if (draft == null || draft.stage != Stage.WAIT_REMINDER_CHOICE || draft.todayTask == null) {
            return CallbackResult.single(simpleMessage(chatId, i18nService.get(languageCode, "habits.restart")), "⚠️");
        }

        boolean remindEvening = "yes".equalsIgnoreCase(answerCode);
        saveDailyTask(userId, draft, remindEvening);
        habitDraftByUser.remove(userId);

        List<SendMessage> messages = new ArrayList<>();
        messages.add(simpleMessage(chatId, i18nService.get(languageCode, "habits.task_saved")));
        messages.add(buildHabitsOverviewMessage(chatId, userId, languageCode));
        return new CallbackResult(messages, "✅");
    }

    private SendMessage buildAreaSelectionMessage(Long chatId, String languageCode) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(i18nService.get(languageCode, "habits.greeting"));
        message.setReplyMarkup(createAreaSelectionKeyboard(languageCode));
        return message;
    }

    private SendMessage buildHabitsOverviewMessage(Long chatId, Long userId, String languageCode) {
        cleanupExpiredTasks();
        List<HabitTrackerEntry> habits = habitTrackerEntryRepository.findByUserIdOrderByCreatedAtDesc(userId);
        StringBuilder text = new StringBuilder(i18nService.get(languageCode, "habits.list.title")).append("\n\n");
        int index = 1;
        for (HabitTrackerEntry habit : habits) {
            text.append(index++)
                .append(". ")
                .append(habit.getHabitName())
                .append(" (")
                .append(habit.getHabitArea())
                .append(")\n")
                .append("   ")
                .append(i18nService.get(languageCode, "habits.list.duration"))
                .append(": ")
                .append(habit.getDurationPlan())
                .append("\n")
                .append("   ")
                .append(i18nService.get(languageCode, "habits.list.frequency"))
                .append(": ")
                .append(habit.getFrequencyPlan())
                .append("\n\n");
        }

        habitDailyTaskRepository.findTopByUserIdAndTaskDateOrderByCreatedAtDesc(userId, LocalDate.now())
            .ifPresent(task -> text.append(i18nService.get(languageCode, "habits.today_task.title")).append("\n")
                .append(task.getTaskText())
                .append("\n")
                .append(i18nService.get(languageCode, "habits.today_task.reminder"))
                .append(Boolean.TRUE.equals(task.getRemindEvening())
                    ? i18nService.get(languageCode, "common.yes")
                    : i18nService.get(languageCode, "common.no")));

        SendMessage message = simpleMessage(chatId, text.toString().trim());
        message.setReplyMarkup(createOverviewKeyboard(languageCode));
        return message;
    }

    private SendMessage buildDeleteMenuMessage(Long chatId, Long userId, String languageCode) {
        List<HabitTrackerEntry> habits = habitTrackerEntryRepository.findByUserIdOrderByCreatedAtDesc(userId);
        if (habits.isEmpty()) {
            SendMessage message = simpleMessage(chatId, i18nService.get(languageCode, "habits.delete.none"));
            message.setReplyMarkup(createOverviewKeyboard(languageCode));
            return message;
        }

        SendMessage message = simpleMessage(chatId, i18nService.get(languageCode, "habits.delete.choose"));
        message.setReplyMarkup(createDeleteKeyboard(habits, languageCode));
        return message;
    }

    private SendMessage buildMainMenuMessage(Long chatId, String languageCode) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(i18nService.get(languageCode, "menu.main.choose_assistant"));
        message.setReplyMarkup(createMainMenuKeyboard(languageCode));
        return message;
    }

    private InlineKeyboardMarkup createAreaSelectionKeyboard(String languageCode) {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        keyboard.add(List.of(button("1️⃣ " + i18nService.get(languageCode, "habits.area.health"), "habits_category:health")));
        keyboard.add(List.of(button("2️⃣ " + i18nService.get(languageCode, "habits.area.productivity"), "habits_category:productivity")));
        keyboard.add(List.of(button("3️⃣ " + i18nService.get(languageCode, "habits.area.mental"), "habits_category:mental")));
        keyboard.add(List.of(button("4️⃣ " + i18nService.get(languageCode, "habits.area.study"), "habits_category:study")));
        keyboard.add(List.of(button("5️⃣ " + i18nService.get(languageCode, "habits.area.finance"), "habits_category:finance")));
        keyboard.add(List.of(button("6️⃣ " + i18nService.get(languageCode, "habits.area.custom"), "habits_category:custom")));
        keyboard.add(List.of(button(i18nService.get(languageCode, "menu.option.back"), "habits_back_main")));
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(keyboard);
        return markup;
    }

    private InlineKeyboardMarkup createOverviewKeyboard(String languageCode) {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        keyboard.add(List.of(button(i18nService.get(languageCode, "habits.add"), "habits_add")));
        keyboard.add(List.of(button(i18nService.get(languageCode, "habits.delete"), "habits_delete")));
        keyboard.add(List.of(button(i18nService.get(languageCode, "menu.option.back"), "habits_back_main")));
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(keyboard);
        return markup;
    }

    private InlineKeyboardMarkup createDeleteKeyboard(List<HabitTrackerEntry> habits, String languageCode) {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        for (HabitTrackerEntry habit : habits) {
            keyboard.add(List.of(button(
                i18nService.get(languageCode, "habits.delete.item", habit.getHabitName()),
                "habits_delete_entry:" + habit.getId()
            )));
        }
        keyboard.add(List.of(button(i18nService.get(languageCode, "menu.option.back"), "main_habits_tracker")));
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(keyboard);
        return markup;
    }

    private InlineKeyboardMarkup createMainMenuKeyboard(String languageCode) {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        keyboard.add(List.of(button(i18nService.get(languageCode, "menu.main.business"), "main_business_ai_assistant")));
        keyboard.add(List.of(button(i18nService.get(languageCode, "menu.main.financial"), "main_financial_ai_assistant")));
        keyboard.add(List.of(button(i18nService.get(languageCode, "menu.main.thinking"), "main_thinking_ai_assistant")));
        keyboard.add(List.of(button(i18nService.get(languageCode, "menu.main.habits"), "main_habits_tracker")));
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(keyboard);
        return markup;
    }

    private Long saveDraft(Long userId, HabitDraft draft) {
        HabitTrackerEntry entry = new HabitTrackerEntry();
        entry.setUserId(userId);
        entry.setHabitArea(draft.habitArea);
        entry.setHabitName(draft.habitName);
        entry.setDurationPlan(draft.durationPlan);
        entry.setFrequencyPlan(draft.frequencyPlan);
        entry.setCreatedAt(LocalDateTime.now());
        return habitTrackerEntryRepository.save(entry).getId();
    }

    private void saveDailyTask(Long userId, HabitDraft draft, boolean remindEvening) {
        LocalDate today = LocalDate.now();
        habitDailyTaskRepository.deleteByUserIdAndTaskDate(userId, today);

        HabitDailyTask task = new HabitDailyTask();
        task.setUserId(userId);
        task.setHabitTrackerEntryId(draft.savedEntryId);
        task.setTaskText(draft.todayTask);
        task.setTaskDate(today);
        task.setRemindEvening(remindEvening);
        task.setCreatedAt(LocalDateTime.now());
        habitDailyTaskRepository.save(task);
    }

    private void cleanupExpiredTasks() {
        habitDailyTaskRepository.deleteByTaskDateBefore(LocalDate.now());
    }

    private List<SendMessage> singleMessage(Long chatId, String text) {
        List<SendMessage> messages = new ArrayList<>();
        messages.add(simpleMessage(chatId, text));
        return messages;
    }

    private List<SendMessage> singleMessageWithMarkup(Long chatId, String text, InlineKeyboardMarkup markup) {
        List<SendMessage> messages = new ArrayList<>();
        SendMessage message = simpleMessage(chatId, text);
        message.setReplyMarkup(markup);
        messages.add(message);
        return messages;
    }

    private SendMessage simpleMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        return message;
    }

    private InlineKeyboardButton button(String text, String data) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(data);
        return button;
    }

    private InlineKeyboardMarkup createReminderChoiceKeyboard(String languageCode) {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        keyboard.add(List.of(button(i18nService.get(languageCode, "common.yes"), "habits_task_reminder:yes"),
            button(i18nService.get(languageCode, "common.no"), "habits_task_reminder:no")));
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(keyboard);
        return markup;
    }

    private enum Stage {
        WAIT_CUSTOM_NAME,
        WAIT_DURATION,
        WAIT_FREQUENCY,
        WAIT_TODAY_TASK,
        WAIT_REMINDER_CHOICE
    }

    private static class HabitDraft {
        private Stage stage;
        private String habitArea;
        private String habitName;
        private String durationPlan;
        private String frequencyPlan;
        private String todayTask;
        private Long savedEntryId;
        private String languageCode;
    }

    public static class CallbackResult {
        private final List<SendMessage> messages;
        private final String callbackAnswer;

        public CallbackResult(List<SendMessage> messages, String callbackAnswer) {
            this.messages = messages;
            this.callbackAnswer = callbackAnswer;
        }

        public static CallbackResult single(SendMessage message, String callbackAnswer) {
            return new CallbackResult(List.of(message), callbackAnswer);
        }

        public List<SendMessage> getMessages() {
            return messages;
        }

        public String getCallbackAnswer() {
            return callbackAnswer;
        }
    }

    public static class TextResult {
        private final boolean handled;
        private final List<SendMessage> messages;

        public TextResult(boolean handled, List<SendMessage> messages) {
            this.handled = handled;
            this.messages = messages;
        }

        public static TextResult handled(List<SendMessage> messages) {
            return new TextResult(true, messages);
        }

        public static TextResult notHandled() {
            return new TextResult(false, List.of());
        }

        public boolean isHandled() {
            return handled;
        }

        public List<SendMessage> getMessages() {
            return messages;
        }
    }
}
