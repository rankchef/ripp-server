package mk.ripp.controller;

import mk.ripp.RipMetadata;
import mk.ripp.stores.RipStore;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;

import java.util.List;
import java.util.UUID;

public class RipController {
    private static final int MAX_FILE_NAME_LENGTH = 255;
    private static final int LATEST_LIMIT = 10;


    private final RipStore rips;

    public RipController(RipStore rips){
        this.rips = rips;
    }

    public void create(Context ctx){
        CreateRip body = readBody(ctx);
        String fileName = fileName(body.fileName());
        int chunkSize = chunkSize(body.chunkSize());
        long fileSize = fileSize(body.fileSize());
        List<byte[]> hashes = hashes(body.chunkHashes(), fileSize, chunkSize);
        RipMetadata rip = new RipMetadata(UUID.randomUUID(), fileName, fileSize, chunkSize, hashes);
        rips.insert(rip);
        ctx.status(201).json(rip);
    }

    public void list(Context ctx) {
        ctx.json(rips.latest(LATEST_LIMIT));
    }

    private static CreateRip readBody(Context ctx) {
        try {
            CreateRip body = ctx.bodyAsClass(CreateRip.class);
            if (body == null) {
                throw new BadRequestResponse("Request body is required");
            }
            return body;
        } catch (BadRequestResponse e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestResponse("Request body must be valid JSON");
        }
    }

    private static String fileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new BadRequestResponse("fileName is required");
        }
        String trimmed = fileName.trim();
        if (trimmed.length() > MAX_FILE_NAME_LENGTH) {
            throw new BadRequestResponse("fileName must be at most " + MAX_FILE_NAME_LENGTH + " characters");
        }
        return trimmed;
    }

    private static long fileSize(long fileSize) {
        if (fileSize < 0) {
            throw new BadRequestResponse("fileSize must be >= 0");
        }
        return fileSize;
    }

    private static int chunkSize(int chunkSize) {
        if (chunkSize <= 0) {
            throw new BadRequestResponse("chunkSize must be positive");
        }
        return chunkSize;
    }

    private static List<byte[]> hashes(List<byte[]> hashes, long fileSize, int chunkSize) {
        if (hashes == null) {
            throw new BadRequestResponse("chunkHashes is required");
        }
        long expected = fileSize == 0 ? 0 : Math.ceilDiv(fileSize, chunkSize);
        if (expected > Integer.MAX_VALUE || hashes.size() != expected) {
            throw new BadRequestResponse("chunkHashes must contain " + expected + " hashes");
        }
        for (int i = 0; i < hashes.size(); i++) {
            byte[] hash = hashes.get(i);
            if (hash == null || hash.length != RipStore.HASH_LENGTH) {
                throw new BadRequestResponse("chunkHashes[" + i + "] must be " + RipStore.HASH_LENGTH + " bytes");
            }
        }
        return List.copyOf(hashes);
    }

    public record CreateRip(String fileName, long fileSize, int chunkSize, List<byte[]> chunkHashes) {}
}
