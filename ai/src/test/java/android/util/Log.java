package android.util;

/** JVM-only logging shim for provider entry-point tests; never packaged in the app. */
public final class Log {
    public static final int VERBOSE = 2;
    public static final int DEBUG = 3;
    public static final int INFO = 4;
    public static final int WARN = 5;
    public static final int ERROR = 6;
    public static final int ASSERT = 7;

    private Log() {}

    public static boolean isLoggable(String tag, int level) { return false; }
    public static int println(int priority, String tag, String message) { return 0; }
    public static int v(String tag, String message) { return 0; }
    public static int v(String tag, String message, Throwable error) { return 0; }
    public static int d(String tag, String message) { return 0; }
    public static int d(String tag, String message, Throwable error) { return 0; }
    public static int i(String tag, String message) { return 0; }
    public static int i(String tag, String message, Throwable error) { return 0; }
    public static int w(String tag, String message) { return 0; }
    public static int w(String tag, String message, Throwable error) { return 0; }
    public static int w(String tag, Throwable error) { return 0; }
    public static int e(String tag, String message) { return 0; }
    public static int e(String tag, String message, Throwable error) { return 0; }
}
