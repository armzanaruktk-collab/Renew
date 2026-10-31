package ru.anton2319.vpnoverssh.utils;

import android.os.Handler;
import android.os.Looper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ConnectionLogger {
    private static volatile ConnectionLogger instance;
    private final List<LogEntry> logs = Collections.synchronizedList(new ArrayList<>());
    private final List<LogListener> listeners = Collections.synchronizedList(new ArrayList<>());
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    public enum Level {
        INFO, SUCCESS, WARN, ERROR
    }

    public static class LogEntry {
        public final long timestamp;
        public final Level level;
        public final String message;
        public final String timeString;

        public LogEntry(Level level, String message, String timeString) {
            this.timestamp = System.currentTimeMillis();
            this.level = level;
            this.message = message;
            this.timeString = timeString;
        }

        @Override
        public String toString() {
            return "[" + timeString + "] [" + level.name() + "] " + message;
        }
    }

    public interface LogListener {
        void onNewLog(LogEntry entry);
        void onLogsCleared();
    }

    private ConnectionLogger() {
        // Initial startup log
        log(Level.INFO, "ระบบพร้อมทำงาน (godarmxvpn v1.0.0)");
    }

    public static ConnectionLogger getInstance() {
        if (instance == null) {
            synchronized (ConnectionLogger.class) {
                if (instance == null) {
                    instance = new ConnectionLogger();
                }
            }
        }
        return instance;
    }

    public void log(Level level, String message) {
        String time = timeFormat.format(new Date());
        LogEntry entry = new LogEntry(level, message, time);
        logs.add(entry);

        // Keep last 500 lines
        if (logs.size() > 500) {
            logs.remove(0);
        }

        mainHandler.post(() -> {
            synchronized (listeners) {
                for (LogListener listener : listeners) {
                    try {
                        listener.onNewLog(entry);
                    } catch (Exception ignored) {}
                }
            }
        });
    }

    public void info(String message) {
        log(Level.INFO, message);
    }

    public void success(String message) {
        log(Level.SUCCESS, message);
    }

    public void warn(String message) {
        log(Level.WARN, message);
    }

    public void error(String message) {
        log(Level.ERROR, message);
    }

    public List<LogEntry> getLogs() {
        synchronized (logs) {
            return new ArrayList<>(logs);
        }
    }

    public String getAllLogsAsText() {
        StringBuilder sb = new StringBuilder();
        synchronized (logs) {
            for (LogEntry entry : logs) {
                sb.append(entry.toString()).append("\n");
            }
        }
        return sb.toString();
    }

    public void clear() {
        logs.clear();
        log(Level.INFO, "ล้างบันทึกการทำงานแล้ว");
        mainHandler.post(() -> {
            synchronized (listeners) {
                for (LogListener listener : listeners) {
                    try {
                        listener.onLogsCleared();
                    } catch (Exception ignored) {}
                }
            }
        });
    }

    public void addListener(LogListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(LogListener listener) {
        listeners.remove(listener);
    }
}
