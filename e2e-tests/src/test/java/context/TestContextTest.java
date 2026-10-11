package context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class TestContextTest {

    @Test
    void dosEscenariosNoCompartenTokenDatosNiRecursosCreados() {
        TestContext firstScenario = new TestContext();
        TestContext secondScenario = new TestContext();

        firstScenario.setToken("token-del-primer-escenario");
        firstScenario.put("empleadoId", "EMP-1");
        firstScenario.registerCreated("empleados", "EMP-1");

        assertFalse(secondScenario.hasToken());
        assertFalse(secondScenario.has("empleadoId"));
        assertEquals(List.of(), secondScenario.createdIds("empleados"));
    }

    @Test
    void lasLimpiezasSeExponenEnOrdenInversoAlSetup() {
        TestContext context = new TestContext();
        List<String> executionOrder = new ArrayList<>();

        context.onCleanup(() -> executionOrder.add("departamento"));
        context.onCleanup(() -> executionOrder.add("empleado"));
        context.cleanupActionsInReverseOrder().forEach(Runnable::run);

        assertEquals(List.of("empleado", "departamento"), executionOrder);
    }

    @Test
    void laListaDeRecursosCreadosNoPuedeModificarseDesdeUnStep() {
        TestContext context = new TestContext();
        context.registerCreated("empleados", "EMP-1");

        assertThrows(UnsupportedOperationException.class,
                () -> context.createdIds("empleados").add("EMP-2"));
    }
}
