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
import java.util.concurrent.ConcurrentHashMap;

public class TrackerSocket {
    private static final String SEEDER_ID = "seederId";

    private final RipStore rips;
    private final SeedStore seeds;
    private final ConcurrentHashMap<SeederId, WsContext> sockets = new ConcurrentHashMap<>();

    public TrackerSocket(RipStore rips, SeedStore seeds) {
        this.rips = rips;
        this.seeds = seeds;
    }

    public void configure(WsConfig ws) {
        ws.onConnect(ctx -> {
            ctx.enableAutomaticPings();
            SeederId id = new SeederId(UUID.randomUUID());
            ctx.attribute(SEEDER_ID, id);
            sockets.put(id, ctx);
        });
        ws.onMessage(ctx -> {
            try {
                SeederId id = ctx.attribute(SEEDER_ID);
                switch (ctx.messageAsClass(ClientMessage.class)) {
                    case Announce announce -> announce(ctx, id, announce);
                    case Offer offer -> offer(ctx, id, offer);
                    case Answer answer -> answer(ctx, id, answer);
                }
            } catch (Exception e) {
                ctx.send(Map.of("type", "error", "message", "Malformed message"));
            }
        });
        ws.onClose(this::disconnect);
        ws.onError(ctx -> {
            if (ctx.session == null || !ctx.session.isOpen()) {
                disconnect(ctx);
            }
        });
    }

    private void announce(WsContext ctx, SeederId id, Announce announce) {
        if (sockets.get(id) != ctx) {
            return;
        }
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
        if (sockets.get(id) != ctx) {
            return;
        }
        if (!missing.isEmpty()) {
            ctx.send(Map.of("type", "error", "message", "Rip not found: " + missing));
            return;
        }
        seeds.announce(id, announce.rips());
    }

    private void offer(WsContext ctx, SeederId id, Offer offer) {
        if (sockets.get(id) != ctx) {
            return;
        }
        if (offer.to() == null || offer.ripId() == null || offer.sdp() == null || offer.sdp().isBlank()) {
            ctx.send(Map.of("type", "error", "message", "offer requires to, ripId, and sdp"));
            return;
        }
        if (offer.to().equals(id.value())) {
            ctx.send(Map.of("type", "error", "message", "Cannot offer a connection to yourself"));
            return;
        }
        if (rips.find(offer.ripId()).isEmpty()) {
            ctx.send(Map.of("type", "error", "message", "Rip not found: " + offer.ripId()));
            return;
        }
        boolean seeding = seeds.seeders(offer.ripId()).contains(new SeederId(offer.to()));
        if (!seeding) {
            ctx.send(Map.of("type", "error", "message", "Seeder is not available"));
            return;
        }
        if (sockets.get(id) != ctx) {
            return;
        }
        boolean delivered = send(new SeederId(offer.to()), Map.of(
                "type", "offer",
                "from", id.value(),
                "ripId", offer.ripId(),
                "sdp", offer.sdp()
        ));
        if (!delivered && sockets.get(id) == ctx) {
            ctx.send(Map.of("type", "error", "message", "Seeder is not available"));
        }
    }

    private void answer(WsContext ctx, SeederId id, Answer answer) {
        if (sockets.get(id) != ctx) {
            return;
        }
        if (answer.to() == null || answer.sdp() == null || answer.sdp().isBlank()) {
            ctx.send(Map.of("type", "error", "message", "answer requires to and sdp"));
            return;
        }
        if (answer.to().equals(id.value())) {
            ctx.send(Map.of("type", "error", "message", "Cannot answer yourself"));
            return;
        }
        if (sockets.get(id) != ctx) {
            return;
        }
        boolean delivered = send(new SeederId(answer.to()), Map.of(
                "type", "answer",
                "from", id.value(),
                "sdp", answer.sdp()
        ));
        if (!delivered && sockets.get(id) == ctx) {
            ctx.send(Map.of("type", "error", "message", "Peer is not available"));
        }
    }

    private void disconnect(WsContext ctx) {
        SeederId id = ctx.attribute(SEEDER_ID);
        if (id == null) {
            return;
        }
        if (!sockets.remove(id, ctx)) {
            return;
        }
        seeds.remove(id);
    }

    public boolean send(SeederId id, Object message) {
        WsContext ctx = sockets.get(id);
        if (ctx == null) {
            return false;
        }
        try {
            if (ctx.session == null || !ctx.session.isOpen()) {
                disconnect(ctx);
                return false;
            }
            ctx.send(message);
            return true;
        } catch (RuntimeException e) {
            disconnect(ctx);
            return false;
        }
    }
}