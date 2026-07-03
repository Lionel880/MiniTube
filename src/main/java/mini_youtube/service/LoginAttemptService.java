package mini_youtube.service;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPT = 5;
    private static final long LOCKOUT_DURATION = 15 * 60 * 1000; // 15 minutes

    private final ConcurrentHashMap<String, Integer> attemptsMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lockoutMap = new ConcurrentHashMap<>();

    public boolean isBlocked(String username) {
        if (username == null) return false;
        
        Long lockoutTime = lockoutMap.get(username);
        if (lockoutTime == null) {
            return false;
        }

        if (System.currentTimeMillis() < lockoutTime) {
            return true;
        }

        // 鎖定時間已過，自動解除鎖定
        lockoutMap.remove(username);
        attemptsMap.remove(username);
        return false;
    }

    public void loginFailed(String username) {
        if (username == null) return;

        int attempts = attemptsMap.getOrDefault(username, 0) + 1;
        attemptsMap.put(username, attempts);

        if (attempts >= MAX_ATTEMPT) {
            lockoutMap.put(username, System.currentTimeMillis() + LOCKOUT_DURATION);
        }
    }

    public void loginSucceeded(String username) {
        if (username == null) return;
        attemptsMap.remove(username);
        lockoutMap.remove(username);
    }
}
