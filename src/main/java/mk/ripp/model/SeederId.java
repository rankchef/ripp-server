package mk.ripp.model;
import java.util.UUID;
public record SeederId(UUID value) {
    public SeederId {
        if (value == null) {
            throw new IllegalArgumentException("SeederId requires a value");
        }
    }
}