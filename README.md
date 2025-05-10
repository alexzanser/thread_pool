# Custom Thread Pool Implementation

## Overview

This project implements a customizable thread pool for high-load server applications, providing fine-grained control over thread management, task queuing, and execution policies. It's designed as an alternative to the standard `ThreadPoolExecutor` with additional features.

## Key Features

- Configurable core and maximum pool sizes
- Idle timeout for worker threads
- Multiple task queues with round-robin distribution
- Minimum spare threads policy
- Detailed execution logging
- Custom rejection policies

## Implementation Details

### Thread Pool Architecture

The thread pool consists of the following key components:

1. **Worker Threads**: Each worker is associated with a specific task queue and processes tasks from it.
2. **Task Queues**: Multiple bounded queues distribute the load among workers.
3. **Thread Factory**: Creates and names worker threads.
4. **Rejection Handler**: Manages task rejection when the pool is overloaded.

### Task Distribution Mechanism

Tasks are distributed using a **Round Robin** approach:
- Each task is offered to queues in a circular order
- This prevents any single queue from becoming a bottleneck
- If all queues are full, the pool attempts to create a new worker (if below maxPoolSize)
- If no capacity is available, the rejection policy is applied

### Worker Lifecycle Management

Workers follow these lifecycle rules:
- Core threads remain alive indefinitely (unless shutdown is called)
- Non-core threads terminate after being idle for the keepAliveTime
- If the number of spare threads falls below minSpareThreads, new workers are created
- Terminated workers are automatically replaced if the pool size falls below corePoolSize

### Rejection Handling

The default rejection policy is AbortPolicy, which:
- Logs the rejection with a warning
- Throws a RejectedExecutionException
- This was chosen as it provides immediate feedback and prevents silent failures

Alternative policies could include:
- CallerRuns: Execute the task in the caller's thread
- Discard: Silently drop the task
- DiscardOldest: Remove oldest task and try again

## Performance Analysis

### Comparison with Standard ThreadPoolExecutor

The custom pool offers several advantages:
- **Multiple queues**: Reduces contention compared to a single shared queue
- **Min spare threads**: More responsive under fluctuating loads
- **Per-queue processing**: Better locality and cache efficiency

Potential disadvantages:
- Higher memory footprint due to multiple queue structures
- More complex implementation with higher maintenance overhead
- Thread creation/destruction overhead if parameters are not tuned correctly

### Optimal Configuration Parameters

Based on testing, these parameters work well for different scenarios:

**For IO-bound workloads:**
- Higher core-to-max ratio (e.g., 8:16)
- Larger queues (size 100+)
- Longer keepAliveTime (30-60 seconds)
- Low minSpareThreads (1-2)

**For CPU-bound workloads:**
- Lower core-to-max ratio (cores equal to CPU count)
- Smaller queues (size 10-20)
- Shorter keepAliveTime (5-10 seconds)
- Higher minSpareThreads (cores/4)

**For mixed workloads:**
- Medium core size (CPU count + 2)
- Medium queue size (25-50)
- Medium keepAliveTime (15-20 seconds)
- Medium minSpareThreads (2-4)

## Usage Example

```java
// Create a thread pool with specified parameters
CustomThreadPool pool = new CustomThreadPool(
    4,                     // corePoolSize
    8,                     // maxPoolSize
    30,                    // keepAliveTime
    TimeUnit.SECONDS,      // timeUnit
    50,                    // queueSize
    2                      // minSpareThreads
);

// Submit tasks
pool.execute(() -> {
    // Task implementation
});

// Submit tasks with result
Future<String> result = pool.submit(() -> {
    // Callable implementation
    return "result";
});

// Shutdown the pool
pool.shutdown();
```

## Running the Demo

The project includes a demonstration application that showcases:
1. Normal load handling
2. Overload scenarios and rejection handling
3. Proper shutdown procedures
4. Interrupted tasks behavior

To run the demo:
```
java -cp target/classes ru.threadpool.Main
```

## Future Improvements

Potential enhancements for this implementation:
- Task priority support
- Least-loaded queue distribution algorithm
- Worker thread affinity for better cache locality
- More sophisticated monitoring capabilities
- Adaptive sizing based on historical load patterns 