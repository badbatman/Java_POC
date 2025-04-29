package com.bob.test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.google.gson.Gson;

public class MemoryLoader {

    private static final int REPORT_INTERVAL_SECONDS = 5;
    private static final Gson gson = new Gson();

    private static final AtomicReference<List<byte[]>> memoryHolder = new AtomicReference<>(new ArrayList<>());
    private static volatile long currentAllocationSizeInBytes = 0;

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public static void main(String[] args) throws InterruptedException {
        String jsonInput = "{\"mUsage\": \"2000M\", \"period\": 30}";

        // Simulate receiving a request
        handleRequest(jsonInput);

        // Keep the app running long enough to observe behavior
        Thread.sleep(60000);
        scheduler.shutdown();
    }

    public static void handleRequest(String json) {
        RequestConfig config = gson.fromJson(json, RequestConfig.class);
        long requestedSizeMB = extractSizeInMB(config.mUsage);
        long requestedSizeBytes = requestedSizeMB * 1024 * 1024;
        int periodSeconds = config.period;

        // Log baseline memory before any change
        reportMemoryUsage("Before allocation");

        releaseCurrentMemory(); // Release any existing memory

        System.out.println("Allocating " + requestedSizeMB + " MB memory for " + periodSeconds + " seconds");

        List<byte[]> memoryChunks = new ArrayList<>();
        try {
            // Allocate in ~1MB chunks
            int chunkCount = (int) requestedSizeMB;
            for (int i = 0; i < chunkCount; i++) {
                byte[] chunk = new byte[1024 * 1024]; // 1MB
                memoryChunks.add(chunk);
            }
            memoryHolder.set(memoryChunks);
            currentAllocationSizeInBytes = requestedSizeBytes;
        } catch (OutOfMemoryError e) {
            System.err.println("Failed to allocate memory: Out of memory!");
            return;
        }

        // Report right after allocation
        reportMemoryUsage("After allocation");

        scheduleMemoryRelease(periodSeconds);
        scheduleUsageReporting();
    }

    private static void releaseCurrentMemory() {
        List<byte[]> oldMem = memoryHolder.getAndSet(new ArrayList<>());
        oldMem.clear(); // Help GC
        currentAllocationSizeInBytes = 0;

        // Report after releasing
        reportMemoryUsage("After memory release");
    }

    private static void scheduleMemoryRelease(int periodSeconds) {
        scheduler.schedule(() -> {
            releaseCurrentMemory();
            System.out.println("Memory automatically released after " + periodSeconds + " seconds.");
        }, periodSeconds, TimeUnit.SECONDS);
    }

    private static void scheduleUsageReporting() {
        scheduler.scheduleAtFixedRate(() -> {
            Runtime runtime = Runtime.getRuntime();
            long used = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
            long allocatedByLoader = currentAllocationSizeInBytes / (1024 * 1024);

            System.out.printf("JVM used: %d MB | Loader held: %d MB%n", used, allocatedByLoader);
        }, 0, REPORT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Reports current JVM memory usage with label
     */
    private static void reportMemoryUsage(String label) {
        Runtime rt = Runtime.getRuntime();
        long usedMB = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        System.out.printf("[%s] JVM Used Memory: %d MB%n", label, usedMB);
    }

    private static long extractSizeInMB(String mUsage) {
        if (mUsage == null || !mUsage.matches("\\d+[Mm]")) {
            throw new IllegalArgumentException("Invalid memory format. Expected format: e.g., '1000M'");
        }
        return Long.parseLong(mUsage.replaceAll("[^\\d]", ""));
    }

    private static class RequestConfig {
        String mUsage;
        int period;
    }
}