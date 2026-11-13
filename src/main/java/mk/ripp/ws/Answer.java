package mk.ripp.ws;

import java.util.UUID;

public record Answer(UUID to, String sdp) implements ClientMessage {}