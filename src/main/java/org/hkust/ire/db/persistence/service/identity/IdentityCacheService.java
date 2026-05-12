package org.hkust.ire.db.persistence.service.identity;

import org.hkust.ire.db.persistence.domain.IdentityDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Manages Redis caching of identity records for fast lookup.
 *
 * <p>Cache TTL is 24 hours. Redis failures are handled gracefully
 * and never propagate to the caller.</p>
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
@Service
public class IdentityCacheService {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private static final String CACHE_PREFIX = "ire:identity:";
    private static final long CACHE_TTL_HOURS = 24;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * Caches an identity in Redis.
     *
     * @param goldenId the golden identity ID
     * @param identity the IdentityDAO to cache
     */
    public void cacheIdentity(String goldenId, IdentityDAO identity) {
        if (redisTemplate == null) {
            return;
        }
        try {
            String key = CACHE_PREFIX + goldenId;
            redisTemplate.opsForValue().set(key, identity, CACHE_TTL_HOURS, TimeUnit.HOURS);
            log.debug("Cached identity: goldenId={}", goldenId);
        } catch (Exception e) {
            log.error("Error caching identity goldenId={}: {}", goldenId, e.getMessage());
        }
    }

    /**
     * Retrieves a cached identity.
     *
     * @param goldenId the golden identity ID
     * @return cached IdentityDAO or null
     */
    public IdentityDAO getCachedIdentity(String goldenId) {
        if (redisTemplate == null) {
            return null;
        }
        try {
            String key = CACHE_PREFIX + goldenId;
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached instanceof IdentityDAO) {
                log.debug("Cache hit for goldenId={}", goldenId);
                return (IdentityDAO) cached;
            }
        } catch (Exception e) {
            log.error("Error getting cached identity goldenId={}: {}", goldenId, e.getMessage());
        }
        return null;
    }

    /**
     * Evicts a cached identity.
     *
     * @param goldenId the golden identity ID to evict
     */
    public void evictIdentity(String goldenId) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.delete(CACHE_PREFIX + goldenId);
            log.debug("Evicted cache for goldenId={}", goldenId);
        } catch (Exception e) {
            log.error("Error evicting cache for goldenId={}: {}", goldenId, e.getMessage());
        }
    }
}
