package ee.digit25.detector.domain.account;

import ee.digit25.detector.domain.account.external.AccountRequester;
import ee.digit25.detector.domain.account.external.api.Account;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountValidator {

    private final AccountRequester requester;
    // Cache account information for 5 minutes
    private final Map<String, Account> accountCache = new ConcurrentHashMap<>();
    private final Map<String, Long> cacheTimestamps = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION = 5 * 60 * 1000; // 5 minutes in milliseconds

    public boolean isValidSenderAccount(String accountNumber, BigDecimal amount, String senderPersonCode) {
        log.info("Checking if account {} is valid sender account", accountNumber);
        
        Account account = getAccount(accountNumber);
        if (account == null) {
            return false;
        }

        // Quick checks first
        if (account.getClosed()) {
            log.info("Account {} is closed", accountNumber);
            return false;
        }

        if (!account.getOwner().equals(senderPersonCode)) {
            log.info("Account {} is not owned by {}", accountNumber, senderPersonCode);
            return false;
        }

        // Most expensive check last
        if (account.getBalance().compareTo(amount) < 0) {
            log.info("Account {} has insufficient balance", accountNumber);
            return false;
        }

        return true;
    }

    public boolean isValidRecipientAccount(String accountNumber, String recipientPersonCode) {
        log.info("Checking if account {} is valid recipient account", accountNumber);
        
        Account account = getAccount(accountNumber);
        if (account == null) {
            return false;
        }

        if (account.getClosed()) {
            log.info("Account {} is closed", accountNumber);
            return false;
        }

        if (!account.getOwner().equals(recipientPersonCode)) {
            log.info("Account {} is not owned by {}", accountNumber, recipientPersonCode);
            return false;
        }

        return true;
    }

    private Account getAccount(String accountNumber) {
        long currentTime = System.currentTimeMillis();
        Long timestamp = cacheTimestamps.get(accountNumber);
        
        // Check if we have a valid cached account
        if (timestamp != null && (currentTime - timestamp) < CACHE_DURATION) {
            return accountCache.get(accountNumber);
        }

        // If not in cache or expired, fetch from API
        try {
            Account account = requester.get(accountNumber);
            accountCache.put(accountNumber, account);
            cacheTimestamps.put(accountNumber, currentTime);
            return account;
        } catch (Exception e) {
            log.error("Error fetching account {}: {}", accountNumber, e.getMessage());
            return null;
        }
    }
}
