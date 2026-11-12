package mk.ripp;

import java.util.List;
import java.util.UUID;

public record RipMetadata(
        UUID id,
        String fileName,
        long fileSize,
        int chunkSize,
        List<byte[]> chunkHashes
) {
    public int totalChunks() {
        return chunkHashes.size();
    }
}