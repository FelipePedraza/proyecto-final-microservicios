package context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import config.E2EConfig;
import io.restassured.response.Response;

/**
 * Estado compartido entre los pasos, los hooks y las funcionalidades de un mismo escenario.
 * PicoContainer crea una instancia nueva por escenario y la inyecta por constructor, así que
 * no hay estado que se filtre de un escenario a otro. David y Samuel reutilizan esta clase.
 */
public class TestContext {

    private final String baseUrl = E2EConfig.baseUrl();
    private final Map<String, Object> data = new HashMap<>();
    private final Map<String, List<String>> createdIds = new HashMap<>();
    private final List<Runnable> cleanupActions = new ArrayList<>();

    private String token;
    private Response lastResponse;

    public String baseUrl() {
        return baseUrl;
    }

    // ---- JWT -----------------------------------------------------------------------------------

    public Optional<String> token() {
        return Optional.ofNullable(token);
    }

    public void setToken(String token) {
        this.token = token;
    }

    public boolean hasToken() {
        return token != null && !token.isBlank();
    }

    // ---- última respuesta HTTP --------------------------------------------------------------------

    public Response lastResponse() {
        if (lastResponse == null) {
            throw new IllegalStateException("Todavía no se ha ejecutado ninguna petición HTTP en este escenario");
        }
        return lastResponse;
    }

    public void setLastResponse(Response response) {
        this.lastResponse = response;
    }

    // ---- datos compartidos -----------------------------------------------------------------------

    public void put(String key, Object value) {
        data.put(key, value);
    }

    public <T> T get(String key, Class<T> type) {
        Object value = data.get(key);
        if (value == null) {
            throw new IllegalStateException("No hay ningún dato compartido con la clave '" + key + "'");
        }
        return type.cast(value);
    }

    public boolean has(String key) {
        return data.containsKey(key);
    }

    // ---- ids creados durante el escenario (para limpiarlos al final) ----------------------------------

    public void registerCreated(String resource, String id) {
        createdIds.computeIfAbsent(resource, key -> new ArrayList<>()).add(id);
    }

    public List<String> createdIds(String resource) {
        return Collections.unmodifiableList(createdIds.getOrDefault(resource, List.of()));
    }

    /** Acción de limpieza que el hook {@code @After} ejecuta en orden inverso al de registro. */
    public void onCleanup(Runnable action) {
        cleanupActions.add(action);
    }

    public List<Runnable> cleanupActionsInReverseOrder() {
        List<Runnable> reversed = new ArrayList<>(cleanupActions);
        Collections.reverse(reversed);
        return reversed;
    }
}
