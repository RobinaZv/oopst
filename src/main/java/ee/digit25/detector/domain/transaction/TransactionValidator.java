package ee.digit25.detector.domain.transaction;

import ee.digit25.detector.domain.account.AccountValidator;
import ee.digit25.detector.domain.device.DeviceValidator;
import ee.digit25.detector.domain.person.PersonValidator;
import ee.digit25.detector.domain.transaction.external.api.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionValidator {

    private final PersonValidator personValidator;
    private final DeviceValidator deviceValidator;
    private final AccountValidator accountValidator;

    public boolean isLegitimate(Transaction transaction) {
        // Quick checks first
        if (!deviceValidator.isValid(transaction.getDeviceMac())) {
            log.info("Invalid device for transaction {}", transaction.getId());
            return false;
        }

        if (!personValidator.isValid(transaction.getSender())) {
            log.info("Invalid sender for transaction {}", transaction.getId());
            return false;
        }

        if (!personValidator.isValid(transaction.getRecipient())) {
            log.info("Invalid recipient for transaction {}", transaction.getId());
            return false;
        }

        // More expensive checks last
        if (!accountValidator.isValidSenderAccount(transaction.getSenderAccount(), 
                transaction.getAmount(), transaction.getSender())) {
            log.info("Invalid sender account for transaction {}", transaction.getId());
            return false;
        }

        if (!accountValidator.isValidRecipientAccount(transaction.getRecipientAccount(), 
                transaction.getRecipient())) {
            log.info("Invalid recipient account for transaction {}", transaction.getId());
            return false;
        }

        return true;
    }
}
