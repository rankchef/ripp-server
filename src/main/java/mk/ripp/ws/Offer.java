package mk.ripp.ws;

import java.util.UUID;

public record Offer(UUID to, UUID ripId, String sdp) implements ClientMessage {}