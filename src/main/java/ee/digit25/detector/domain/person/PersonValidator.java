package ee.digit25.detector.domain.person;

import ee.digit25.detector.domain.person.external.PersonRequester;
import ee.digit25.detector.domain.person.external.api.Person;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class PersonValidator {

    private final PersonRequester requester;
    // Cache person information for 5 minutes
    private final Map<String, Person> personCache = new ConcurrentHashMap<>();
    private final Map<String, Long> cacheTimestamps = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION = 5 * 60 * 1000; // 5 minutes in milliseconds

    public boolean isValid(String personCode) {
        log.info("Validating person {}", personCode);
        
        Person person = getPerson(personCode);
        if (person == null) {
            log.warn("Could not fetch person data for {}", personCode);
            return false;
        }

        // Quick checks first
        if (person.getWarrantIssued()) {
            log.info("Person {} has a warrant issued", personCode);
            return false;
        }

        if (person.getBlacklisted()) {
            log.info("Person {} is blacklisted", personCode);
            return false;
        }

        if (!person.getHasContract()) {
            log.info("Person {} has no contract", personCode);
            return false;
        }

        return true;
    }

    private Person getPerson(String personCode) {
        long currentTime = System.currentTimeMillis();
        Long timestamp = cacheTimestamps.get(personCode);
        
        // Check if we have a valid cached person
        if (timestamp != null && (currentTime - timestamp) < CACHE_DURATION) {
            return personCache.get(personCode);
        }

        // If not in cache or expired, fetch from API
        try {
            Person person = requester.get(personCode);
            personCache.put(personCode, person);
            cacheTimestamps.put(personCode, currentTime);
            return person;
        } catch (Exception e) {
            log.error("Error fetching person {}: {}", personCode, e.getMessage());
            return null;
        }
    }
}
