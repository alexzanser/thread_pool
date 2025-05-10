package ru.threadpool;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class Main {
    
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
    private static final AtomicInteger completedTasks = new AtomicInteger(0);
    
    static {
        try {
            InputStream is = Main.class.getClassLoader().getResourceAsStream("logging.properties");
            if (is != null) {
                LogManager.getLogManager().readConfiguration(is);
                is.close();
            }
        } catch (IOException e) {
            System.err.println("Could not load logging.properties file: " + e.getMessage());
        }
    }
    
    public static void main(String[] args) {
        CustomThreadPool threadPool = new CustomThreadPool(
                2,
                4,
                5,
                TimeUnit.SECONDS,
                5,
                1
        );
        
        LOGGER.info("Thread pool initialized");
        
        LOGGER.info("\n=== Submitting normal load (5 tasks) ===");
        for (int i = 1; i <= 5; i++) {
            final int taskId = i;
            threadPool.execute(createTask("Task-" + taskId, 2000));
        }
        
        sleepSeconds(3);
        LOGGER.info("Completed tasks: " + completedTasks.get());
        
        LOGGER.info("\n=== Submitting heavy load (15 tasks) ===");
        try {
            for (int i = 6; i <= 20; i++) {
                final int taskId = i;
                threadPool.execute(createTask("Task-" + taskId, 1000));
                Thread.sleep(100);
            }
        } catch (Exception e) {
            LOGGER.warning("Exception during task submission: " + e.getMessage());
        }
        
        sleepSeconds(5);
        LOGGER.info("Completed tasks: " + completedTasks.get());
        
        LOGGER.info("\n=== Initiating shutdown ===");
        threadPool.shutdown();
        
        sleepSeconds(10);
        LOGGER.info("Final completed tasks: " + completedTasks.get());
        
        LOGGER.info("\n=== Creating a second pool to test shutdownNow ===");
        CustomThreadPool secondPool = new CustomThreadPool(
                2, 4, 5, TimeUnit.SECONDS, 5, 1
        );
        
        for (int i = 1; i <= 5; i++) {
            final int taskId = i;
            secondPool.execute(createTask("SecondPool-Task-" + taskId, 3000));
        }
        
        sleepSeconds(2);
        LOGGER.info("Calling shutdownNow()");
        secondPool.shutdownNow();
        
        LOGGER.info("\n=== Demo completed ===");
    }
    
    private static Runnable createTask(String name, long duration) {
        return () -> {
            try {
                LOGGER.info("[Task] " + name + " started");
                Thread.sleep(duration);
                LOGGER.info("[Task] " + name + " completed");
                completedTasks.incrementAndGet();
            } catch (InterruptedException e) {
                LOGGER.info("[Task] " + name + " was interrupted");
            }
        };
    }
    
    private static void sleepSeconds(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
} 