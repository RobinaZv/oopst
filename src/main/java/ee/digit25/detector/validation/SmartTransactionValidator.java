package ee.digit25.detector.validation;

import ee.digit25.detector.domain.transaction.external.api.Transaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class SmartTransactionValidator {
    
    // Pattern recognition maps
    private final Map<String, AtomicInteger> senderFrequency = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> recipientFrequency = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> averageAmounts = new ConcurrentHashMap<>();
    private final Map<String, Integer> transactionCounts = new ConcurrentHashMap<>();
    
    // Trust thresholds
    private static final int TRUST_THRESHOLD = 5;
    private static final BigDecimal AMOUNT_VARIANCE_THRESHOLD = new BigDecimal("1000.00");
    
    public boolean isLikelyLegitimate(Transaction transaction) {
        String sender = transaction.getSender();
        String recipient = transaction.getRecipient();
        BigDecimal amount = transaction.getAmount();
        
        // Update frequency maps
        senderFrequency.computeIfAbsent(sender, k -> new AtomicInteger(0)).incrementAndGet();
        recipientFrequency.computeIfAbsent(recipient, k -> new AtomicInteger(0)).incrementAndGet();
        
        // Update amount statistics
        updateAmountStatistics(sender, amount);
        
        // Check for trusted patterns
        if (isTrustedSender(sender) && isTrustedRecipient(recipient)) {
            return true;
        }
        
        // Check for suspicious patterns
        if (isSuspiciousAmount(sender, amount)) {
            return false;
        }
        
        return true;
    }
    
    private void updateAmountStatistics(String sender, BigDecimal amount) {
        transactionCounts.merge(sender, 1, Integer::sum);
        averageAmounts.merge(sender, amount, (oldAvg, newAmount) -> 
            oldAvg.add(newAmount).divide(new BigDecimal("2")));
    }
    
    private boolean isTrustedSender(String sender) {
        return senderFrequency.getOrDefault(sender, new AtomicInteger(0)).get() >= TRUST_THRESHOLD;
    }
    
    private boolean isTrustedRecipient(String recipient) {
        return recipientFrequency.getOrDefault(recipient, new AtomicInteger(0)).get() >= TRUST_THRESHOLD;
    }
    
    private boolean isSuspiciousAmount(String sender, BigDecimal amount) {
        BigDecimal avgAmount = averageAmounts.get(sender);
        if (avgAmount == null) return false;
        
        BigDecimal difference = amount.subtract(avgAmount).abs();
        return difference.compareTo(AMOUNT_VARIANCE_THRESHOLD) > 0;
    }
} 