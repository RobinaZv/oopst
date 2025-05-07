package ee.digit25.detector.domain.transaction.external;

import ee.bitweb.core.retrofit.RetrofitRequestExecutor;
import ee.digit25.detector.domain.transaction.external.api.Transaction;
import ee.digit25.detector.domain.transaction.external.api.TransactionApiProperties;
import ee.digit25.detector.domain.transaction.external.api.TransactionsApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionVerifier {

    private final TransactionsApi api;
    private final TransactionApiProperties properties;
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    public void verify(Transaction transaction) {
        verifyWithRetry(() -> {
            log.info("Verifying transaction {}", transaction.getId());
            RetrofitRequestExecutor.executeRaw(api.verify(properties.getToken(), transaction.getId()));
        });
    }

    public void reject(Transaction transaction) {
        verifyWithRetry(() -> {
            log.info("Rejecting transaction {}", transaction.getId());
            RetrofitRequestExecutor.executeRaw(api.reject(properties.getToken(), transaction.getId()));
        });
    }

    public void verify(List<Transaction> transactions) {
        if (transactions.isEmpty()) return;
        
        List<String> ids = transactions.stream()
            .map(Transaction::getId)
            .toList();
            
        verifyWithRetry(() -> {
            log.info("Bulk verifying transactions {}", ids);
            RetrofitRequestExecutor.executeRaw(api.verify(properties.getToken(), ids));
        });
    }

    public void reject(List<Transaction> transactions) {
        if (transactions.isEmpty()) return;
        
        List<String> ids = transactions.stream()
            .map(Transaction::getId)
            .toList();
            
        verifyWithRetry(() -> {
            log.info("Bulk rejecting transactions {}", ids);
            RetrofitRequestExecutor.executeRaw(api.reject(properties.getToken(), ids));
        });
    }

    private void verifyWithRetry(Runnable operation) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                operation.run();
                return;
            } catch (Exception e) {
                log.error("Error in verification (attempt {}/{}): {}", 
                    attempt, MAX_RETRIES, e.getMessage());
                
                if (attempt < MAX_RETRIES) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }
}
