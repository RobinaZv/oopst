package ee.digit25.detector.process;

import ee.digit25.detector.domain.transaction.TransactionValidator;
import ee.digit25.detector.domain.transaction.external.TransactionRequester;
import ee.digit25.detector.domain.transaction.external.TransactionVerifier;
import ee.digit25.detector.domain.transaction.external.api.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class Processor {

    private final int TRANSACTION_BATCH_SIZE = 50;
    private final TransactionRequester requester;
    private final TransactionValidator validator;
    private final TransactionVerifier verifier;
    
    private long totalTransactionsProcessed = 0;
    private long totalProcessingTime = 0;

    @Scheduled(fixedDelay = 1000)
    public void process() {
        Instant start = Instant.now();
        log.info("Starting to process a batch of transactions of size {}", TRANSACTION_BATCH_SIZE);

        List<Transaction> transactions = requester.getUnverified(TRANSACTION_BATCH_SIZE);
        
        // Process transactions in parallel
        List<CompletableFuture<Map.Entry<Boolean, Transaction>>> validationFutures = 
            transactions.stream()
                .map(transaction -> CompletableFuture.supplyAsync(() -> 
                    Map.entry(validator.isLegitimate(transaction), transaction)))
                .collect(Collectors.toList());

        // Wait for all validations to complete
        List<Map.Entry<Boolean, Transaction>> results = validationFutures.stream()
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
            log.info("Bulk verifying {} legitimate transactions", legitimateTransactions.size());
            verifier.verify(legitimateTransactions);
        }

        if (!rejectedTransactions.isEmpty()) {
            log.info("Bulk rejecting {} transactions", rejectedTransactions.size());
            verifier.reject(rejectedTransactions);
        }

        // Update performance metrics
        totalTransactionsProcessed += transactions.size();
        Duration processingTime = Duration.between(start, Instant.now());
        totalProcessingTime += processingTime.toMillis();
        
        // Log performance metrics
        log.info("Batch processed in {} ms. Average processing time: {} ms per transaction",
            processingTime.toMillis(),
            totalTransactionsProcessed > 0 ? totalProcessingTime / totalTransactionsProcessed : 0);
    }
}
