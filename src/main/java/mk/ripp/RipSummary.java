package mk.ripp;

import java.util.UUID;

public record RipSummary(UUID id, String fileName, long fileSize, int chunkSize, int totalChunks) {}