package com.skillnoob.dh.benchmark.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class LogMonitor implements AutoCloseable {
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private final Thread readerThread;

    /**
     * Creates a LogMonitor with specified debug mode.
     */
    public LogMonitor(BufferedReader reader, boolean debugMode) {
        readerThread = new Thread(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (debugMode) {
                        System.out.println(line);
                    }
                    queue.offer(line);
                }
            } catch (IOException e) {
                System.err.println("LogMonitor error:");
                e.printStackTrace();
            }
        }, "LogMonitor-Thread");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    /**
     * Polls for the next line, waiting up to the given timeout.
     */
    public String pollLine(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }

    @Override
    public void close() {
        readerThread.interrupt();
    }
}
