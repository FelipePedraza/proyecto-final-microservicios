package support;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Predicate;

import config.E2EConfig;

/** Espera condiciones de consistencia eventual sin usar pausas fijas en los escenarios. */
public final class Polling {

    private Polling() {
    }

    @FunctionalInterface
    public interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    public interface CheckedCondition {
        boolean evaluate() throws Exception;
    }

    public static void waitUntil(String description, CheckedCondition condition) {
        until(
                description,
                condition::evaluate,
                Boolean.TRUE::equals,
                E2EConfig.pollingMaxAttempts(),
                Duration.ofMillis(E2EConfig.pollingIntervalMillis()),
                Duration.ofSeconds(E2EConfig.pollingTimeoutSeconds()));
    }

    public static <T> T until(
            String description,
            CheckedSupplier<T> supplier,
            Predicate<T> completed) {
        return until(
                description,
                supplier,
                completed,
                E2EConfig.pollingMaxAttempts(),
                Duration.ofMillis(E2EConfig.pollingIntervalMillis()),
                Duration.ofSeconds(E2EConfig.pollingTimeoutSeconds()));
    }

    public static <T> T until(
            String description,
            CheckedSupplier<T> supplier,
            Predicate<T> completed,
            int maxAttempts,
            Duration interval,
            Duration timeout) {
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(supplier, "supplier");
        Objects.requireNonNull(completed, "completed");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts debe ser mayor que cero");
        }
        if (interval.isNegative() || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("interval y timeout deben ser duraciones válidas");
        }

        Instant deadline = Instant.now().plus(timeout);
        Throwable lastError = null;
        int attemptsMade = 0;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            attemptsMade = attempt;
            try {
                T result = supplier.get();
                if (completed.test(result)) {
                    return result;
                }
            } catch (Exception | AssertionError error) {
                lastError = error;
            }

            if (attempt == maxAttempts || !Instant.now().isBefore(deadline)) {
                break;
            }

            Duration remaining = Duration.between(Instant.now(), deadline);
            if (!remaining.isPositive()) {
                break;
            }
            if (interval.isPositive()) {
                Duration delay = interval.compareTo(remaining) < 0 ? interval : remaining;
                try {
                    Thread.sleep(delay.toMillis());
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("Polling interrumpido mientras esperaba: " + description, error);
                }
            }
        }

        AssertionError timeoutError = new AssertionError(
                "Condición no cumplida tras " + attemptsMade + " intentos y un timeout de "
                        + timeout.toSeconds() + " s: " + description);
        if (lastError != null) {
            timeoutError.initCause(lastError);
        }
        throw timeoutError;
    }
}
