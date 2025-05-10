package ru.threadpool;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class CustomThreadPool implements CustomExecutor {
    
    private static final Logger LOGGER = Logger.getLogger(CustomThreadPool.class.getName());
    
    private final int corePoolSize;
    private final int maxPoolSize;
    private final long keepAliveTime;
    private final TimeUnit timeUnit;
    private final int queueSize;
    private final int minSpareThreads;
    
    private final ThreadFactory threadFactory;
    private final Set<Worker> workers;
    private final AtomicInteger workerCount = new AtomicInteger(0);
    
    private final List<BlockingQueue<Runnable>> taskQueues;
    private final AtomicInteger nextQueueIndex = new AtomicInteger(0);
    
    private final RejectedExecutionHandler rejectedExecutionHandler;
    
    private volatile boolean isShutdown = false;
    
    public CustomThreadPool(int corePoolSize, int maxPoolSize, long keepAliveTime, 
                           TimeUnit timeUnit, int queueSize, int minSpareThreads) {
        
        if (corePoolSize < 0 || maxPoolSize <= 0 || maxPoolSize < corePoolSize || keepAliveTime < 0) {
            throw new IllegalArgumentException("Invalid thread pool parameters");
        }
        
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.keepAliveTime = keepAliveTime;
        this.timeUnit = timeUnit;
        this.queueSize = queueSize;
        this.minSpareThreads = minSpareThreads;
        
        this.threadFactory = new CustomThreadFactory();
        this.workers = Collections.synchronizedSet(new HashSet<>());
        
        this.taskQueues = new ArrayList<>(corePoolSize);
        for (int i = 0; i < corePoolSize; i++) {
            taskQueues.add(new LinkedBlockingQueue<>(queueSize));
        }
        
        this.rejectedExecutionHandler = new AbortPolicy();
        
        for (int i = 0; i < corePoolSize; i++) {
            addWorker();
        }
    }
    
    @Override
    public void execute(Runnable command) {
        if (command == null) {
            throw new NullPointerException("Command cannot be null");
        }
        
        if (isShutdown) {
            rejectedExecutionHandler.rejectedExecution(command, this);
            return;
        }
        
        if (!addTaskToQueue(command)) {
            if (workerCount.get() < maxPoolSize) {
                Worker worker = addWorker();
                worker.queue.offer(command);
                LOGGER.info("[Pool] Task assigned directly to new worker: " + worker.getName());
            } else {
                rejectedExecutionHandler.rejectedExecution(command, this);
            }
        }
        
        ensureMinSpareThreads();
    }
    
    @Override
    public <T> Future<T> submit(Callable<T> callable) {
        if (callable == null) {
            throw new NullPointerException("Callable cannot be null");
        }
        
        FutureTask<T> futureTask = new FutureTask<>(callable);
        execute(futureTask);
        return futureTask;
    }
    
    @Override
    public void shutdown() {
        isShutdown = true;
        
        LOGGER.info("[Pool] Initiating shutdown, waiting for active tasks to complete");
    }
    
    @Override
    public void shutdownNow() {
        isShutdown = true;
        
        LOGGER.info("[Pool] Initiating immediate shutdown, interrupting all workers");
        for (Worker worker : workers) {
            worker.thread.interrupt();
        }
        
        for (BlockingQueue<Runnable> queue : taskQueues) {
            queue.clear();
        }
    }
    
    private boolean addTaskToQueue(Runnable command) {
        int startIndex = nextQueueIndex.getAndIncrement() % taskQueues.size();
        if (nextQueueIndex.get() > 100000) {
            nextQueueIndex.set(0);
        }
        
        for (int i = 0; i < taskQueues.size(); i++) {
            int queueIndex = (startIndex + i) % taskQueues.size();
            BlockingQueue<Runnable> queue = taskQueues.get(queueIndex);
            
            if (queue.offer(command)) {
                LOGGER.info("[Pool] Task accepted into queue #" + queueIndex + ": " + command);
                return true;
            }
        }
        
        return false;
    }
    
    private Worker addWorker() {
        if (isShutdown) {
            return null;
        }
        
        int queueIndex = workerCount.get() % taskQueues.size();
        BlockingQueue<Runnable> queue = taskQueues.get(queueIndex);
        
        Worker worker = new Worker(queue);
        Thread thread = threadFactory.newThread(worker);
        worker.thread = thread;
        
        workers.add(worker);
        workerCount.incrementAndGet();
        thread.start();
        
        return worker;
    }
    
    private void ensureMinSpareThreads() {
        long idleWorkers = workers.stream()
                .filter(worker -> !worker.isActive)
                .count();
        
        int threadsToAdd = (int) Math.min(
                minSpareThreads - idleWorkers, 
                maxPoolSize - workerCount.get()
        );
        
        for (int i = 0; i < threadsToAdd; i++) {
            if (workerCount.get() < maxPoolSize) {
                addWorker();
            }
        }
    }
    
    private class Worker implements Runnable {
        final BlockingQueue<Runnable> queue;
        Thread thread;
        volatile boolean isActive = false;
        
        Worker(BlockingQueue<Runnable> queue) {
            this.queue = queue;
        }
        
        String getName() {
            return thread != null ? thread.getName() : "Unknown";
        }
        
        @Override
        public void run() {
            try {
                while (!isShutdown || !queue.isEmpty()) {
                    Runnable task = null;
                    try {
                        if (workerCount.get() > corePoolSize) {
                            task = queue.poll(keepAliveTime, timeUnit);
                            if (task == null) {
                                LOGGER.info("[Worker] " + getName() + " idle timeout, stopping.");
                                if (workerCount.get() > corePoolSize) {
                                    break;
                                }
                                continue;
                            }
                        } else {
                            task = queue.take();
                        }
                        
                        isActive = true;
                        LOGGER.info("[Worker] " + getName() + " executes " + task);
                        task.run();
                    } catch (InterruptedException e) {
                        if (isShutdown) {
                            break;
                        }
                    } finally {
                        isActive = false;
                    }
                }
            } finally {
                boolean removed = workers.remove(this);
                if (removed) {
                    workerCount.decrementAndGet();
                }
                LOGGER.info("[Worker] " + getName() + " terminated.");
                
                if (!isShutdown && workerCount.get() < corePoolSize) {
                    addWorker();
                }
            }
        }
    }
    
    private class CustomThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix = "MyPool-worker-";
        
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + threadNumber.getAndIncrement());
            if (t.isDaemon()) {
                t.setDaemon(false);
            }
            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }
            LOGGER.info("[ThreadFactory] Creating new thread: " + t.getName());
            return t;
        }
    }
    
    public class AbortPolicy implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable r, CustomThreadPool executor) {
            LOGGER.severe("[Rejected] Task " + r + " was rejected due to overload!");
            throw new RejectedExecutionException("Task " + r + " rejected");
        }
    }
    
    public interface RejectedExecutionHandler {
        void rejectedExecution(Runnable r, CustomThreadPool executor);
    }
} 