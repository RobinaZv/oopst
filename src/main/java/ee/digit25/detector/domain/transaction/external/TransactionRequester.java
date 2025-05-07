package ee.digit25.detector.domain.transaction.external;

import ee.bitweb.core.retrofit.RetrofitRequestExecutor;
import ee.digit25.detector.domain.transaction.external.api.Transaction;
import ee.digit25.detector.domain.transaction.external.api.TransactionApiProperties;
import ee.digit25.detector.domain.transaction.external.api.TransactionsApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionRequester {

    private final TransactionsApi api;
    private final TransactionApiProperties properties;
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    public List<Transaction> getUnverified(int amount) {
        log.info("Requesting a batch of unverified transactions of size {}", amount);
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                List<Transaction> transactions = RetrofitRequestExecutor.executeRaw(
                    api.getUnverified(properties.getToken(), amount)
                );
                
                if (transactions != null && !transactions.isEmpty()) {
                    return transactions;
                }
                
                log.warn("Received empty transaction list, attempt {}/{}", attempt, MAX_RETRIES);
            } catch (Exception e) {
                log.error("Error fetching transactions (attempt {}/{}): {}", 
                    attempt, MAX_RETRIES, e.getMessage());
            }
            
            if (attempt < MAX_RETRIES) {
                try {
                    TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        log.error("Failed to fetch transactions after {} attempts", MAX_RETRIES);
        return new ArrayList<>();
    }

}
