package mk.ripp.ws;

import io.javalin.websocket.WsConfig;
import io.javalin.websocket.WsContext;
import mk.ripp.model.SeederId;
import mk.ripp.stores.RipStore;
import mk.ripp.stores.SeedStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TrackerSocket {
    private static final String SEEDER_ID = "seederId";

    private final RipStore rips;
    private final SeedStore seeds;

    public TrackerSocket(RipStore rips, SeedStore seeds) {
        this.rips = rips;
        this.seeds = seeds;
    }

    public void configure(WsConfig ws) {
        ws.onConnect(ctx -> {
            ctx.enableAutomaticPings();
            ctx.attribute(SEEDER_ID, new SeederId(UUID.randomUUID()));
        });
        ws.onMessage(ctx -> {
            SeederId id = ctx.attribute(SEEDER_ID);
            switch (ctx.messageAsClass(ClientMessage.class)) {
                case Announce announce -> announce(ctx, id, announce);
            }
        });
        ws.onClose(this::disconnect);
        ws.onError(this::disconnect);
    }

    private void announce(WsContext ctx, SeederId id, Announce announce) {
        if (announce.rips() == null) {
            ctx.send(Map.of("type", "error", "message", "rips is required"));
            return;
        }
        List<UUID> missing = new ArrayList<>();
        for (UUID ripId : announce.rips()) {
            if (ripId == null || rips.find(ripId).isEmpty()) {
                missing.add(ripId);
            }
        }
        if (!missing.isEmpty()) {
            ctx.send(Map.of("type", "error", "message", "Rip not found: " + missing));
            return;
        }
        seeds.announce(id, announce.rips());
    }

    private void disconnect(WsContext ctx) {
        SeederId id = ctx.attribute(SEEDER_ID);
        if (id != null) {
            seeds.remove(id);
        }
    }
}