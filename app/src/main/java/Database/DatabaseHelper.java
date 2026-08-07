package Database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import Models.AQIData;
import Models.ChatMessage;
import Models.ChatSession;
import Models.Exercise;
import Models.MedicalData;
import Models.Podcast;
import Models.Suggestion;
import Models.UserProfile;
import Models.Workout;
import Models.WorkoutExercise;
import Utils.ContactUtils;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "exercise.db";
    private static final int DATABASE_VERSION = 18; // v18: ethnicity, recent weight change, medication categories

    private static final String TABLE_NAME = "exercises";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_BODY_PART = "body_part";
    private static final String COLUMN_EXERCISE = "exercise";
    private static final String COLUMN_SETS = "sets";
    private static final String COLUMN_REPS = "reps";
    private static final String COLUMN_WEIGHT = "weight";

    private static final String TABLE_WORKOUTS = "workouts";
    private static final String COLUMN_WORKOUT_ID = "workout_id";
    private static final String COLUMN_WORKOUT_NAME = "workout_name";
    private static final String COLUMN_WORKOUT_DATE = "workout_date";

    private static final String TABLE_WORKOUT_EXERCISES = "workout_exercises";
    private static final String COLUMN_EXERCISE_ID = "exercise_id";

    private static final String TABLE_USER_PROFILE = "user_profile";
    private static final String COLUMN_NAME = "name";
    private static final String COLUMN_EMAIL = "email";
    private static final String COLUMN_DOB = "date_of_birth";
    private static final String COLUMN_GENDER = "gender";
    private static final String COLUMN_IS_METRIC = "is_metric";
    private static final String COLUMN_HEIGHT = "height";
    private static final String COLUMN_TARGET_WEIGHT = "target_weight";
    private static final String COLUMN_WAIST_CIRCUMFERENCE = "waist_circumference";
    private static final String COLUMN_HEART_RATE = "heart_rate";
    private static final String COLUMN_BLOOD_PRESSURE = "blood_pressure";
    private static final String COLUMN_ACTIVITY_LEVEL = "activity_level";
    private static final String COLUMN_PRIMARY_GOAL = "primary_goal";
    private static final String COLUMN_WEEKLY_GOAL = "weekly_goal";
    private static final String COLUMN_SLEEP_HOURS = "sleep_hours";
    private static final String COLUMN_DIET_TYPE = "diet_type";
    private static final String COLUMN_NOTIFICATIONS = "notifications";
    private static final String COLUMN_SHARE_PROGRESS = "share_progress";
    private static final String COLUMN_UPDATED_AT = "updated_at";
    private static final String COLUMN_CREATED_AT = "created_at";

    private static final String TABLE_CONTACTS = "contacts";
    private static final String COLUMN_CONTACT_NAME = "name";
    private static final String COLUMN_CONTACT_PHONE = "phone";
    private static final String COLUMN_CONTACT_EMAIL = "email";
    private static final String COLUMN_CONTACT_RELATIONSHIP = "relationship";
    private static final String COLUMN_IS_EMERGENCY = "is_emergency";
    private static final String COLUMN_USER_ID = "user_id";

    private static final String TABLE_CHAT_SESSIONS = "chat_sessions";
    private static final String TABLE_CHAT_MESSAGES = "chat_messages";

    private static final String COLUMN_SESSION_ID = "session_id";
    private static final String COLUMN_SESSION_TITLE = "title";
    private static final String COLUMN_SESSION_DATE = "created_at";

    private static final String COLUMN_MESSAGE_ID = "message_id";
    private static final String COLUMN_MESSAGE = "message";
    private static final String COLUMN_IS_AI = "is_ai";
    private static final String COLUMN_TIMESTAMP = "timestamp";

    private static final String TABLE_SUGGESTIONS = "chat_suggestions";

    private static final String COLUMN_TITLE = "title";
    private static final String COLUMN_LAST_MESSAGE = "last_message";
    private static final String COLUMN_MESSAGE_COUNT = "message_count";
    private static final String COLUMN_SUGGESTION_TEXT = "suggestion_text";
    private static final String COLUMN_CATEGORY = "category";
    private static final String COLUMN_USE_COUNT = "use_count";

    // Reports
    private static final String TABLE_MEDICAL_REPORTS = "medical_reports";
    private static final String COLUMN_REPORT_ID = "report_id";
    private static final String COLUMN_FILE_NAME = "file_name";
    private static final String COLUMN_FILE_TYPE = "file_type";
    private static final String COLUMN_REPORT_TYPE = "report_type";
    private static final String COLUMN_FILE_PATH = "file_path";
    private static final String COLUMN_UPLOAD_DATE = "upload_date";
    private static final String COLUMN_AI_ANALYSIS = "ai_analysis";
    private static final String COLUMN_EXTRACTED_DATA = "extracted_data";

    // Add podcasts table
    private static final String TABLE_PODCASTS = "podcasts";
    private static final String COLUMN_PODCAST_ID = "podcast_id";
    private static final String COLUMN_PODCAST_TITLE = "title";
    private static final String COLUMN_PODCAST_DESCRIPTION = "description";
    private static final String COLUMN_PODCAST_AUDIO = "audio_resource_name";
    private static final String COLUMN_PODCAST_DURATION = "duration";
    private static final String COLUMN_PODCAST_CATEGORY = "category";
    private static final String COLUMN_PODCAST_ADDED_DATE = "added_date";
    private static final String COLUMN_PODCAST_ICON = "icon_resource_id";

    // Add podcast bookmarks table
    private static final String TABLE_PODCAST_BOOKMARKS = "podcast_bookmarks";
    private static final String COLUMN_BOOKMARK_ID = "bookmark_id";
    private static final String COLUMN_PODCAST_ID_FK = "podcast_id";
    private static final String COLUMN_BOOKMARK_TIMESTAMP = "bookmark_timestamp";
    private static final String COLUMN_BOOKMARK_NOTE = "note";

    private static final String COLUMN_RESTING_HEART_RATE = "resting_heart_rate";
    private static final String COLUMN_BLOOD_TYPE = "blood_type";
    private static final String COLUMN_SYSTOLIC_BP = "systolic_bp";
    private static final String COLUMN_DIASTOLIC_BP = "diastolic_bp";
    private static final String COLUMN_MEDICAL_CONDITIONS = "medical_conditions";
    private static final String COLUMN_MEDICATIONS = "medications";
    private static final String COLUMN_ALLERGIES = "allergies";

    private static final String COLUMN_MENSTRUAL_STATUS = "menstrual_status";
    private static final String COLUMN_AVERAGE_CYCLE_LENGTH = "average_cycle_length";
    private static final String COLUMN_AVERAGE_PERIOD_LENGTH = "average_period_length";
    private static final String COLUMN_MENSTRUAL_SYMPTOMS = "menstrual_symptoms";
    private static final String COLUMN_PREGNANCY_STATUS = "pregnancy_status";
    private static final String COLUMN_CONTRACEPTION_METHOD = "contraception_method";

    // Personal / contact
    private static final String COLUMN_PHONE_NUMBER = "phone_number";
    private static final String COLUMN_LOCATION = "location";

    // Lifestyle / habits
    private static final String COLUMN_OCCUPATION_TYPE = "occupation_type";
    private static final String COLUMN_STRESS_LEVEL = "stress_level";
    private static final String COLUMN_MEALS_PER_DAY = "meals_per_day";
    private static final String COLUMN_WATER_INTAKE = "water_intake";
    private static final String COLUMN_SMOKER = "smoker";
    private static final String COLUMN_SMOKING_LEVEL = "smoking_level";
    private static final String COLUMN_SMOKING_FREQUENCY = "smoking_frequency";
    private static final String COLUMN_ALCOHOL_CONSUMPTION = "alcohol_consumption";
    private static final String COLUMN_ALCOHOL_LEVEL = "alcohol_level";
    private static final String COLUMN_CAFFEINE_HABIT = "caffeine_habit";
    private static final String COLUMN_SCREEN_TIME_BEFORE_BED = "screen_time_before_bed";
    private static final String COLUMN_SUN_EXPOSURE = "sun_exposure";
    private static final String COLUMN_FAMILY_HISTORY = "family_history";

    // Conditional follow-ups (v17)
    private static final String COLUMN_SMOKING_STATUS = "smoking_status";
    private static final String COLUMN_SMOKING_DURATION = "smoking_duration";
    private static final String COLUMN_CIGARETTES_PER_DAY = "cigarettes_per_day";
    private static final String COLUMN_LAST_SMOKED = "last_smoked";
    private static final String COLUMN_DRINKS_PER_WEEK = "drinks_per_week";
    private static final String COLUMN_CONDITIONS_DIAGNOSED = "conditions_diagnosed";
    private static final String COLUMN_CONDITIONS_MEDICATED = "conditions_medicated";
    private static final String COLUMN_FAMILY_HISTORY_RELATIVES = "family_history_relatives";

    // Predictive extras (v18)
    private static final String COLUMN_ETHNICITY = "ethnicity";
    private static final String COLUMN_RECENT_WEIGHT_CHANGE = "recent_weight_change";
    private static final String COLUMN_MEDICATION_CATEGORIES = "medication_categories";

    private static final String COLUMN_REPORT_STATUS = "status";
    private static final String COLUMN_REPORT_SERVER_ID = "server_report_id";
    private static final String COLUMN_IS_DELETED = "is_deleted";
    private static final String COLUMN_DELETED_AT = "deleted_at";

    // Define the new tables for medical data
    private static final String TABLE_MEDICAL_DATA = "medical_data";
    private static final String COLUMN_MEDICAL_DATA_ID = "id";
    private static final String COLUMN_DATA_TYPE = "data_type";
    private static final String COLUMN_RECORDED_AT = "recorded_at";
    private static final String COLUMN_SERVER_ID = "server_id";
    private static final String COLUMN_DATA_JSON = "data_json";

    // AQI Data table
    private static final String TABLE_AQI_DATA = "aqi_data";
    private static final String COLUMN_AQI_VALUE = "aqi_value";
    private static final String COLUMN_AQI_STATUS = "aqi_status";


    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createExercisesTable = "CREATE TABLE " + TABLE_NAME + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT, " +
                "category TEXT, " +
                "met REAL, " +
                "equipment TEXT, " +
                "difficulty TEXT, " +
                "description TEXT)";

        String createWorkoutsTable = "CREATE TABLE " + TABLE_WORKOUTS + " (" +
                COLUMN_WORKOUT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_WORKOUT_NAME + " TEXT NOT NULL, " +
                COLUMN_WORKOUT_DATE + " DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                COLUMN_USER_ID + " INTEGER)";  // Added user_id column

        String createWorkoutExercisesTable = "CREATE TABLE " + TABLE_WORKOUT_EXERCISES + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_WORKOUT_ID + " INTEGER, " +
                COLUMN_EXERCISE_ID + " INTEGER, " +
                COLUMN_SETS + " INTEGER, " +
                COLUMN_REPS + " INTEGER, " +
                COLUMN_WEIGHT + " REAL, " +
                "FOREIGN KEY(" + COLUMN_WORKOUT_ID + ") REFERENCES " + TABLE_WORKOUTS + "(" + COLUMN_WORKOUT_ID + "), " +
                "FOREIGN KEY(" + COLUMN_EXERCISE_ID + ") REFERENCES " + TABLE_NAME + "(" + COLUMN_ID + "))";

        String createProfileTable = "CREATE TABLE " + TABLE_USER_PROFILE + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_NAME + " TEXT, "
                + COLUMN_EMAIL + " TEXT UNIQUE, "
                + COLUMN_DOB + " TEXT, "
                + COLUMN_GENDER + " TEXT, "
                + COLUMN_IS_METRIC + " INTEGER DEFAULT 1, "
                + COLUMN_HEIGHT + " REAL, "
                + COLUMN_WEIGHT + " REAL, "
                + COLUMN_TARGET_WEIGHT + " REAL, "
                + COLUMN_WAIST_CIRCUMFERENCE + " REAL, "
                + COLUMN_RESTING_HEART_RATE + " INTEGER, "
                + COLUMN_BLOOD_TYPE + " TEXT, "
                + COLUMN_SYSTOLIC_BP + " INTEGER, "
                + COLUMN_DIASTOLIC_BP + " INTEGER, "
                + COLUMN_MEDICAL_CONDITIONS + " TEXT, "
                + COLUMN_MEDICATIONS + " TEXT, "
                + COLUMN_ALLERGIES + " TEXT, "
                + COLUMN_ACTIVITY_LEVEL + " INTEGER, "
                + COLUMN_BLOOD_PRESSURE + " TEXT, "
                + COLUMN_DIET_TYPE + " TEXT, "
                + COLUMN_PRIMARY_GOAL + " TEXT, "
                + COLUMN_WEEKLY_GOAL + " REAL, "
                + COLUMN_SLEEP_HOURS + " INTEGER, "
                + COLUMN_NOTIFICATIONS + " INTEGER DEFAULT 1, "
                + COLUMN_SHARE_PROGRESS + " INTEGER DEFAULT 0, "
                + COLUMN_MENSTRUAL_STATUS + " TEXT, "
                + COLUMN_AVERAGE_CYCLE_LENGTH + " INTEGER, "
                + COLUMN_AVERAGE_PERIOD_LENGTH + " INTEGER, "
                + COLUMN_MENSTRUAL_SYMPTOMS + " TEXT, "
                + COLUMN_PREGNANCY_STATUS + " TEXT, "
                + COLUMN_CONTRACEPTION_METHOD + " TEXT, "
                + COLUMN_PHONE_NUMBER + " TEXT, "
                + COLUMN_LOCATION + " TEXT, "
                + COLUMN_OCCUPATION_TYPE + " TEXT, "
                + COLUMN_STRESS_LEVEL + " INTEGER, "
                + COLUMN_MEALS_PER_DAY + " INTEGER, "
                + COLUMN_WATER_INTAKE + " INTEGER, "
                + COLUMN_SMOKER + " INTEGER DEFAULT 0, "
                + COLUMN_SMOKING_LEVEL + " INTEGER, "
                + COLUMN_SMOKING_FREQUENCY + " TEXT, "
                + COLUMN_ALCOHOL_CONSUMPTION + " TEXT, "
                + COLUMN_ALCOHOL_LEVEL + " INTEGER, "
                + COLUMN_CAFFEINE_HABIT + " TEXT, "
                + COLUMN_SCREEN_TIME_BEFORE_BED + " TEXT, "
                + COLUMN_SUN_EXPOSURE + " TEXT, "
                + COLUMN_FAMILY_HISTORY + " TEXT, "
                + COLUMN_SMOKING_STATUS + " TEXT, "
                + COLUMN_SMOKING_DURATION + " TEXT, "
                + COLUMN_CIGARETTES_PER_DAY + " TEXT, "
                + COLUMN_LAST_SMOKED + " TEXT, "
                + COLUMN_DRINKS_PER_WEEK + " TEXT, "
                + COLUMN_CONDITIONS_DIAGNOSED + " TEXT, "
                + COLUMN_CONDITIONS_MEDICATED + " TEXT, "
                + COLUMN_FAMILY_HISTORY_RELATIVES + " TEXT, "
                + COLUMN_ETHNICITY + " TEXT, "
                + COLUMN_RECENT_WEIGHT_CHANGE + " TEXT, "
                + COLUMN_MEDICATION_CATEGORIES + " TEXT, "
                + COLUMN_CREATED_AT + " DATETIME DEFAULT CURRENT_TIMESTAMP, "
                + COLUMN_UPDATED_AT + " DATETIME DEFAULT CURRENT_TIMESTAMP)";


        // Chat Sessions table
        String createChatSessionsTable = "CREATE TABLE " + TABLE_CHAT_SESSIONS + " ("
                + COLUMN_SESSION_ID + " TEXT PRIMARY KEY,"
                + COLUMN_TITLE + " TEXT,"
                + COLUMN_LAST_MESSAGE + " TEXT,"
                + COLUMN_MESSAGE_COUNT + " INTEGER,"
                + COLUMN_TIMESTAMP + " INTEGER,"
                + COLUMN_USER_ID + " INTEGER,"
                + "FOREIGN KEY(" + COLUMN_USER_ID + ") REFERENCES "
                + TABLE_USER_PROFILE + "(" + COLUMN_ID + "))";

        // Chat Messages table
        String createChatMessagesTable = "CREATE TABLE " + TABLE_CHAT_MESSAGES + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_SESSION_ID + " TEXT,"
                + COLUMN_MESSAGE + " TEXT,"
                + COLUMN_IS_AI + " INTEGER,"
                + COLUMN_TIMESTAMP + " INTEGER,"
                + "message_id TEXT,"  // MongoDB message ID
                + "is_saved INTEGER DEFAULT 0,"  // Save status
                + "FOREIGN KEY(" + COLUMN_SESSION_ID + ") REFERENCES "
                + TABLE_CHAT_SESSIONS + "(" + COLUMN_SESSION_ID + "))";

        // Suggestions table
        String createSuggestionsTable = "CREATE TABLE " + TABLE_SUGGESTIONS + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_SUGGESTION_TEXT + " TEXT UNIQUE,"
                + COLUMN_CATEGORY + " TEXT,"
                + COLUMN_USE_COUNT + " INTEGER)";

        // Create Podcasts table
        String createPodcastsTable = "CREATE TABLE " + TABLE_PODCASTS + " ("
                + COLUMN_PODCAST_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_PODCAST_TITLE + " TEXT, "
                + COLUMN_PODCAST_DESCRIPTION + " TEXT, "
                + COLUMN_PODCAST_AUDIO + " TEXT, "
                + COLUMN_PODCAST_DURATION + " INTEGER, "
                + COLUMN_PODCAST_CATEGORY + " TEXT, "
                + COLUMN_PODCAST_ADDED_DATE + " DATETIME DEFAULT CURRENT_TIMESTAMP, "
                + COLUMN_PODCAST_ICON + " INTEGER)";

        // Create Podcast Bookmarks table with user_id relationship
        String createPodcastBookmarksTable = "CREATE TABLE " + TABLE_PODCAST_BOOKMARKS + " ("
                + COLUMN_BOOKMARK_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_PODCAST_ID_FK + " INTEGER, "
                + COLUMN_USER_ID + " INTEGER, "
                + COLUMN_BOOKMARK_TIMESTAMP + " INTEGER, "
                + COLUMN_BOOKMARK_NOTE + " TEXT, "
                + "FOREIGN KEY(" + COLUMN_PODCAST_ID_FK + ") REFERENCES " + TABLE_PODCASTS + "(" + COLUMN_PODCAST_ID + "), "
                + "FOREIGN KEY(" + COLUMN_USER_ID + ") REFERENCES " + TABLE_USER_PROFILE + "(" + COLUMN_ID + "))";


        // Create the medical data table during database creation
       String createMedicalDataTable = "CREATE TABLE " + TABLE_MEDICAL_DATA + " ("
                    + COLUMN_MEDICAL_DATA_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + COLUMN_USER_ID + " INTEGER, "
                    + COLUMN_DATA_TYPE + " TEXT, "
                    + COLUMN_RECORDED_AT + " DATETIME, "
                    + COLUMN_CREATED_AT + " DATETIME DEFAULT CURRENT_TIMESTAMP, "
                    + COLUMN_SERVER_ID + " TEXT, "
                    + COLUMN_DATA_JSON + " TEXT, "
                    + COLUMN_IS_DELETED + " INTEGER DEFAULT 0, "
                    + COLUMN_DELETED_AT + " DATETIME, "
                    + "FOREIGN KEY(" + COLUMN_USER_ID + ") REFERENCES "
                    + TABLE_USER_PROFILE + "(" + COLUMN_ID + "))";

        // Create AQI data table
        String createAQITable = "CREATE TABLE " + TABLE_AQI_DATA + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_USER_ID + " INTEGER, "
                + COLUMN_AQI_VALUE + " INTEGER, "
                + COLUMN_AQI_STATUS + " TEXT, "
                + COLUMN_RECORDED_AT + " DATETIME, "
                + COLUMN_CREATED_AT + " DATETIME DEFAULT CURRENT_TIMESTAMP, "
                + COLUMN_SERVER_ID + " TEXT, "
                + COLUMN_IS_DELETED + " INTEGER DEFAULT 0, "
                + COLUMN_DELETED_AT + " DATETIME, "
                + "FOREIGN KEY(" + COLUMN_USER_ID + ") REFERENCES "
                + TABLE_USER_PROFILE + "(" + COLUMN_ID + "))";

        db.execSQL(createMedicalDataTable);
        db.execSQL(createAQITable);
        db.execSQL(createChatSessionsTable);
        db.execSQL(createChatMessagesTable);
        db.execSQL(createSuggestionsTable);
        db.execSQL(createProfileTable);
        db.execSQL(createExercisesTable);
        db.execSQL(createWorkoutsTable);
        db.execSQL(createWorkoutExercisesTable);
        db.execSQL(createPodcastsTable);
        db.execSQL(createPodcastBookmarksTable);
        createContactsTable(db);
        createMedicalReportsTable(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 14) {
            // Legacy reset path: schema diverged too much to migrate column-by-column
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_PODCAST_BOOKMARKS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_PODCASTS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_WORKOUT_EXERCISES);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_WORKOUTS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_USER_PROFILE);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_CONTACTS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_CHAT_MESSAGES);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_CHAT_SESSIONS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_MEDICAL_REPORTS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_SUGGESTIONS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_MEDICAL_DATA);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_AQI_DATA);
            onCreate(db);
            return;
        }
        if (oldVersion < 15) {
            // v15: additive personal/lifestyle/habits/family-history columns on user_profile
            addColumnIfMissing(db, TABLE_USER_PROFILE, COLUMN_CONTRACEPTION_METHOD, "TEXT");
            addColumnIfMissing(db, TABLE_USER_PROFILE, COLUMN_PHONE_NUMBER, "TEXT");
            addColumnIfMissing(db, TABLE_USER_PROFILE, COLUMN_LOCATION, "TEXT");
            addColumnIfMissing(db, TABLE_USER_PROFILE, COLUMN_OCCUPATION_TYPE, "TEXT");
            addColumnIfMissing(db, TABLE_USER_PROFILE, COLUMN_STRESS_LEVEL, "INTEGER DEFAULT 0");
            addColumnIfMissing(db, TABLE_USER_PROFILE, COLUMN_MEALS_PER_DAY, "INTEGER DEFAULT 0");
            addColumnIfMissing(db, TABLE_USER_PROFILE, COLUMN_WATER_INTAKE, "INTEGER DEFAULT 0");
            addColumnIfMissing(db, TABLE_USER_PROFILE, COLUMN_SMOKER, "INTEGER DEFAULT 0");
            addColumnIfMissing(db, TABLE_USER_PROFILE, COLUMN_SMOKING_LEVEL, "INTEGER DEFAULT 0");
            addColumnIfMissing(db, TABLE_USER_PROFILE, COLUMN_SMOKING_FREQUENCY, "TEXT");
            addColumnIfMissing(db, TABLE_USER_PROFILE, COLUMN_ALCOHOL_CONSUMPTION, "TEXT");
            addColumnIfMissing(db, TABLE_USER_PROFILE, COLUMN_ALCOHOL_LEVEL, "INTEGER DEFAULT 0");
            addColumnIfMissing(db, TABLE_USER_PROFILE, COLUMN_CAFFEINE_HABIT, "TEXT");
            addColumnIfMissing(db, TABLE_USER_PROFILE, COLUMN_SCREEN_TIME_BEFORE_BED, "TEXT");
            addColumnIfMissing(db, TABLE_USER_PROFILE, COLUMN_SUN_EXPOSURE, "TEXT");
            addColumnIfMissing(db, TABLE_USER_PROFILE, COLUMN_FAMILY_HISTORY, "TEXT");
        }
        if (oldVersion < 16) {
            // v16: waist circumference (cm) — powers predictive metabolic/diabetes risk
            addColumnIfMissing(db, TABLE_USER_PROFILE, COLUMN_WAIST_CIRCUMFERENCE, "REAL");
        }
        if (oldVersion < 17) {
            // v17: conditional follow-up answers
            addColumnIfMissing(db, TABLE_USER_PROFILE, COLUMN_SMOKING_STATUS, "TEXT");
            addColumnIfMissing(db, TABLE_USER_PROFILE, COLUMN_SMOKING_DURATION, "TEXT");
            addColumnIfMissing(db, TABLE_USER_PROFILE, COLUMN_CIGARETTES_PER_DAY, "TEXT");
            addColumnIfMissing(db, TABLE_USER_PROFILE, COLUMN_LAST_SMOKED, "TEXT");
            addColumnIfMissing(db, TABLE_USER_PROFILE, COLUMN_DRINKS_PER_WEEK, "TEXT");
            addColumnIfMissing(db, TABLE_USER_PROFILE, COLUMN_CONDITIONS_DIAGNOSED, "TEXT");
            addColumnIfMissing(db, TABLE_USER_PROFILE, COLUMN_CONDITIONS_MEDICATED, "TEXT");
            addColumnIfMissing(db, TABLE_USER_PROFILE, COLUMN_FAMILY_HISTORY_RELATIVES, "TEXT");
        }
        if (oldVersion < 18) {
            // v18: ethnicity, recent weight change, medication categories
            addColumnIfMissing(db, TABLE_USER_PROFILE, COLUMN_ETHNICITY, "TEXT");
            addColumnIfMissing(db, TABLE_USER_PROFILE, COLUMN_RECENT_WEIGHT_CHANGE, "TEXT");
            addColumnIfMissing(db, TABLE_USER_PROFILE, COLUMN_MEDICATION_CATEGORIES, "TEXT");
        }
    }

    private void addColumnIfMissing(SQLiteDatabase db, String table, String column, String typeAndDefault) {
        Cursor c = null;
        try {
            c = db.rawQuery("PRAGMA table_info(" + table + ")", null);
            int nameIdx = c.getColumnIndex("name");
            while (c.moveToNext()) {
                if (column.equalsIgnoreCase(c.getString(nameIdx))) return;
            }
            db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + typeAndDefault);
        } catch (Exception e) {
            Log.e("DatabaseHelper", "addColumnIfMissing failed for " + table + "." + column, e);
        } finally {
            if (c != null) c.close();
        }
    }

    // Add to onCreate():
    private void createContactsTable(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_CONTACTS + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_CONTACT_NAME + " TEXT, "
                + COLUMN_CONTACT_PHONE + " TEXT, "
                + COLUMN_CONTACT_EMAIL + " TEXT, "
                + COLUMN_CONTACT_RELATIONSHIP + " TEXT, "
                + COLUMN_IS_EMERGENCY + " INTEGER DEFAULT 0, "
                + COLUMN_USER_ID + " INTEGER)";
        db.execSQL(createTable);
    }

    // Method to create medical reports table
    private void createMedicalReportsTable(SQLiteDatabase db) {
        String createMedicalReportsTableQuery = "CREATE TABLE " + TABLE_MEDICAL_REPORTS + " (" +
                COLUMN_REPORT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_USER_ID + " INTEGER, " +
                COLUMN_FILE_NAME + " TEXT, " +
                COLUMN_FILE_TYPE + " TEXT, " +
                COLUMN_REPORT_TYPE + " TEXT, " +
                COLUMN_FILE_PATH + " TEXT, " +
                COLUMN_UPLOAD_DATE + " DATETIME, " +
                COLUMN_AI_ANALYSIS + " TEXT, " +
                COLUMN_EXTRACTED_DATA + " TEXT, " +
                COLUMN_REPORT_STATUS + " TEXT DEFAULT 'uploaded', " +
                COLUMN_REPORT_SERVER_ID + " TEXT, " +
                COLUMN_IS_DELETED + " INTEGER DEFAULT 0, " +
                COLUMN_DELETED_AT + " DATETIME, " +
                "FOREIGN KEY(" + COLUMN_USER_ID + ") REFERENCES " +
                TABLE_USER_PROFILE + "(" + COLUMN_ID + "))";

        db.execSQL(createMedicalReportsTableQuery);
    }

    // Contact methods
    public void saveContacts(long userId, List<ContactUtils.Contact> contacts) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            // Clear existing contacts
            db.delete(TABLE_CONTACTS, COLUMN_USER_ID + " = ?",
                    new String[]{String.valueOf(userId)});

            // Insert new contacts
            for (ContactUtils.Contact contact : contacts) {
                ContentValues values = new ContentValues();
                values.put(COLUMN_CONTACT_NAME, contact.name);
                values.put(COLUMN_CONTACT_PHONE, contact.phoneNumber);
                values.put(COLUMN_CONTACT_EMAIL, contact.email);
                values.put(COLUMN_CONTACT_RELATIONSHIP, contact.relationship);
                values.put(COLUMN_IS_EMERGENCY, contact.isEmergencyContact ? 1 : 0);
                values.put(COLUMN_USER_ID, userId);

                db.insert(TABLE_CONTACTS, null, values);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    // Podcast Methods
    public long insertPodcast(Podcast podcast) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COLUMN_PODCAST_TITLE, podcast.getTitle());
        values.put(COLUMN_PODCAST_DESCRIPTION, podcast.getDescription());
        values.put(COLUMN_PODCAST_AUDIO, podcast.getAudioResourceName());
        values.put(COLUMN_PODCAST_DURATION, podcast.getDuration());
        values.put(COLUMN_PODCAST_CATEGORY, podcast.getCategory());
        values.put(COLUMN_PODCAST_ICON, podcast.getIconResourceId());

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        values.put(COLUMN_PODCAST_ADDED_DATE, sdf.format(podcast.getAddedDate()));

        long podcastId = db.insert(TABLE_PODCASTS, null, values);
        db.close();
        return podcastId;
    }

    // Podcast Bookmarks methods
    public long addPodcastBookmark(long podcastId, long userId, long timestamp, String note) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COLUMN_PODCAST_ID_FK, podcastId);
        values.put(COLUMN_USER_ID, userId);
        values.put(COLUMN_BOOKMARK_TIMESTAMP, timestamp);
        values.put(COLUMN_BOOKMARK_NOTE, note);

        long bookmarkId = db.insert(TABLE_PODCAST_BOOKMARKS, null, values);
        db.close();
        return bookmarkId;
    }

    public int deleteBookmark(long bookmarkId) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rowsDeleted = db.delete(
                TABLE_PODCAST_BOOKMARKS,
                COLUMN_BOOKMARK_ID + " = ?",
                new String[]{String.valueOf(bookmarkId)}
        );
        db.close();
        return rowsDeleted;
    }

    // Exercise methods
    public void insertExercise(Exercise exercise) {
        SQLiteDatabase db = this.getWritableDatabase();

        // Check if exercise already exists
        Cursor cursor = db.query(TABLE_NAME,
                new String[]{COLUMN_ID},
                "id = ?",
                new String[]{String.valueOf(exercise.getId())},
                null, null, null);

        boolean exists = cursor.moveToFirst();
        cursor.close();

        if (!exists) {
            ContentValues values = new ContentValues();
            values.put("id", exercise.getId());  // Important: preserve the JSON ID
            values.put("name", exercise.getName());
            values.put("category", exercise.getCategory());
            values.put("met", exercise.getMet());
            values.put("equipment", exercise.getEquipment());
            values.put("difficulty", exercise.getDifficulty());
            values.put("description", exercise.getDescription());

            db.insert(TABLE_NAME, null, values);
            System.out.println("Debug - Inserted exercise: " + exercise.getName());
        }

        db.close();
    }

    // Workout methods with user relation
    public long insertWorkout(Workout workout) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_WORKOUT_NAME, workout.getName());

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        values.put(COLUMN_WORKOUT_DATE, sdf.format(workout.getDate()));

        // Add user_id if available
        if (workout.getUserId() > 0) {
            values.put(COLUMN_USER_ID, workout.getUserId());
        }

        long workoutId = db.insert(TABLE_WORKOUTS, null, values);
        db.close();
        return workoutId;
    }

    // Get all workouts (keeping this for backward compatibility)
    public List<Workout> getAllWorkouts() {
        List<Workout> workouts = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

        String selectQuery = "SELECT * FROM " + TABLE_WORKOUTS + " ORDER BY " + COLUMN_WORKOUT_DATE + " DESC";
        try (Cursor cursor = db.rawQuery(selectQuery, null)) {
            if (cursor.moveToFirst()) {
                do {
                    long workoutId = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_WORKOUT_ID));
                    String workoutName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_WORKOUT_NAME));
                    String dateStr = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_WORKOUT_DATE));

                    // Get user ID if present (may be null for old records)
                    long userId = 0;
                    int userIdColumnIndex = cursor.getColumnIndex(COLUMN_USER_ID);
                    if (userIdColumnIndex != -1 && !cursor.isNull(userIdColumnIndex)) {
                        userId = cursor.getLong(userIdColumnIndex);
                    }

                    Date date;
                    try {
                        date = sdf.parse(dateStr);
                    } catch (ParseException e) {
                        date = new Date();
                    }

                    Workout workout = new Workout(workoutId, workoutName, date);
                    workout.setUserId(userId);
                    List<WorkoutExercise> exercises = getWorkoutExercises(workoutId);
                    workout.setExercises(exercises);
                    workouts.add(workout);
                } while (cursor.moveToNext());
            }
        }
        return workouts;
    }

    public List<WorkoutExercise> getWorkoutExercises(long workoutId) {
        List<WorkoutExercise> workoutExercises = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        // Modified query to match exact column names
        String query = "SELECT we." + COLUMN_ID + ", " +
                "we." + COLUMN_WORKOUT_ID + ", " +
                "we." + COLUMN_EXERCISE_ID + ", " +
                "we." + COLUMN_SETS + ", " +
                "we." + COLUMN_REPS + ", " +
                "we." + COLUMN_WEIGHT + ", " +
                "e.id as exercise_id, " +
                "e.name, " +
                "e.category, " +
                "e.met, " +
                "e.equipment, " +
                "e.difficulty, " +
                "e.description " +
                "FROM " + TABLE_WORKOUT_EXERCISES + " we " +
                "INNER JOIN " + TABLE_NAME + " e ON we." + COLUMN_EXERCISE_ID + " = e.id " +
                "WHERE we." + COLUMN_WORKOUT_ID + " = ?";

        try (Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(workoutId)})) {
            if (cursor.moveToFirst()) {
                do {
                    try {
                        Exercise exercise = new Exercise(
                                cursor.getInt(cursor.getColumnIndexOrThrow("exercise_id")),
                                cursor.getString(cursor.getColumnIndexOrThrow("name")),
                                cursor.getString(cursor.getColumnIndexOrThrow("category")),
                                cursor.getDouble(cursor.getColumnIndexOrThrow("met")),
                                cursor.getString(cursor.getColumnIndexOrThrow("equipment")),
                                cursor.getString(cursor.getColumnIndexOrThrow("difficulty")),
                                cursor.getString(cursor.getColumnIndexOrThrow("description"))
                        );

                        WorkoutExercise workoutExercise = new WorkoutExercise(
                                cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                                exercise,
                                cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SETS)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_REPS)),
                                cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_WEIGHT))
                        );
                        workoutExercises.add(workoutExercise);
                    } catch (Exception e) {
                        System.out.println("Debug - Error loading exercise: " + e.getMessage());
                    }
                } while (cursor.moveToNext());
            }
        }
        return workoutExercises;
    }

    public void insertWorkoutExerciseMapping(long workoutId, long exerciseId, int sets, int reps, double weight) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COLUMN_WORKOUT_ID, workoutId);
        values.put(COLUMN_EXERCISE_ID, exerciseId);
        values.put(COLUMN_SETS, sets);
        values.put(COLUMN_REPS, reps);
        values.put(COLUMN_WEIGHT, weight);

        db.insert(TABLE_WORKOUT_EXERCISES, null, values);
        db.close();
    }

    /** Delete only the exercise mappings for a workout, keeping the workout row itself. */
    public void deleteWorkoutExercises(long workoutId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_WORKOUT_EXERCISES,
                COLUMN_WORKOUT_ID + " = ?",
                new String[]{String.valueOf(workoutId)});
        db.close();
    }

    public void deleteWorkout(long workoutId) {
        SQLiteDatabase db = this.getWritableDatabase();

        // First delete all workout exercises mappings
        db.delete(TABLE_WORKOUT_EXERCISES,
                COLUMN_WORKOUT_ID + " = ?",
                new String[]{String.valueOf(workoutId)});

        // Then delete the workout itself
        db.delete(TABLE_WORKOUTS,
                COLUMN_WORKOUT_ID + " = ?",
                new String[]{String.valueOf(workoutId)});

        db.close();
    }

    public void updateSessionLastMessage(String sessionId, String message) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COLUMN_LAST_MESSAGE, message);
        values.put(COLUMN_TIMESTAMP, System.currentTimeMillis());

        String whereClause = COLUMN_SESSION_ID + " = ?";
        String[] whereArgs = {sessionId};

        db.update(TABLE_CHAT_SESSIONS, values, whereClause, whereArgs);
    }

    public void incrementSessionMessageCount(String sessionId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("UPDATE " + TABLE_CHAT_SESSIONS +
                        " SET " + COLUMN_MESSAGE_COUNT + " = " + COLUMN_MESSAGE_COUNT + " + 1" +
                        " WHERE " + COLUMN_SESSION_ID + " = ?",
                new String[]{sessionId});
    }

    // Suggestion Methods
    public long saveSuggestion(Suggestion suggestion) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COLUMN_SUGGESTION_TEXT, suggestion.getText());
        values.put(COLUMN_CATEGORY, suggestion.getCategory());
        values.put(COLUMN_USE_COUNT, suggestion.getUseCount());

        return db.insert(TABLE_SUGGESTIONS, null, values);
    }

    public List<Suggestion> getFrequentSuggestions() {
        List<Suggestion> suggestions = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT * FROM " + TABLE_SUGGESTIONS +
                " ORDER BY " + COLUMN_USE_COUNT + " DESC" +
                " LIMIT 5";

        try (Cursor cursor = db.rawQuery(query, null)) {
            if (cursor.moveToFirst()) {
                do {
                    Suggestion suggestion = new Suggestion(
                            cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                            cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SUGGESTION_TEXT)),
                            cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CATEGORY)),
                            cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_USE_COUNT)),
                            true
                    );
                    suggestions.add(suggestion);
                } while (cursor.moveToNext());
            }
        }

        return suggestions;
    }

    public void incrementSuggestionUseCount(long suggestionId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("UPDATE " + TABLE_SUGGESTIONS +
                        " SET " + COLUMN_USE_COUNT + " = " + COLUMN_USE_COUNT + " + 1" +
                        " WHERE " + COLUMN_ID + " = ?",
                new String[]{String.valueOf(suggestionId)});
    }

    // User Profile Methods
    public long insertUserProfile(UserProfile profile) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = getProfileContentValues(profile);

        long id = db.insert(TABLE_USER_PROFILE, null, values);
        db.close();
        return id;
    }

    public int updateUserProfile(UserProfile profile) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = getProfileContentValues(profile);

        // Add updated timestamp
        values.put(COLUMN_UPDATED_AT, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date()));

        int rowsAffected = db.update(TABLE_USER_PROFILE, values,
                COLUMN_ID + " = ?", new String[]{String.valueOf(profile.getId())});

        if (rowsAffected == 0) {
            // If no profile exists, insert a new one
            db.insert(TABLE_USER_PROFILE, null, values);
        }

        db.close();
        return rowsAffected;
    }

    public UserProfile getUserProfile() {
        SQLiteDatabase db = this.getReadableDatabase();
        UserProfile profile = null;

        // Most recent row first — if stale duplicate rows exist (e.g. account
        // switched without logout), use the newest, which has the latest data
        // (DOB, etc.) rather than an older pre-DOB row.
        Cursor cursor = db.query(TABLE_USER_PROFILE, null, null,
                null, null, null, COLUMN_ID + " DESC");

        if (cursor != null && cursor.moveToFirst()) {
            profile = new UserProfile();

            // Required fields
            profile.setId(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)));
            profile.setName(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME)));
            profile.setEmail(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EMAIL)));

            // Optional fields - check if column exists
            int dobIndex = cursor.getColumnIndex(COLUMN_DOB);
            if (dobIndex != -1) {
                String dobString = cursor.getString(dobIndex);
                if (dobString != null) {
                    try {
                        Date dob = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dobString);
                        profile.setDateOfBirth(dob);
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }
            }

            int genderIndex = cursor.getColumnIndex(COLUMN_GENDER);
            if (genderIndex != -1) {
                profile.setGender(cursor.getString(genderIndex));
            }

            int metricIndex = cursor.getColumnIndex(COLUMN_IS_METRIC);
            if (metricIndex != -1) {
                profile.setMetric(cursor.getInt(metricIndex) == 1);
            }

            int heightIndex = cursor.getColumnIndex(COLUMN_HEIGHT);
            if (heightIndex != -1) {
                profile.setHeight(cursor.getDouble(heightIndex));
            }

            int weightIndex = cursor.getColumnIndex(COLUMN_WEIGHT);
            if (weightIndex != -1) {
                profile.setWeight(cursor.getDouble(weightIndex));
            }

            int targetWeightIndex = cursor.getColumnIndex(COLUMN_TARGET_WEIGHT);
            if (targetWeightIndex != -1) {
                profile.setTargetWeight(cursor.getDouble(targetWeightIndex));
            }

            int waistIndex = cursor.getColumnIndex(COLUMN_WAIST_CIRCUMFERENCE);
            if (waistIndex != -1) {
                profile.setWaistCircumference(cursor.getDouble(waistIndex));
            }

            // New health metrics fields
            int restingHeartRateIndex = cursor.getColumnIndex(COLUMN_RESTING_HEART_RATE);
            if (restingHeartRateIndex != -1) {
                profile.setRestingHeartRate(cursor.getInt(restingHeartRateIndex));
            }

            int bloodTypeIndex = cursor.getColumnIndex(COLUMN_BLOOD_TYPE);
            if (bloodTypeIndex != -1) {
                profile.setBloodType(cursor.getString(bloodTypeIndex));
            }

            int systolicBPIndex = cursor.getColumnIndex(COLUMN_SYSTOLIC_BP);
            if (systolicBPIndex != -1) {
                profile.setSystolicBP(cursor.getInt(systolicBPIndex));
            }

            int diastolicBPIndex = cursor.getColumnIndex(COLUMN_DIASTOLIC_BP);
            if (diastolicBPIndex != -1) {
                profile.setDiastolicBP(cursor.getInt(diastolicBPIndex));
            }

            // Parse JSON arrays for lists
            int medicalConditionsIndex = cursor.getColumnIndex(COLUMN_MEDICAL_CONDITIONS);
            if (medicalConditionsIndex != -1) {
                String medicalConditionsJson = cursor.getString(medicalConditionsIndex);
                if (medicalConditionsJson != null && !medicalConditionsJson.isEmpty()) {
                    try {
                        JSONArray jsonArray = new JSONArray(medicalConditionsJson);
                        List<String> conditions = new ArrayList<>();
                        for (int i = 0; i < jsonArray.length(); i++) {
                            conditions.add(jsonArray.getString(i));
                        }
                        profile.setMedicalConditions(conditions);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }

            int medicationsIndex = cursor.getColumnIndex(COLUMN_MEDICATIONS);
            if (medicationsIndex != -1) {
                String medicationsJson = cursor.getString(medicationsIndex);
                if (medicationsJson != null && !medicationsJson.isEmpty()) {
                    try {
                        JSONArray jsonArray = new JSONArray(medicationsJson);
                        List<String> medications = new ArrayList<>();
                        for (int i = 0; i < jsonArray.length(); i++) {
                            medications.add(jsonArray.getString(i));
                        }
                        profile.setMedications(medications);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }

            int allergiesIndex = cursor.getColumnIndex(COLUMN_ALLERGIES);
            if (allergiesIndex != -1) {
                String allergiesJson = cursor.getString(allergiesIndex);
                if (allergiesJson != null && !allergiesJson.isEmpty()) {
                    try {
                        JSONArray jsonArray = new JSONArray(allergiesJson);
                        List<String> allergies = new ArrayList<>();
                        for (int i = 0; i < jsonArray.length(); i++) {
                            allergies.add(jsonArray.getString(i));
                        }
                        profile.setAllergies(allergies);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }

            // Menstrual health
            int menstrualStatusIndex = cursor.getColumnIndex(COLUMN_MENSTRUAL_STATUS);
            if (menstrualStatusIndex != -1) {
                profile.setMenstrualStatus(cursor.getString(menstrualStatusIndex));
            }

            int cycleLengthIndex = cursor.getColumnIndex(COLUMN_AVERAGE_CYCLE_LENGTH);
            if (cycleLengthIndex != -1 && !cursor.isNull(cycleLengthIndex)) {
                profile.setAverageCycleLength(cursor.getInt(cycleLengthIndex));
            }

            int periodLengthIndex = cursor.getColumnIndex(COLUMN_AVERAGE_PERIOD_LENGTH);
            if (periodLengthIndex != -1 && !cursor.isNull(periodLengthIndex)) {
                profile.setAveragePeriodLength(cursor.getInt(periodLengthIndex));
            }

            int pregnancyStatusIndex = cursor.getColumnIndex(COLUMN_PREGNANCY_STATUS);
            if (pregnancyStatusIndex != -1) {
                profile.setPregnancyStatus(cursor.getString(pregnancyStatusIndex));
            }

            int menstrualSymptomsIndex = cursor.getColumnIndex(COLUMN_MENSTRUAL_SYMPTOMS);
            if (menstrualSymptomsIndex != -1) {
                String symptomsJson = cursor.getString(menstrualSymptomsIndex);
                if (symptomsJson != null && !symptomsJson.isEmpty()) {
                    try {
                        JSONArray jsonArray = new JSONArray(symptomsJson);
                        List<String> symptoms = new ArrayList<>();
                        for (int i = 0; i < jsonArray.length(); i++) {
                            symptoms.add(jsonArray.getString(i));
                        }
                        profile.setMenstrualSymptoms(symptoms);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }

            // Activity level
            int activityIndex = cursor.getColumnIndex(COLUMN_ACTIVITY_LEVEL);
            if (activityIndex != -1) {
                profile.setActivityLevel(cursor.getInt(activityIndex));
            }

            // Goals
            int goalIndex = cursor.getColumnIndex(COLUMN_PRIMARY_GOAL);
            if (goalIndex != -1) {
                profile.setPrimaryGoal(cursor.getString(goalIndex));
            }

            int weeklyGoalIndex = cursor.getColumnIndex(COLUMN_WEEKLY_GOAL);
            if (weeklyGoalIndex != -1) {
                profile.setWeeklyGoal(cursor.getDouble(weeklyGoalIndex));
            }

            // Lifestyle
            int dietTypeIndex = cursor.getColumnIndex(COLUMN_DIET_TYPE);
            if (dietTypeIndex != -1) {
                profile.setDietType(cursor.getString(dietTypeIndex));
            }

            int sleepHoursIndex = cursor.getColumnIndex(COLUMN_SLEEP_HOURS);
            if (sleepHoursIndex != -1) {
                profile.setSleepHours(cursor.getInt(sleepHoursIndex));
            }

            // Settings
            int notificationsIndex = cursor.getColumnIndex(COLUMN_NOTIFICATIONS);
            if (notificationsIndex != -1) {
                profile.setReceiveNotifications(cursor.getInt(notificationsIndex) == 1);
            }

            int shareProgressIndex = cursor.getColumnIndex(COLUMN_SHARE_PROGRESS);
            if (shareProgressIndex != -1) {
                profile.setShareProgress(cursor.getInt(shareProgressIndex) == 1);
            }

            // Contraception method
            int contraceptionIndex = cursor.getColumnIndex(COLUMN_CONTRACEPTION_METHOD);
            if (contraceptionIndex != -1 && !cursor.isNull(contraceptionIndex)) {
                profile.setContraceptionMethod(cursor.getString(contraceptionIndex));
            }

            // Personal / contact
            int phoneIndex = cursor.getColumnIndex(COLUMN_PHONE_NUMBER);
            if (phoneIndex != -1) profile.setPhoneNumber(cursor.getString(phoneIndex));

            int locationIndex = cursor.getColumnIndex(COLUMN_LOCATION);
            if (locationIndex != -1) profile.setLocation(cursor.getString(locationIndex));

            // Lifestyle / habits
            int occupationIndex = cursor.getColumnIndex(COLUMN_OCCUPATION_TYPE);
            if (occupationIndex != -1) profile.setOccupationType(cursor.getString(occupationIndex));

            int stressIndex = cursor.getColumnIndex(COLUMN_STRESS_LEVEL);
            if (stressIndex != -1 && !cursor.isNull(stressIndex)) profile.setStressLevel(cursor.getInt(stressIndex));

            int mealsIndex = cursor.getColumnIndex(COLUMN_MEALS_PER_DAY);
            if (mealsIndex != -1 && !cursor.isNull(mealsIndex)) profile.setMealsPerDay(cursor.getInt(mealsIndex));

            int waterIndex = cursor.getColumnIndex(COLUMN_WATER_INTAKE);
            if (waterIndex != -1 && !cursor.isNull(waterIndex)) profile.setWaterIntake(cursor.getInt(waterIndex));

            int smokerIndex = cursor.getColumnIndex(COLUMN_SMOKER);
            if (smokerIndex != -1) profile.setSmoker(cursor.getInt(smokerIndex) == 1);

            int smokingLevelIndex = cursor.getColumnIndex(COLUMN_SMOKING_LEVEL);
            if (smokingLevelIndex != -1 && !cursor.isNull(smokingLevelIndex)) profile.setSmokingLevel(cursor.getInt(smokingLevelIndex));

            int smokingFreqIndex = cursor.getColumnIndex(COLUMN_SMOKING_FREQUENCY);
            if (smokingFreqIndex != -1) profile.setSmokingFrequency(cursor.getString(smokingFreqIndex));

            int alcoholIndex = cursor.getColumnIndex(COLUMN_ALCOHOL_CONSUMPTION);
            if (alcoholIndex != -1) profile.setAlcoholConsumption(cursor.getString(alcoholIndex));

            int alcoholLevelIndex = cursor.getColumnIndex(COLUMN_ALCOHOL_LEVEL);
            if (alcoholLevelIndex != -1 && !cursor.isNull(alcoholLevelIndex)) profile.setAlcoholLevel(cursor.getInt(alcoholLevelIndex));

            int caffeineIndex = cursor.getColumnIndex(COLUMN_CAFFEINE_HABIT);
            if (caffeineIndex != -1) profile.setCaffeineHabit(cursor.getString(caffeineIndex));

            int screenTimeIndex = cursor.getColumnIndex(COLUMN_SCREEN_TIME_BEFORE_BED);
            if (screenTimeIndex != -1) profile.setScreenTimeBeforeBed(cursor.getString(screenTimeIndex));

            int sunIndex = cursor.getColumnIndex(COLUMN_SUN_EXPOSURE);
            if (sunIndex != -1) profile.setSunExposure(cursor.getString(sunIndex));

            int familyHistoryIndex = cursor.getColumnIndex(COLUMN_FAMILY_HISTORY);
            if (familyHistoryIndex != -1) {
                String json = cursor.getString(familyHistoryIndex);
                if (json != null && !json.isEmpty()) {
                    try {
                        JSONArray arr = new JSONArray(json);
                        List<String> list = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) list.add(arr.getString(i));
                        profile.setFamilyHistory(list);
                    } catch (JSONException e) { e.printStackTrace(); }
                }
            }

            int familyRelIndex = cursor.getColumnIndex(COLUMN_FAMILY_HISTORY_RELATIVES);
            if (familyRelIndex != -1) {
                String json = cursor.getString(familyRelIndex);
                if (json != null && !json.isEmpty()) {
                    try {
                        JSONArray arr = new JSONArray(json);
                        List<String> list = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) list.add(arr.getString(i));
                        profile.setFamilyHistoryRelatives(list);
                    } catch (JSONException e) { e.printStackTrace(); }
                }
            }

            // Conditional follow-up strings (v17)
            int smStatusIdx = cursor.getColumnIndex(COLUMN_SMOKING_STATUS);
            if (smStatusIdx != -1) profile.setSmokingStatus(cursor.getString(smStatusIdx));
            int smDurIdx = cursor.getColumnIndex(COLUMN_SMOKING_DURATION);
            if (smDurIdx != -1) profile.setSmokingDuration(cursor.getString(smDurIdx));
            int cigIdx = cursor.getColumnIndex(COLUMN_CIGARETTES_PER_DAY);
            if (cigIdx != -1) profile.setCigarettesPerDay(cursor.getString(cigIdx));
            int lastSmokedIdx = cursor.getColumnIndex(COLUMN_LAST_SMOKED);
            if (lastSmokedIdx != -1) profile.setLastSmoked(cursor.getString(lastSmokedIdx));
            int drinksIdx = cursor.getColumnIndex(COLUMN_DRINKS_PER_WEEK);
            if (drinksIdx != -1) profile.setDrinksPerWeek(cursor.getString(drinksIdx));
            int condDiagIdx = cursor.getColumnIndex(COLUMN_CONDITIONS_DIAGNOSED);
            if (condDiagIdx != -1) profile.setConditionsDiagnosed(cursor.getString(condDiagIdx));
            int condMedIdx = cursor.getColumnIndex(COLUMN_CONDITIONS_MEDICATED);
            if (condMedIdx != -1) profile.setConditionsMedicated(cursor.getString(condMedIdx));

            int ethnicityIdx = cursor.getColumnIndex(COLUMN_ETHNICITY);
            if (ethnicityIdx != -1) profile.setEthnicity(cursor.getString(ethnicityIdx));
            int rwcIdx = cursor.getColumnIndex(COLUMN_RECENT_WEIGHT_CHANGE);
            if (rwcIdx != -1) profile.setRecentWeightChange(cursor.getString(rwcIdx));
            int medCatIdx = cursor.getColumnIndex(COLUMN_MEDICATION_CATEGORIES);
            if (medCatIdx != -1) {
                String json = cursor.getString(medCatIdx);
                if (json != null && !json.isEmpty()) {
                    try {
                        JSONArray arr = new JSONArray(json);
                        List<String> list = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) list.add(arr.getString(i));
                        profile.setMedicationCategories(list);
                    } catch (JSONException e) { e.printStackTrace(); }
                }
            }

            // Blood pressure (old format - if it exists)
            int bpIndex = cursor.getColumnIndex(COLUMN_BLOOD_PRESSURE);
            if (bpIndex != -1) {
                String bp = cursor.getString(bpIndex);
                if (bp != null && bp.contains("/")) {
                    String[] bpParts = bp.split("/");
                    try {
                        profile.setSystolicBP(Integer.parseInt(bpParts[0]));
                        profile.setDiastolicBP(Integer.parseInt(bpParts[1]));
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        if (cursor != null) {
            cursor.close();
        }
        db.close();

        return profile;
    }

    // Update your getProfileContentValues method:
    private ContentValues getProfileContentValues(UserProfile profile) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME, profile.getName());
        values.put(COLUMN_EMAIL, profile.getEmail());

        if (profile.getDateOfBirth() != null) {
            values.put(COLUMN_DOB, new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(profile.getDateOfBirth()));
        }

        values.put(COLUMN_GENDER, profile.getGender());
        values.put(COLUMN_IS_METRIC, profile.isMetric() ? 1 : 0);
        values.put(COLUMN_HEIGHT, profile.getHeight());
        values.put(COLUMN_WEIGHT, profile.getWeight());
        values.put(COLUMN_TARGET_WEIGHT, profile.getTargetWeight());
        values.put(COLUMN_WAIST_CIRCUMFERENCE, profile.getWaistCircumference());
        values.put(COLUMN_RESTING_HEART_RATE, profile.getRestingHeartRate());
        values.put(COLUMN_BLOOD_TYPE, profile.getBloodType());
        values.put(COLUMN_SYSTOLIC_BP, profile.getSystolicBP());
        values.put(COLUMN_DIASTOLIC_BP, profile.getDiastolicBP());

        // Store lists as JSON strings
        if (profile.getMedicalConditions() != null) {
            values.put(COLUMN_MEDICAL_CONDITIONS, new JSONArray(profile.getMedicalConditions()).toString());
        }
        if (profile.getMedications() != null) {
            values.put(COLUMN_MEDICATIONS, new JSONArray(profile.getMedications()).toString());
        }
        if (profile.getAllergies() != null) {
            values.put(COLUMN_ALLERGIES, new JSONArray(profile.getAllergies()).toString());
        }

        values.put(COLUMN_BLOOD_PRESSURE, profile.getFormattedBP());
        values.put(COLUMN_ACTIVITY_LEVEL, profile.getActivityLevel());
        values.put(COLUMN_PRIMARY_GOAL, profile.getPrimaryGoal());
        values.put(COLUMN_WEEKLY_GOAL, profile.getWeeklyGoal());
        values.put(COLUMN_SLEEP_HOURS, profile.getSleepHours());
        values.put(COLUMN_DIET_TYPE, profile.getDietType());
        values.put(COLUMN_NOTIFICATIONS, profile.isReceiveNotifications() ? 1 : 0);
        values.put(COLUMN_SHARE_PROGRESS, profile.isShareProgress() ? 1 : 0);

        // Menstrual health fields
        values.put(COLUMN_MENSTRUAL_STATUS, profile.getMenstrualStatus());
        values.put(COLUMN_AVERAGE_CYCLE_LENGTH, profile.getAverageCycleLength());
        values.put(COLUMN_AVERAGE_PERIOD_LENGTH, profile.getAveragePeriodLength());
        values.put(COLUMN_PREGNANCY_STATUS, profile.getPregnancyStatus());
        values.put(COLUMN_CONTRACEPTION_METHOD, profile.getContraceptionMethod());
        if (profile.getMenstrualSymptoms() != null) {
            values.put(COLUMN_MENSTRUAL_SYMPTOMS, new JSONArray(profile.getMenstrualSymptoms()).toString());
        }

        // Personal / contact
        values.put(COLUMN_PHONE_NUMBER, profile.getPhoneNumber());
        values.put(COLUMN_LOCATION, profile.getLocation());

        // Lifestyle / habits
        values.put(COLUMN_OCCUPATION_TYPE, profile.getOccupationType());
        values.put(COLUMN_STRESS_LEVEL, profile.getStressLevel());
        values.put(COLUMN_MEALS_PER_DAY, profile.getMealsPerDay());
        values.put(COLUMN_WATER_INTAKE, profile.getWaterIntake());
        values.put(COLUMN_SMOKER, profile.isSmoker() ? 1 : 0);
        values.put(COLUMN_SMOKING_LEVEL, profile.getSmokingLevel());
        values.put(COLUMN_SMOKING_FREQUENCY, profile.getSmokingFrequency());
        values.put(COLUMN_ALCOHOL_CONSUMPTION, profile.getAlcoholConsumption());
        values.put(COLUMN_ALCOHOL_LEVEL, profile.getAlcoholLevel());
        values.put(COLUMN_CAFFEINE_HABIT, profile.getCaffeineHabit());
        values.put(COLUMN_SCREEN_TIME_BEFORE_BED, profile.getScreenTimeBeforeBed());
        values.put(COLUMN_SUN_EXPOSURE, profile.getSunExposure());
        if (profile.getFamilyHistory() != null) {
            values.put(COLUMN_FAMILY_HISTORY, new JSONArray(profile.getFamilyHistory()).toString());
        }
        if (profile.getFamilyHistoryRelatives() != null) {
            values.put(COLUMN_FAMILY_HISTORY_RELATIVES, new JSONArray(profile.getFamilyHistoryRelatives()).toString());
        }
        values.put(COLUMN_SMOKING_STATUS, profile.getSmokingStatus());
        values.put(COLUMN_SMOKING_DURATION, profile.getSmokingDuration());
        values.put(COLUMN_CIGARETTES_PER_DAY, profile.getCigarettesPerDay());
        values.put(COLUMN_LAST_SMOKED, profile.getLastSmoked());
        values.put(COLUMN_DRINKS_PER_WEEK, profile.getDrinksPerWeek());
        values.put(COLUMN_CONDITIONS_DIAGNOSED, profile.getConditionsDiagnosed());
        values.put(COLUMN_CONDITIONS_MEDICATED, profile.getConditionsMedicated());
        values.put(COLUMN_ETHNICITY, profile.getEthnicity());
        values.put(COLUMN_RECENT_WEIGHT_CHANGE, profile.getRecentWeightChange());
        if (profile.getMedicationCategories() != null) {
            values.put(COLUMN_MEDICATION_CATEGORIES, new JSONArray(profile.getMedicationCategories()).toString());
        }

        return values;
    }


    // Update insertMedicalReport to handle new fields
//    public long insertMedicalReport(long userId, UserProfile.MedicalReport report) {
//        SQLiteDatabase db = this.getWritableDatabase();
//        ContentValues values = new ContentValues();
//
//        values.put(COLUMN_USER_ID, userId);
//        values.put(COLUMN_FILE_NAME, report.getFileName());
//        values.put(COLUMN_FILE_TYPE, report.getFileType());
//        values.put(COLUMN_REPORT_TYPE, report.getReportType());
//        values.put(COLUMN_FILE_PATH, report.getFilePath());
//        values.put(COLUMN_REPORT_SERVER_ID, report.getServerReportId());
//        values.put(COLUMN_REPORT_STATUS, report.getStatus());
//
//        // Convert upload date to string
//        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
//        values.put(COLUMN_UPLOAD_DATE, dateFormat.format(report.getUploadDate()));
//
//        values.put(COLUMN_AI_ANALYSIS, report.getAiAnalysis());
//
//        // Serialize extracted data to JSON
//        if (report.getExtractedData() != null && !report.getExtractedData().isEmpty()) {
//            try {
//                JSONObject jsonData = new JSONObject();
//                for (Map.Entry<String, Object> entry : report.getExtractedData().entrySet()) {
//                    jsonData.put(entry.getKey(), entry.getValue().toString());
//                }
//                values.put(COLUMN_EXTRACTED_DATA, jsonData.toString());
//            } catch (JSONException e) {
//                e.printStackTrace();
//            }
//        }
//
//        long reportId = db.insert(TABLE_MEDICAL_REPORTS, null, values);
//        db.close();
//        return reportId;
//    }
//
//
//
//    public List<UserProfile.MedicalReport> getMedicalReportsForUser(long userId) {
//        List<UserProfile.MedicalReport> reports = new ArrayList<>();
//        SQLiteDatabase db = this.getReadableDatabase();
//
//        String query = "SELECT * FROM " + TABLE_MEDICAL_REPORTS +
//                " WHERE " + COLUMN_USER_ID + " = ? AND " +
//                COLUMN_IS_DELETED + " = 0" +  // Only get non-deleted reports
//                " ORDER BY " + COLUMN_UPLOAD_DATE + " DESC";
//
//        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(userId)});
//
//        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
//
//        if (cursor.moveToFirst()) {
//            do {
//                UserProfile.MedicalReport report = new UserProfile.MedicalReport();
//
//                report.setId(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_REPORT_ID)));
//                report.setFileName(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FILE_NAME)));
//                report.setFileType(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FILE_TYPE)));
//                report.setReportType(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_REPORT_TYPE)));
//                report.setFilePath(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FILE_PATH)));
//
//                // Parse date
//                try {
//                    String dateStr = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_UPLOAD_DATE));
//                    report.setUploadDate(dateFormat.parse(dateStr));
//                } catch (ParseException e) {
//                    report.setUploadDate(new Date());
//                }
//
//                report.setAiAnalysis(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_AI_ANALYSIS)));
//
//                // Parse extracted data
//                String extractedDataStr = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EXTRACTED_DATA));
//                if (extractedDataStr != null && !extractedDataStr.isEmpty()) {
//                    try {
//                        JSONObject jsonData = new JSONObject(extractedDataStr);
//                        Map<String, Object> extractedData = new HashMap<>();
//
//                        Iterator<String> keys = jsonData.keys();
//                        while (keys.hasNext()) {
//                            String key = keys.next();
//                            extractedData.put(key, jsonData.getString(key));
//                        }
//
//                        report.setExtractedData(extractedData);
//                    } catch (JSONException e) {
//                        e.printStackTrace();
//                    }
//                }
//
//                reports.add(report);
//            } while (cursor.moveToNext());
//        }
//
//        cursor.close();
//        db.close();
//
//        return reports;
//    }


    // Methods for Symptoms
    public long insertSymptom(MedicalData.Symptom symptom) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COLUMN_USER_ID, symptom.getUserId());
        values.put(COLUMN_DATA_TYPE, MedicalData.TYPE_SYMPTOM);
        values.put(COLUMN_RECORDED_AT, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(symptom.getRecordedAt()));

        if (symptom.getServerId() != null) {
            values.put(COLUMN_SERVER_ID, symptom.getServerId());
        }

        // Convert symptom to JSON
        try {
            JSONObject symptomJson = new JSONObject();
            symptomJson.put("name", symptom.getName());
            symptomJson.put("severity", symptom.getSeverity());
            symptomJson.put("duration", symptom.getDuration());
            symptomJson.put("description", symptom.getDescription());
            symptomJson.put("shareWithFamily", symptom.isShareWithFamily());

            values.put(COLUMN_DATA_JSON, symptomJson.toString());
        } catch (JSONException e) {
            Log.e("DatabaseHelper", "Error converting symptom to JSON", e);
            return -1;
        }

        long id = db.insert(TABLE_MEDICAL_DATA, null, values);
        db.close();
        return id;
    }

    // Methods for Health Metrics
    public long insertHealthMetric(MedicalData.HealthMetric metric) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COLUMN_USER_ID, metric.getUserId());
        values.put(COLUMN_DATA_TYPE, MedicalData.TYPE_MEASUREMENT);
        values.put(COLUMN_RECORDED_AT, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(metric.getRecordedAt()));

        if (metric.getServerId() != null) {
            values.put(COLUMN_SERVER_ID, metric.getServerId());
        }

        // Convert metric to JSON
        try {
            JSONObject metricJson = new JSONObject();
            metricJson.put("metricType", metric.getMetricType());
            metricJson.put("value", metric.getValue());
            metricJson.put("unit", metric.getUnit());
            metricJson.put("status", metric.getStatus());
            metricJson.put("notes", metric.getNotes());
            metricJson.put("shareWithFamily", metric.isShareWithFamily());

            values.put(COLUMN_DATA_JSON, metricJson.toString());
        } catch (JSONException e) {
            Log.e("DatabaseHelper", "Error converting metric to JSON", e);
            return -1;
        }

        long id = db.insert(TABLE_MEDICAL_DATA, null, values);
        db.close();
        return id;
    }

    // Methods for Period Logs
    public long insertPeriodLog(MedicalData.PeriodLog periodLog) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COLUMN_USER_ID, periodLog.getUserId());
        values.put(COLUMN_DATA_TYPE, MedicalData.TYPE_PERIOD_LOG);
        values.put(COLUMN_RECORDED_AT, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(periodLog.getStartDate()));

        if (periodLog.getServerId() != null) {
            values.put(COLUMN_SERVER_ID, periodLog.getServerId());
        }

        // Convert period log to JSON
        try {
            JSONObject periodJson = new JSONObject();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            periodJson.put("startDate", sdf.format(periodLog.getStartDate()));
            if (periodLog.getEndDate() != null) {
                periodJson.put("endDate", sdf.format(periodLog.getEndDate()));
            }
            periodJson.put("flowIntensity", periodLog.getFlowIntensity());
            periodJson.put("painLevel", periodLog.getPainLevel());
            periodJson.put("notes", periodLog.getNotes());
            periodJson.put("shareWithFamily", periodLog.isShareWithFamily());

            values.put(COLUMN_DATA_JSON, periodJson.toString());
        } catch (JSONException e) {
            Log.e("DatabaseHelper", "Error converting period log to JSON", e);
            return -1;
        }

        long id = db.insert(TABLE_MEDICAL_DATA, null, values);
        db.close();
        return id;
    }

    // Get all medical data for a user
    public List<MedicalData> getMedicalDataForUser(long userId) {
        List<MedicalData> medicalDataList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT * FROM " + TABLE_MEDICAL_DATA +
                " WHERE " + COLUMN_USER_ID + " = ? AND " +
                COLUMN_IS_DELETED + " = 0" +
                " ORDER BY " + COLUMN_RECORDED_AT + " DESC";

        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(userId)});

        if (cursor.moveToFirst()) {
            do {
                String dataType = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DATA_TYPE));
                String dataJson = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DATA_JSON));

                try {
                    JSONObject json = new JSONObject(dataJson);

                    if (MedicalData.TYPE_SYMPTOM.equals(dataType)) {
                        MedicalData.Symptom symptom = new MedicalData.Symptom();
                        symptom.setId(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_MEDICAL_DATA_ID)));
                        symptom.setUserId(userId);

                        // Parse date
                        String recordedAtStr = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_RECORDED_AT));
                        try {
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                            symptom.setRecordedAt(sdf.parse(recordedAtStr));
                        } catch (ParseException e) {
                            symptom.setRecordedAt(new Date());
                        }

                        // Set server ID if available
                        int serverIdIndex = cursor.getColumnIndex(COLUMN_SERVER_ID);
                        if (serverIdIndex != -1 && !cursor.isNull(serverIdIndex)) {
                            symptom.setServerId(cursor.getString(serverIdIndex));
                        }

                        // Parse symptom details
                        symptom.setName(json.getString("name"));
                        symptom.setSeverity(json.getInt("severity"));
                        symptom.setDuration(json.getString("duration"));
                        symptom.setDescription(json.optString("description", ""));
                        symptom.setShareWithFamily(json.optBoolean("shareWithFamily", false));

                        medicalDataList.add(symptom);

                    } else if (MedicalData.TYPE_MEASUREMENT.equals(dataType)) {
                        MedicalData.HealthMetric metric = new MedicalData.HealthMetric();
                        metric.setId(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_MEDICAL_DATA_ID)));
                        metric.setUserId(userId);

                        // Parse date
                        String recordedAtStr = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_RECORDED_AT));
                        try {
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                            metric.setRecordedAt(sdf.parse(recordedAtStr));
                        } catch (ParseException e) {
                            metric.setRecordedAt(new Date());
                        }

                        // Set server ID if available
                        int serverIdIndex = cursor.getColumnIndex(COLUMN_SERVER_ID);
                        if (serverIdIndex != -1 && !cursor.isNull(serverIdIndex)) {
                            metric.setServerId(cursor.getString(serverIdIndex));
                        }

                        // Parse metric details
                        metric.setMetricType(json.getString("metricType"));
                        metric.setValue(json.getDouble("value"));
                        metric.setUnit(json.getString("unit"));
                        metric.setStatus(json.optString("status", "normal"));
                        metric.setNotes(json.optString("notes", ""));
                        metric.setShareWithFamily(json.optBoolean("shareWithFamily", false));

                        medicalDataList.add(metric);

                    } else if (MedicalData.TYPE_PERIOD_LOG.equals(dataType)) {
                        MedicalData.PeriodLog periodLog = new MedicalData.PeriodLog();
                        periodLog.setId(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_MEDICAL_DATA_ID)));
                        periodLog.setUserId(userId);

                        // Parse date
                        String recordedAtStr = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_RECORDED_AT));
                        try {
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                            periodLog.setRecordedAt(sdf.parse(recordedAtStr));
                        } catch (ParseException e) {
                            periodLog.setRecordedAt(new Date());
                        }

                        // Set server ID if available
                        int serverIdIndex = cursor.getColumnIndex(COLUMN_SERVER_ID);
                        if (serverIdIndex != -1 && !cursor.isNull(serverIdIndex)) {
                            periodLog.setServerId(cursor.getString(serverIdIndex));
                        }

                        // Parse period log details
                        SimpleDateFormat isoSdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
                        isoSdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                        try {
                            periodLog.setStartDate(isoSdf.parse(json.getString("startDate")));
                        } catch (Exception e) {
                            periodLog.setStartDate(periodLog.getRecordedAt());
                        }
                        if (json.has("endDate") && !json.isNull("endDate")) {
                            try {
                                periodLog.setEndDate(isoSdf.parse(json.getString("endDate")));
                            } catch (Exception e) {
                                // ignore
                            }
                        }
                        periodLog.setFlowIntensity(json.optString("flowIntensity", "medium"));
                        periodLog.setPainLevel(json.optInt("painLevel", 3));
                        periodLog.setNotes(json.optString("notes", ""));
                        periodLog.setShareWithFamily(json.optBoolean("shareWithFamily", false));

                        medicalDataList.add(periodLog);
                    }

                } catch (JSONException e) {
                    Log.e("DatabaseHelper", "Error parsing medical data JSON", e);
                }

            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return medicalDataList;
    }

    // Update existing medical data
    public int updateMedicalData(MedicalData data) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COLUMN_RECORDED_AT, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(data.getRecordedAt()));

        if (data.getServerId() != null) {
            values.put(COLUMN_SERVER_ID, data.getServerId());
        }

        // Convert data to JSON based on type
        try {
            JSONObject dataJson = new JSONObject();

            if (data instanceof MedicalData.Symptom) {
                MedicalData.Symptom symptom = (MedicalData.Symptom) data;
                dataJson.put("name", symptom.getName());
                dataJson.put("severity", symptom.getSeverity());
                dataJson.put("duration", symptom.getDuration());
                dataJson.put("description", symptom.getDescription());
                dataJson.put("shareWithFamily", symptom.isShareWithFamily());

            } else if (data instanceof MedicalData.HealthMetric) {
                MedicalData.HealthMetric metric = (MedicalData.HealthMetric) data;
                dataJson.put("metricType", metric.getMetricType());
                dataJson.put("value", metric.getValue());
                dataJson.put("unit", metric.getUnit());
                dataJson.put("status", metric.getStatus());
                dataJson.put("notes", metric.getNotes());
                dataJson.put("shareWithFamily", metric.isShareWithFamily());
            }

            values.put(COLUMN_DATA_JSON, dataJson.toString());

        } catch (JSONException e) {
            Log.e("DatabaseHelper", "Error converting data to JSON", e);
            return 0;
        }

        int rowsUpdated = db.update(TABLE_MEDICAL_DATA, values,
                COLUMN_MEDICAL_DATA_ID + " = ?",
                new String[]{String.valueOf(data.getId())});

        db.close();
        return rowsUpdated;
    }

    // Soft delete medical data
    public int softDeleteMedicalData(long dataId) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COLUMN_IS_DELETED, 1);
        values.put(COLUMN_DELETED_AT, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date()));

        int rowsUpdated = db.update(TABLE_MEDICAL_DATA, values,
                COLUMN_MEDICAL_DATA_ID + " = ?",
                new String[]{String.valueOf(dataId)});

        db.close();
        return rowsUpdated;
    }

    // AQI Data Methods
    public long insertAQIData(AQIData aqiData) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COLUMN_USER_ID, aqiData.getUserId());
        values.put(COLUMN_AQI_VALUE, aqiData.getAqiValue());
        values.put(COLUMN_AQI_STATUS, aqiData.getStatus());

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        values.put(COLUMN_RECORDED_AT, sdf.format(aqiData.getRecordedAt()));

        if (aqiData.getServerId() != null) {
            values.put(COLUMN_SERVER_ID, aqiData.getServerId());
        }

        long id = db.insert(TABLE_AQI_DATA, null, values);
        db.close();
        return id;
    }

    public List<AQIData> getAQIHistoryForUser(long userId) {
        List<AQIData> aqiHistory = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

        String query = "SELECT * FROM " + TABLE_AQI_DATA +
                " WHERE " + COLUMN_USER_ID + " = ? AND " +
                COLUMN_IS_DELETED + " = 0" +
                " ORDER BY " + COLUMN_RECORDED_AT + " DESC";

        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(userId)});

        if (cursor.moveToFirst()) {
            do {
                try {
                    int id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID));
                    int aqiValue = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_AQI_VALUE));
                    String status = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_AQI_STATUS));
                    String recordedAtStr = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_RECORDED_AT));
                    int serverIdIndex = cursor.getColumnIndex(COLUMN_SERVER_ID);
                    String serverId = serverIdIndex != -1 ? cursor.getString(serverIdIndex) : null;

                    Date recordedAt = sdf.parse(recordedAtStr);

                    AQIData aqiData = new AQIData(id, userId, aqiValue, status, recordedAt, serverId);
                    aqiHistory.add(aqiData);
                } catch (ParseException e) {
                    Log.e("DatabaseHelper", "Error parsing AQI date", e);
                }
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return aqiHistory;
    }


    /** Clear all user-specific data. Called on logout so next user doesn't see stale data. */
    public void clearUserData() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_MEDICAL_DATA, null, null);
        db.delete(TABLE_AQI_DATA, null, null);
        db.delete(TABLE_USER_PROFILE, null, null);
        db.delete(TABLE_CHAT_MESSAGES, null, null);
        db.delete(TABLE_CHAT_SESSIONS, null, null);
        db.delete(TABLE_SUGGESTIONS, null, null);
        db.delete(TABLE_MEDICAL_REPORTS, null, null);
        db.close();
    }
}