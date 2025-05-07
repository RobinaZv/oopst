package ee.digit25.detector.domain.device;

import ee.digit25.detector.domain.device.external.DeviceRequester;
import ee.digit25.detector.domain.device.external.api.Device;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceValidator {

    private final DeviceRequester requester;
    // Cache device information for 5 minutes
    private final Map<String, Device> deviceCache = new ConcurrentHashMap<>();
    private final Map<String, Long> cacheTimestamps = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION = 5 * 60 * 1000; // 5 minutes in milliseconds

    public boolean isValid(String mac) {
        log.info("Validating device {}", mac);
        return !isBlacklisted(mac);
    }

    public boolean isBlacklisted(String mac) {
        Device device = getDevice(mac);
        if (device == null) {
            log.warn("Could not fetch device data for {}", mac);
            return true; // Assume blacklisted if we can't verify
        }
        return device.getIsBlacklisted();
    }

    private Device getDevice(String mac) {
        long currentTime = System.currentTimeMillis();
        Long timestamp = cacheTimestamps.get(mac);
        
        // Check if we have a valid cached device
        if (timestamp != null && (currentTime - timestamp) < CACHE_DURATION) {
            return deviceCache.get(mac);
        }

        // If not in cache or expired, fetch from API
        try {
            Device device = requester.get(mac);
            deviceCache.put(mac, device);
            cacheTimestamps.put(mac, currentTime);
            return device;
        } catch (Exception e) {
            log.error("Error fetching device {}: {}", mac, e.getMessage());
            return null;
        }
    }
}
