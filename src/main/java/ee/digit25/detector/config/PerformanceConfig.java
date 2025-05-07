package ee.digit25.detector.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

@Configuration
@EnableScheduling
public class PerformanceConfig {
    
    private static final int MAX_CONCURRENT_REQUESTS = 45; // Leave some headroom from the 50 limit
    private static final int MAX_PENDING_TRANSACTIONS = 9000; // Leave headroom from 10000 limit
    private static final int OPTIMAL_BATCH_SIZE = 100; // Optimal batch size for our resources
    
    @Bean
    public Semaphore apiRequestSemaphore() {
        return new Semaphore(MAX_CONCURRENT_REQUESTS);
    }
    
    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4); // 2 vCPU * 2 threads per CPU
        executor.setMaxPoolSize(8);  // Allow some burst
        executor.setQueueCapacity(MAX_PENDING_TRANSACTIONS);
        executor.setThreadNamePrefix("TransactionProcessor-");
        executor.initialize();
        return executor;
    }
    
    @Bean
    public int optimalBatchSize() {
        return OPTIMAL_BATCH_SIZE;
    }
} 