package ee.digit25.detector.monitoring;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class PerformanceMonitor {
    
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    private final AtomicLong peakMemoryUsage = new AtomicLong(0);
    private final AtomicLong peakThreadCount = new AtomicLong(0);

    @Scheduled(fixedRate = 1000)
    public void monitorPerformance() {
        // Monitor memory usage
        long heapUsage = memoryBean.getHeapMemoryUsage().getUsed();
        long nonHeapUsage = memoryBean.getNonHeapMemoryUsage().getUsed();
        long totalMemory = heapUsage + nonHeapUsage;
        
        // Update peaks
        peakMemoryUsage.updateAndGet(current -> Math.max(current, totalMemory));
        peakThreadCount.updateAndGet(current -> Math.max(current, threadBean.getThreadCount()));

        // Log if approaching limits
        if (totalMemory > 6_000_000_000L) { // 6GB
            log.warn("High memory usage detected: {} MB", totalMemory / 1_000_000);
        }

        if (threadBean.getThreadCount() > 30) {
            log.warn("High thread count detected: {}", threadBean.getThreadCount());
        }
    }

    @Scheduled(fixedRate = 5000)
    public void logPeakMetrics() {
        log.info("""
            Peak Performance Metrics:
            - Peak memory usage: {} MB
            - Peak thread count: {}
            - Current memory usage: {} MB
            - Current thread count: {}
            """,
            peakMemoryUsage.get() / 1_000_000,
            peakThreadCount.get(),
            (memoryBean.getHeapMemoryUsage().getUsed() + 
             memoryBean.getNonHeapMemoryUsage().getUsed()) / 1_000_000,
            threadBean.getThreadCount()
        );
    }
} 