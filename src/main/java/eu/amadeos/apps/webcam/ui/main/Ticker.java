package eu.amadeos.apps.webcam.ui.main;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Runs things later or again and again; tests replace it with a clock they move by hand. */
public interface Ticker {
    interface Cancel {
        void cancel();
    }

    Cancel after(Duration delay, Runnable task);

    Cancel every(Duration period, Runnable task);

    /** On one daemon thread. */
    static Ticker system() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "webcam-ticker");
            t.setDaemon(true);
            return t;
        });
        return new Ticker() {
            @Override
            public Cancel after(Duration delay, Runnable task) {
                ScheduledFuture<?> f = executor.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS);
                return () -> f.cancel(false);
            }

            @Override
            public Cancel every(Duration period, Runnable task) {
                ScheduledFuture<?> f = executor.scheduleWithFixedDelay(task, period.toMillis(), period.toMillis(),
                        TimeUnit.MILLISECONDS);
                return () -> f.cancel(false);
            }
        };
    }
}
