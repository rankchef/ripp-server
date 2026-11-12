package mk.ripp;

import io.javalin.Javalin;
import mk.ripp.controller.RipController;
import mk.ripp.stores.RipStore;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.UUID;

public class Main {
    public static void main(String[] args) {
        RipStore rips = new RipStore(Path.of("data", "ripp.db"));
        RipController ripController = new RipController(rips);
        Javalin.create(config -> {
            config.routes.post("/rips/create", ripController::create);
            config.routes.get("/rips", ripController::list);
            config.events.serverStopping(rips::close);
        }).start(7070);
    }

    record Health(String status) {}
}