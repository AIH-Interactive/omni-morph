package top.aihmc.omnimorph.backend2.library;

import java.util.Map;
import java.util.UUID;

/** Persists visibility for the current source-bound equipment without downloading or activating a model. */
public final class LibraryEquipmentVisibility {
    private final LibraryClient client;

    public record State(UUID model, long modelRevision, long equipmentRevision, boolean equipped, boolean showing) {
    }

    public LibraryEquipmentVisibility(LibraryClient client) {
        this.client = client;
    }

    private State state(UUID model, LibraryModels.SessionInfo session) throws LibraryClient.Failure {
        if (!client.session().player().equals(session.uuid())) {
            throw new LibraryClient.Failure(0, "LIBRARY_IDENTITY_MISMATCH");
        }
        boolean equipped = model.equals(session.equipmentModelId())
                && session.equipmentModelRevision() != null && session.equipmentModelRevision() > 0;
        return new State(model, equipped ? session.equipmentModelRevision() : 0, session.equipmentRevision(),
                equipped, equipped && session.showPrivateEquipment());
    }

    public LibraryClient.Request<State> read(UUID model) {
        return client.submit(LibraryClient.Lane.CONTROL, exchange ->
                state(model, exchange.json("GET", "/api/library/v1/session", null, LibraryModels.SessionInfo.class)));
    }

    public LibraryClient.Request<State> save(State expected, boolean showing) {
        if (!expected.equipped()) {
            throw new IllegalArgumentException("LIBRARY_MODEL_NOT_EQUIPPED");
        }
        UUID operation = UUID.randomUUID();
        return client.submit(LibraryClient.Lane.CONTROL, exchange -> {
            var result = exchange.json("POST", "/api/library/v1/models/" + expected.model() + "/equip",
                    Map.of("operation_id", operation.toString(), "model_revision", expected.modelRevision(),
                            "expected_equipment_revision", expected.equipmentRevision(),
                            "show_private_equipment", showing), LibraryModels.Equipment.class);
            if (!expected.model().equals(result.modelId()) || !client.session().player().equals(result.owner())
                    || result.modelRevision() != expected.modelRevision() || result.showPrivateEquipment() != showing) {
                throw new LibraryClient.Failure(409, "EQUIPMENT_CHANGED");
            }
            State saved = state(expected.model(), exchange.json("GET", "/api/library/v1/session", null,
                    LibraryModels.SessionInfo.class));
            if (!saved.equipped() || saved.showing() != showing || saved.equipmentRevision() != result.equipmentRevision()) {
                throw new LibraryClient.Failure(409, "EQUIPMENT_CHANGED");
            }
            return saved;
        });
    }
}
