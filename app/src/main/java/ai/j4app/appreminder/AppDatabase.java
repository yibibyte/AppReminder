package ai.j4app.appreminder;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {Reminder.class}, version = 1, exportSchema = true) // Отключить экспорт схемы (если не нужен) false
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {
    public abstract ReminderDao reminderDao();

    // Добавьте миграцию если нужно сохранить данные
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Для простого добавления столбца:
            database.execSQL("ALTER TABLE reminders ADD COLUMN reminder_time INTEGER");
        }
    };
}