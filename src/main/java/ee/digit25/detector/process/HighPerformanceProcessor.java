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
public class HighPerformanceProcessor {

    private final TransactionRequester requester;
    private final TransactionValidator validator;
    private final TransactionVerifier verifier;
    private final Executor taskExecutor;
    private final Semaphore apiRequestSemaphore;
    private final int optimalBatchSize;
    
    private final AtomicLong totalTransactionsProcessed = new AtomicLong(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);
    private final BlockingQueue<Transaction> transactionQueue = new LinkedBlockingQueue<>(10000);
    private volatile boolean isShuttingDown = false;

    @Scheduled(fixedDelay = 100) // Process every 100ms for maximum throughput
    public void process() {
        if (isShuttingDown) return;

        try {
            // Fill the queue if it's getting low
            if (transactionQueue.size() < optimalBatchSize) {
                List<Transaction> newTransactions = requester.getUnverified(optimalBatchSize);
                transactionQueue.addAll(newTransactions);
            }

            // Process a batch of transactions
            List<Transaction> batch = new ArrayList<>();
            transactionQueue.drainTo(batch, optimalBatchSize);

            if (batch.isEmpty()) return;

            // Process the batch in parallel with rate limiting
            List<CompletableFuture<Map.Entry<Boolean, Transaction>>> futures = batch.stream()
                .map(transaction -> CompletableFuture.supplyAsync(() -> {
                    try {
                        apiRequestSemaphore.acquire();
                        try {
                            boolean isValid = validator.isLegitimate(transaction);
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

            // Wait for all validations to complete
            List<Map.Entry<Boolean, Transaction>> results = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

            // Group transactions by validation result
            Map<Boolean, List<Transaction>> groupedTransactions = results.stream()
                .collect(Collectors.groupingBy(
                    Map.Entry::getKey,
                    Collectors.mapping(Map.Entry::getValue, Collectors.toList())
                ));

            // Bulk verify/reject transactions
            List<Transaction> legitimateTransactions = groupedTransactions.getOrDefault(true, new ArrayList<>());
            List<Transaction> rejectedTransactions = groupedTransactions.getOrDefault(false, new ArrayList<>());

            if (!legitimateTransactions.isEmpty()) {
                verifier.verify(legitimateTransactions);
            }

            if (!rejectedTransactions.isEmpty()) {
                verifier.reject(rejectedTransactions);
            }

            // Update metrics
            totalTransactionsProcessed.addAndGet(batch.size());
            
        } catch (Exception e) {
            log.error("Error in transaction processing", e);
        }
    }

    @Scheduled(fixedRate = 1000) // Log metrics every second
    public void logMetrics() {
        long processed = totalTransactionsProcessed.get();
        long time = totalProcessingTime.get();
        
        log.info("""
            Performance Metrics:
            - Total transactions processed: {}
            - Average processing time: {} ms
            - Current queue size: {}
            - Transactions per second: {}
            """,
            processed,
            processed > 0 ? time / processed : 0,
            transactionQueue.size(),
            processed > 0 ? (processed * 1000.0) / time : 0
        );
    }
} 