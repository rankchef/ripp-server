package mk.ripp;

import io.javalin.Javalin;
import mk.ripp.controller.RipController;
import mk.ripp.stores.RipStore;
import mk.ripp.stores.SeedStore;
import mk.ripp.ws.TrackerSocket;
import java.nio.file.Path;
import java.util.UUID;


public class Main {
    public static void main(String[] args) {
        RipStore rips = new RipStore(Path.of("data", "ripp.db"));
        SeedStore seeds = new SeedStore();
        RipController ripController = new RipController(rips, seeds);
        TrackerSocket trackerSocket = new TrackerSocket(rips, seeds);

        Javalin.create(config -> {
            config.routes.post("/rips/create", ripController::create);
            config.routes.get("/rips", ripController::list);
            config.routes.get("/rips/{id}/seeders", ripController::seeders);
            config.routes.ws("/ws", trackerSocket::configure);
            config.events.serverStopping(rips::close);
            config.validation.register(UUID.class, UUID::fromString);
        }).start(7070);
    }
}