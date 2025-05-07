package ee.digit25.detector.monitoring;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class PerformanceProfiler {
    
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    private final AtomicLong peakMemoryUsage = new AtomicLong(0);
    private final AtomicLong peakThreadCount = new AtomicLong(0);
    private final AtomicLong totalTransactions = new AtomicLong(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);
    
    @Scheduled(fixedRate = 1000)
    public void profile() {
        // Memory profiling
        long heapUsage = memoryBean.getHeapMemoryUsage().getUsed();
        long nonHeapUsage = memoryBean.getNonHeapMemoryUsage().getUsed();
        long totalMemory = heapUsage + nonHeapUsage;
        
        // Update peaks
        peakMemoryUsage.updateAndGet(current -> Math.max(current, totalMemory));
        peakThreadCount.updateAndGet(current -> Math.max(current, threadBean.getThreadCount()));
        
        // Calculate performance metrics
        long transactions = totalTransactions.get();
        long processingTime = totalProcessingTime.get();
        double tps = transactions > 0 ? (transactions * 1000.0) / processingTime : 0;
        
        // Log performance data
        log.info("""
            Performance Profile:
            - Memory Usage: {} MB / {} MB
            - Thread Count: {} / {}
            - Transactions: {}
            - TPS: {:.2f}
            - Avg Processing Time: {} ms
            """,
            totalMemory / 1_000_000,
            peakMemoryUsage.get() / 1_000_000,
            threadBean.getThreadCount(),
            peakThreadCount.get(),
            transactions,
            tps,
            transactions > 0 ? processingTime / transactions : 0
        );
    }
    
    public void recordTransaction(long processingTime) {
        totalTransactions.incrementAndGet();
        totalProcessingTime.addAndGet(processingTime);
    }
} 