package com.example.m1nd.bot;

import com.example.m1nd.model.HabitDailyTask;
import com.example.m1nd.model.HabitTrackerEntry;
import com.example.m1nd.repository.HabitDailyTaskRepository;
import com.example.m1nd.repository.HabitTrackerEntryRepository;
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
    private static final String AREA_HEALTH = "Здоровье";
    private static final String AREA_PRODUCTIVITY = "Продуктивность";
    private static final String AREA_MENTAL = "Ментал";
    private static final String AREA_STUDY = "Учёба";
    private static final String AREA_FINANCE = "Финансы";
    private static final String AREA_CUSTOM = "Свое";

    private final HabitTrackerEntryRepository habitTrackerEntryRepository;
    private final HabitDailyTaskRepository habitDailyTaskRepository;

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

        if ("main_habits_tracker".equals(data)) {
            return openHabitsTracker(chatId, userId);
        }
        if ("habits_add".equals(data)) {
            habitDraftByUser.remove(userId);
            return CallbackResult.single(buildAreaSelectionMessage(chatId), "✅");
        }
        if ("habits_delete".equals(data)) {
            return CallbackResult.single(buildDeleteMenuMessage(chatId, userId), "✅");
        }
        if ("habits_back_main".equals(data)) {
            habitDraftByUser.remove(userId);
            return CallbackResult.single(buildMainMenuMessage(chatId), "✅");
        }
        if (data.startsWith("habits_category:")) {
            return onCategorySelected(chatId, userId, data.substring("habits_category:".length()));
        }
        if (data.startsWith("habits_delete_entry:")) {
            return onDeleteEntry(chatId, userId, data.substring("habits_delete_entry:".length()));
        }
        if (data.startsWith("habits_task_reminder:")) {
            return onTaskReminderSelected(chatId, userId, data.substring("habits_task_reminder:".length()));
        }

        return CallbackResult.single(simpleMessage(chatId, "Раздел в разработке."), "✅");
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
                "В течение какого времени хочешь это делать? (например, 21 день)"
            ));
        }

        if (draft.stage == Stage.WAIT_DURATION) {
            draft.durationPlan = messageText;
            draft.stage = Stage.WAIT_FREQUENCY;
            return TextResult.handled(singleMessage(
                chatId,
                "Со скольки раз в неделю/день тебе удобно начинать?"
            ));
        }

        if (draft.stage == Stage.WAIT_FREQUENCY) {
            draft.frequencyPlan = messageText;
            draft.savedEntryId = saveDraft(userId, draft);
            draft.stage = Stage.WAIT_TODAY_TASK;

            return TextResult.handled(singleMessage(
                chatId,
                "Отлично! Привычка сохранена.\n\nЗадание на сегодня:"
            ));
        }

        if (draft.stage == Stage.WAIT_TODAY_TASK) {
            draft.todayTask = messageText;
            draft.stage = Stage.WAIT_REMINDER_CHOICE;
            return TextResult.handled(singleMessageWithMarkup(
                chatId,
                "Напомнить вечером отчитаться?",
                createReminderChoiceKeyboard()
            ));
        }

        return TextResult.notHandled();
    }

    private CallbackResult openHabitsTracker(Long chatId, Long userId) {
        habitDraftByUser.remove(userId);
        cleanupExpiredTasks();
        List<HabitTrackerEntry> habits = habitTrackerEntryRepository.findByUserIdOrderByCreatedAtDesc(userId);
        if (habits.isEmpty()) {
            return CallbackResult.single(buildAreaSelectionMessage(chatId), "✅");
        }
        return CallbackResult.single(buildHabitsOverviewMessage(chatId, userId), "✅");
    }

    private CallbackResult onCategorySelected(Long chatId, Long userId, String categoryCode) {
        HabitDraft draft = new HabitDraft();
        draft.habitArea = switch (categoryCode) {
            case "health" -> AREA_HEALTH;
            case "productivity" -> AREA_PRODUCTIVITY;
            case "mental" -> AREA_MENTAL;
            case "study" -> AREA_STUDY;
            case "finance" -> AREA_FINANCE;
            case "custom" -> AREA_CUSTOM;
            default -> AREA_CUSTOM;
        };

        if ("custom".equals(categoryCode)) {
            draft.stage = Stage.WAIT_CUSTOM_NAME;
            habitDraftByUser.put(userId, draft);
            return CallbackResult.single(
                simpleMessage(chatId, "Какую привычку ты хочешь сформировать?"),
                "✅"
            );
        }

        draft.habitName = draft.habitArea;
        draft.stage = Stage.WAIT_DURATION;
        habitDraftByUser.put(userId, draft);
        return CallbackResult.single(
            simpleMessage(chatId, "В течение какого времени хочешь это делать? (например, 21 день)"),
            "✅"
        );
    }

    private CallbackResult onDeleteEntry(Long chatId, Long userId, String idPart) {
        try {
            Long id = Long.parseLong(idPart);
            Optional<HabitTrackerEntry> entryOpt = habitTrackerEntryRepository.findById(id);
            if (entryOpt.isPresent() && userId.equals(entryOpt.get().getUserId())) {
                habitTrackerEntryRepository.deleteById(id);
                return CallbackResult.single(buildHabitsOverviewMessage(chatId, userId), "✅ Удалено");
            }
            return CallbackResult.single(simpleMessage(chatId, "Привычка не найдена."), "⚠️");
        } catch (NumberFormatException ex) {
            return CallbackResult.single(simpleMessage(chatId, "Некорректный идентификатор привычки."), "⚠️");
        }
    }

    private CallbackResult onTaskReminderSelected(Long chatId, Long userId, String answerCode) {
        HabitDraft draft = habitDraftByUser.get(userId);
        if (draft == null || draft.stage != Stage.WAIT_REMINDER_CHOICE || draft.todayTask == null) {
            return CallbackResult.single(simpleMessage(chatId, "Давай начнем заново через меню трекера привычек."), "⚠️");
        }

        boolean remindEvening = "yes".equalsIgnoreCase(answerCode);
        saveDailyTask(userId, draft, remindEvening);
        habitDraftByUser.remove(userId);

        List<SendMessage> messages = new ArrayList<>();
        messages.add(simpleMessage(chatId, "Готово! Задание на сегодня сохранено."));
        messages.add(buildHabitsOverviewMessage(chatId, userId));
        return new CallbackResult(messages, "✅");
    }

    private SendMessage buildAreaSelectionMessage(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(
            "Привет! Я — твой личный трекер привычек. Помогу выработать новые полезные привычки!\n\n" +
            "Выбери направление, над которым хочешь работать:"
        );
        message.setReplyMarkup(createAreaSelectionKeyboard());
        return message;
    }

    private SendMessage buildHabitsOverviewMessage(Long chatId, Long userId) {
        cleanupExpiredTasks();
        List<HabitTrackerEntry> habits = habitTrackerEntryRepository.findByUserIdOrderByCreatedAtDesc(userId);
        StringBuilder text = new StringBuilder("Твои привычки:\n\n");
        int index = 1;
        for (HabitTrackerEntry habit : habits) {
            text.append(index++)
                .append(". ")
                .append(habit.getHabitName())
                .append(" (")
                .append(habit.getHabitArea())
                .append(")\n")
                .append("   Срок: ")
                .append(habit.getDurationPlan())
                .append("\n")
                .append("   Частота: ")
                .append(habit.getFrequencyPlan())
                .append("\n\n");
        }

        habitDailyTaskRepository.findTopByUserIdAndTaskDateOrderByCreatedAtDesc(userId, LocalDate.now())
            .ifPresent(task -> text.append("Задание на сегодня:\n")
                .append(task.getTaskText())
                .append("\n")
                .append("Напоминание вечером: ")
                .append(Boolean.TRUE.equals(task.getRemindEvening()) ? "Да" : "Нет"));

        SendMessage message = simpleMessage(chatId, text.toString().trim());
        message.setReplyMarkup(createOverviewKeyboard());
        return message;
    }

    private SendMessage buildDeleteMenuMessage(Long chatId, Long userId) {
        List<HabitTrackerEntry> habits = habitTrackerEntryRepository.findByUserIdOrderByCreatedAtDesc(userId);
        if (habits.isEmpty()) {
            SendMessage message = simpleMessage(chatId, "У тебя пока нет привычек для удаления.");
            message.setReplyMarkup(createOverviewKeyboard());
            return message;
        }

        SendMessage message = simpleMessage(chatId, "Выбери привычку, которую нужно удалить:");
        message.setReplyMarkup(createDeleteKeyboard(habits));
        return message;
    }

    private SendMessage buildMainMenuMessage(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Выберите ассистента:");
        message.setReplyMarkup(createMainMenuKeyboard());
        return message;
    }

    private InlineKeyboardMarkup createAreaSelectionKeyboard() {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        keyboard.add(List.of(button("1️⃣ Здоровье", "habits_category:health")));
        keyboard.add(List.of(button("2️⃣ Продуктивность", "habits_category:productivity")));
        keyboard.add(List.of(button("3️⃣ Ментал", "habits_category:mental")));
        keyboard.add(List.of(button("4️⃣ Учёба", "habits_category:study")));
        keyboard.add(List.of(button("5️⃣ Финансы", "habits_category:finance")));
        keyboard.add(List.of(button("6️⃣ Свое", "habits_category:custom")));
        keyboard.add(List.of(button("◀️ Назад", "habits_back_main")));
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(keyboard);
        return markup;
    }

    private InlineKeyboardMarkup createOverviewKeyboard() {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        keyboard.add(List.of(button("➕ Добавить привычку", "habits_add")));
        keyboard.add(List.of(button("🗑 Удалить привычку", "habits_delete")));
        keyboard.add(List.of(button("◀️ Назад", "habits_back_main")));
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(keyboard);
        return markup;
    }

    private InlineKeyboardMarkup createDeleteKeyboard(List<HabitTrackerEntry> habits) {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        for (HabitTrackerEntry habit : habits) {
            keyboard.add(List.of(button(
                "Удалить: " + habit.getHabitName(),
                "habits_delete_entry:" + habit.getId()
            )));
        }
        keyboard.add(List.of(button("◀️ Назад", "main_habits_tracker")));
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(keyboard);
        return markup;
    }

    private InlineKeyboardMarkup createMainMenuKeyboard() {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        keyboard.add(List.of(button("Бизнес ассистент", "main_business_ai_assistant")));
        keyboard.add(List.of(button("Финансовый ассистент", "main_financial_ai_assistant")));
        keyboard.add(List.of(button("Ассистент по мышлению", "main_thinking_ai_assistant")));
        keyboard.add(List.of(button("Трекер привычек", "main_habits_tracker")));
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

    private InlineKeyboardMarkup createReminderChoiceKeyboard() {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        keyboard.add(List.of(button("Да", "habits_task_reminder:yes"), button("Нет", "habits_task_reminder:no")));
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
