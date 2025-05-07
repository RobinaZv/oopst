package ee.digit25.detector.process;

import ee.digit25.detector.domain.transaction.TransactionValidator;
import ee.digit25.detector.domain.transaction.external.TransactionRequester;
import ee.digit25.detector.domain.transaction.external.TransactionVerifier;
import ee.digit25.detector.domain.transaction.external.api.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PredictiveProcessor {

    private final TransactionRequester requester;
    private final TransactionValidator validator;
    private final TransactionVerifier verifier;
    private final Executor taskExecutor;
    private final Semaphore apiRequestSemaphore;
    
    // Performance tuning constants
    private static final int INITIAL_BATCH_SIZE = 12;
    private static final int MAX_BATCH_SIZE = 200;
    private static final int MIN_BATCH_SIZE = 5;
    private static final int ADJUSTMENT_INTERVAL = 5000; // 5 seconds
    
    // Dynamic batch size adjustment
    private volatile int currentBatchSize = INITIAL_BATCH_SIZE;
    private final AtomicLong totalTransactionsProcessed = new AtomicLong(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);
    private final BlockingQueue<Transaction> transactionQueue = new LinkedBlockingQueue<>(10000);
    
    // Performance tracking
    private final Queue<Long> processingTimes = new ConcurrentLinkedQueue<>();
    private final Map<String, Integer> senderFrequency = new ConcurrentHashMap<>();
    private final Map<String, Integer> recipientFrequency = new ConcurrentHashMap<>();
    private final Set<String> knownLegitimateSenders = ConcurrentHashMap.newKeySet();
    private final Set<String> knownLegitimateRecipients = ConcurrentHashMap.newKeySet();
    
    // Prefetch buffer
    private final BlockingQueue<Transaction> prefetchBuffer = new LinkedBlockingQueue<>(2000);
    private volatile boolean isPrefetching = false;

    @Scheduled(fixedDelay = 100) // Process every 100ms
    public void process() {
        try {
            // Ensure prefetch buffer is filled
            if (!isPrefetching && prefetchBuffer.size() < 1000) {
                startPrefetching();
            }

            // Get transactions from prefetch buffer
            List<Transaction> batch = new ArrayList<>();
            prefetchBuffer.drainTo(batch, currentBatchSize);

            if (batch.isEmpty()) return;

            // Process batch with optimized validation
            List<CompletableFuture<Map.Entry<Boolean, Transaction>>> futures = batch.stream()
                .map(transaction -> CompletableFuture.supplyAsync(() -> {
                    try {
                        apiRequestSemaphore.acquire();
                        try {
                            // Quick check for known legitimate parties
                            if (isLikelyLegitimate(transaction)) {
                                return Map.entry(true, transaction);
                            }
                            
                            boolean isValid = validator.isLegitimate(transaction);
                            updateFrequencyMaps(transaction, isValid);
                            return Map.entry(isValid, transaction);
                        } finally {
                            apiRequestSemaphore.release();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return Map.entry(false, transaction);
                    }
                }, taskExecutor))
                .collect(Collectors.toList());

            // Process results
            List<Map.Entry<Boolean, Transaction>> results = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

            // Group and process transactions
            Map<Boolean, List<Transaction>> groupedTransactions = results.stream()
                .collect(Collectors.groupingBy(
                    Map.Entry::getKey,
                    Collectors.mapping(Map.Entry::getValue, Collectors.toList())
                ));

            // Bulk operations
            List<Transaction> legitimateTransactions = groupedTransactions.getOrDefault(true, new ArrayList<>());
            List<Transaction> rejectedTransactions = groupedTransactions.getOrDefault(false, new ArrayList<>());

            if (!legitimateTransactions.isEmpty()) {
                verifier.verify(legitimateTransactions);
            }

            if (!rejectedTransactions.isEmpty()) {
                verifier.reject(rejectedTransactions);
            }

            // Update metrics
            updateMetrics(batch.size());

        } catch (Exception e) {
            log.error("Error in transaction processing", e);
        }
    }

    private void startPrefetching() {
        isPrefetching = true;
        CompletableFuture.runAsync(() -> {
            try {
                while (prefetchBuffer.size() < 1000 && !Thread.currentThread().isInterrupted()) {
                    List<Transaction> newTransactions = requester.getUnverified(currentBatchSize);
                    prefetchBuffer.addAll(newTransactions);
                }
            } finally {
                isPrefetching = false;
            }
        }, taskExecutor);
    }

    private boolean isLikelyLegitimate(Transaction transaction) {
        return knownLegitimateSenders.contains(transaction.getSender()) &&
               knownLegitimateRecipients.contains(transaction.getRecipient());
    }

    private void updateFrequencyMaps(Transaction transaction, boolean isValid) {
        if (isValid) {
            knownLegitimateSenders.add(transaction.getSender());
            knownLegitimateRecipients.add(transaction.getRecipient());
        }
        
        senderFrequency.merge(transaction.getSender(), 1, Integer::sum);
        recipientFrequency.merge(transaction.getRecipient(), 1, Integer::sum);
    }

    @Scheduled(fixedRate = ADJUSTMENT_INTERVAL)
    public void adjustBatchSize() {
        if (processingTimes.size() < 10) return;

        long avgProcessingTime = processingTimes.stream()
            .mapToLong(Long::longValue)
            .sum() / processingTimes.size();

        // Adjust batch size based on processing time
        if (avgProcessingTime < 500 && currentBatchSize < MAX_BATCH_SIZE) {
            currentBatchSize = Math.min(currentBatchSize + 10, MAX_BATCH_SIZE);
        } else if (avgProcessingTime > 1000 && currentBatchSize > MIN_BATCH_SIZE) {
            currentBatchSize = Math.max(currentBatchSize - 10, MIN_BATCH_SIZE);
        }

        processingTimes.clear();
    }

    private void updateMetrics(int batchSize) {
        totalTransactionsProcessed.addAndGet(batchSize);
        processingTimes.add(System.currentTimeMillis());
    }

    @Scheduled(fixedRate = 1000)
    public void logMetrics() {
        long processed = totalTransactionsProcessed.get();
        long time = totalProcessingTime.get();
        
        log.info("""
            Advanced Performance Metrics:
            - Total transactions processed: {}
            - Current batch size: {}
            - Prefetch buffer size: {}
            - Known legitimate senders: {}
            - Known legitimate recipients: {}
            - Transactions per second: {}
            - Average processing time: {} ms
            """,
            processed,
            currentBatchSize,
            prefetchBuffer.size(),
            knownLegitimateSenders.size(),
            knownLegitimateRecipients.size(),
            processed > 0 ? (processed * 1000.0) / time : 0,
            processed > 0 ? time / processed : 0
        );
    }
} 