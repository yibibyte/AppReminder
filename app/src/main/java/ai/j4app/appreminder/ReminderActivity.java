package ai.j4app.appreminder;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class ReminderActivity extends AppCompatActivity {
    private EditText titleEditText, descriptionEditText;
    private TimePicker timePicker;
    private Button addButton;
    private RecyclerView remindersRecyclerView;
    private ReminderAdapter adapter;
    private AppDatabase db;
    private ReminderDao reminderDao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reminder);

        // Инициализация базы данных
//        db = Room.databaseBuilder(getApplicationContext(),
//                        AppDatabase.class, "reminder-database")
//                .build();

        //не беспокоитесь о сохранении данных между обновлениями
        db = Room.databaseBuilder(getApplicationContext(),
                        AppDatabase.class, "reminder-database")
                .fallbackToDestructiveMigration() // Для разработки - пересоздает БД при изменениях
                .build();

        reminderDao = db.reminderDao();


        // Initialize views
        titleEditText = findViewById(R.id.titleEditText);
        descriptionEditText = findViewById(R.id.descriptionEditText);
        timePicker = findViewById(R.id.timePicker);
        addButton = findViewById(R.id.addButton);
        remindersRecyclerView = findViewById(R.id.remindersRecyclerView);

        // Setup RecyclerView
        remindersRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ReminderAdapter(this, reminderDao);
        remindersRecyclerView.setAdapter(adapter);


        // Load reminders
        loadReminders();

        // Обработчик кнопки добавления
        addButton.setOnClickListener(v -> addReminder());
    }
    // Метод для установки слушателя

    private void showEditDialog(Reminder reminder) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_edit_reminder, null);

        EditText titleEditText = view.findViewById(R.id.editTitle);
        EditText descEditText = view.findViewById(R.id.editDescription);
        TimePicker timePicker = view.findViewById(R.id.editTimePicker);

        titleEditText.setText(reminder.getTitle());
        descEditText.setText(reminder.getDescription());

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(reminder.getReminderTime());
        timePicker.setHour(calendar.get(Calendar.HOUR_OF_DAY));
        timePicker.setMinute(calendar.get(Calendar.MINUTE));

        builder.setView(view)
                .setTitle("Edit Reminder")
                .setPositiveButton("Save", (dialog, which) -> {
                    String newTitle = titleEditText.getText().toString();
                    String newDesc = descEditText.getText().toString();

                    Calendar newCalendar = Calendar.getInstance();
                    newCalendar.set(Calendar.HOUR_OF_DAY, timePicker.getHour());
                    newCalendar.set(Calendar.MINUTE, timePicker.getMinute());
                    newCalendar.set(Calendar.SECOND, 0);

                    reminder.setTitle(newTitle);
                    reminder.setDescription(newDesc);
                    reminder.setReminderTime(newCalendar.getTime());

                    new Thread(() -> {
                        reminderDao.update(reminder);
                        runOnUiThread(this::loadReminders);
                    }).start();
                })
                .setNegativeButton("Cancel", null)
                .create()
                .show();
    }
    private void loadReminders() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Reminder> reminders = reminderDao.getAll();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        adapter.setReminders(reminders);
                    }
                });
            }
        }).start();
    }

    private void addReminder() {
        String title = titleEditText.getText().toString().trim();
        String description = descriptionEditText.getText().toString().trim();

        if (title.isEmpty()) {
            Toast.makeText(this, "Введите название напоминания", Toast.LENGTH_SHORT).show();
            return;
        }

        // Get time from TimePicker
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, timePicker.getHour());
        calendar.set(Calendar.MINUTE, timePicker.getMinute());
        calendar.set(Calendar.SECOND, 0);

        Date reminderTime = calendar.getTime();

        final Reminder reminder = new Reminder(title, description, reminderTime);

        new Thread(new Runnable() {
            @Override
            public void run() {
                reminderDao.insert(reminder);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        loadReminders();
                        scheduleNotification(reminder);
                        titleEditText.setText("");
                        descriptionEditText.setText("");
                    }
                });
            }
        }).start();
    }

    private void scheduleNotification(Reminder reminder) {
        Intent intent = new Intent(this, ReminderNotificationReceiver.class);
        intent.putExtra("title", reminder.getTitle());
        intent.putExtra("description", reminder.getDescription());
        intent.putExtra("id", reminder.getId());

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                reminder.getId(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Для Android 12+ проверяем разрешение
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExact(
                            AlarmManager.RTC_WAKEUP,
                            reminder.getReminderTime().getTime(),
                            pendingIntent
                    );
                } else {
                    // Если разрешения нет, запрашиваем его
                    Intent permissionIntent = new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                    startActivity(permissionIntent);
                    Toast.makeText(this, "Please grant exact alarm permission", Toast.LENGTH_LONG).show();
                }
            } else {
                // Для версий ниже Android 12 просто устанавливаем аларм
                alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        reminder.getReminderTime().getTime(),
                        pendingIntent
                );
            }
        } catch (SecurityException e) {
            e.printStackTrace();
            Toast.makeText(this, "Could not schedule exact alarm: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}