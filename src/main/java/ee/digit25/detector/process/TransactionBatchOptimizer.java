package ee.digit25.detector.process;

import ee.digit25.detector.domain.transaction.external.api.Transaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class TransactionBatchOptimizer {
    
    private static final int OPTIMAL_BATCH_SIZE = 100;
    private static final int MAX_BATCH_SIZE = 200;
    private static final int MIN_BATCH_SIZE = 50;
    
    private final Queue<Long> processingTimes = new LinkedList<>();
    private int currentBatchSize = OPTIMAL_BATCH_SIZE;
    
    public List<List<Transaction>> optimizeBatch(List<Transaction> transactions) {
        // Group transactions by sender for parallel processing
        Map<String, List<Transaction>> senderGroups = transactions.stream()
            .collect(Collectors.groupingBy(Transaction::getSender));
            
        // Sort groups by size for optimal processing
        return senderGroups.values().stream()
            .sorted(Comparator.comparingInt(List::size))
            .collect(Collectors.toList());
    }
    
    public void updateBatchSize(long processingTime) {
        processingTimes.add(processingTime);
        if (processingTimes.size() > 10) {
            processingTimes.poll();
        }
        
        if (processingTimes.size() == 10) {
            adjustBatchSize();
        }
    }
    
    private void adjustBatchSize() {
        double avgProcessingTime = processingTimes.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0);
            
        if (avgProcessingTime < 500 && currentBatchSize < MAX_BATCH_SIZE) {
            currentBatchSize = Math.min(currentBatchSize + 10, MAX_BATCH_SIZE);
        } else if (avgProcessingTime > 1000 && currentBatchSize > MIN_BATCH_SIZE) {
            currentBatchSize = Math.max(currentBatchSize - 10, MIN_BATCH_SIZE);
        }
    }
    
    public int getCurrentBatchSize() {
        return currentBatchSize;
    }
} 