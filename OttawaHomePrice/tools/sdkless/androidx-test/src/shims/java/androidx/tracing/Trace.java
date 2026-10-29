package androidx.tracing;

/** The part of androidx.tracing.Trace that androidx.test:monitor calls. */
public final class Trace {
    private Trace() {}

    public static boolean isEnabled() {
        return android.os.Trace.isEnabled();
    }

    public static void forceEnableAppTracing() {}

    public static void beginSection(String label) {
        android.os.Trace.beginSection(label);
    }

    public static void endSection() {
        android.os.Trace.endSection();
    }
}
