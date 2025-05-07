package ee.digit25.detector.process;

import ee.digit25.detector.domain.transaction.TransactionValidator;
import ee.digit25.detector.domain.transaction.external.TransactionRequester;
import ee.digit25.detector.domain.transaction.external.TransactionVerifier;
import ee.digit25.detector.domain.transaction.external.api.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class Processor {

    private static final int TRANSACTION_BATCH_SIZE = 50;
    private final TransactionRequester requester;
    private final TransactionValidator validator;
    private final TransactionVerifier verifier;

    @Scheduled(fixedDelay = 1000)
    public void process() {
        if (log.isInfoEnabled()) {
            log.info("Starting to process a batch of transactions of size {}", TRANSACTION_BATCH_SIZE);
        }

        List<Transaction> transactions = requester.getUnverified(TRANSACTION_BATCH_SIZE);
        
        if (transactions.isEmpty()) {
            return;
        }

        // Parallel processing for better performance with larger batches
        Map<Boolean, List<Transaction>> groupedTransactions = transactions.parallelStream()
            .collect(Collectors.partitioningBy(validator::isLegitimate));

        processTransactionGroups(groupedTransactions);
    }

    private void processTransactionGroups(Map<Boolean, List<Transaction>> groupedTransactions) {
        List<Transaction> legitimateTransactions = groupedTransactions.get(true);
        List<Transaction> rejectedTransactions = groupedTransactions.get(false);

        // Process legitimate transactions asynchronously if supported by verifier
        if (legitimateTransactions != null && !legitimateTransactions.isEmpty()) {
            logBatchSize("Bulk verifying {} legitimate transactions", legitimateTransactions.size());
            verifier.verify(legitimateTransactions);
        }

        // Process rejected transactions
        if (rejectedTransactions != null && !rejectedTransactions.isEmpty()) {
            logBatchSize("Bulk rejecting {} transactions", rejectedTransactions.size());
            verifier.reject(rejectedTransactions);
        }
    }

    private void logBatchSize(String message, int size) {
        if (log.isInfoEnabled()) {
            log.info(message, size);
        }
    }
}