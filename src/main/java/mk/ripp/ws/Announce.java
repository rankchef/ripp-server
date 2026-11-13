package mk.ripp.ws;

import java.util.List;
import java.util.UUID;

public record Announce(List<UUID> rips) implements ClientMessage {}
