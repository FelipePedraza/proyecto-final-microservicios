package support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class PollingTest {

    @Test
    void retornaInmediatamenteCuandoLaCondicionYaSeCumple() {
        AtomicInteger attempts = new AtomicInteger();

        String result = Polling.until(
                "resultado disponible",
                () -> {
                    attempts.incrementAndGet();
                    return "listo";
                },
                "listo"::equals,
                5,
                Duration.ZERO,
                Duration.ofSeconds(1));

        assertEquals("listo", result);
        assertEquals(1, attempts.get());
    }

    @Test
    void reintentaHastaQueLaCondicionSeCumple() {
        AtomicInteger attempts = new AtomicInteger();

        int result = Polling.until(
                "tercer intento",
                attempts::incrementAndGet,
                value -> value == 3,
                5,
                Duration.ZERO,
                Duration.ofSeconds(1));

        assertEquals(3, result);
        assertEquals(3, attempts.get());
    }

    @Test
    void toleraErroresTransitorios() {
        AtomicInteger attempts = new AtomicInteger();

        boolean result = Polling.until(
                "servicio temporalmente no disponible",
                () -> {
                    if (attempts.incrementAndGet() < 3) {
                        throw new IllegalStateException("todavía no está listo");
                    }
                    return true;
                },
                Boolean.TRUE::equals,
                4,
                Duration.ZERO,
                Duration.ofSeconds(1));

        assertTrue(result);
        assertEquals(3, attempts.get());
    }

    @Test
    void fallaConMensajeDescriptivoAlAgotarLosIntentos() {
        AtomicInteger attempts = new AtomicInteger();

        AssertionError error = assertThrows(AssertionError.class, () -> Polling.until(
                "evento de prueba",
                () -> {
                    attempts.incrementAndGet();
                    return false;
                },
                Boolean.TRUE::equals,
                3,
                Duration.ZERO,
                Duration.ofSeconds(1)));

        assertEquals(3, attempts.get());
        assertTrue(error.getMessage().contains("3 intentos"));
        assertTrue(error.getMessage().contains("evento de prueba"));
    }
}
