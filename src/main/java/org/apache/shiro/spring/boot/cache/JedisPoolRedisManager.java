package org.apache.shiro.spring.boot.cache;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.crazycake.shiro.IRedisManager;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.resps.ScanResult;

/** Adapts the application's Jedis pool to Shiro's existing binary cache contract. */
final class JedisPoolRedisManager implements IRedisManager {

    private static final int SCAN_BATCH_SIZE = 100;

    private final JedisPool pool;

    JedisPoolRedisManager(JedisPool pool) {
        this.pool = pool;
    }

    @Override
    public byte[] get(byte[] key) {
        if (Objects.isNull(key)) {
            return null;
        }
        try (Jedis jedis = pool.getResource()) {
            return jedis.get(key);
        }
    }

    @Override
    public byte[] set(byte[] key, byte[] value, int expire) {
        if (Objects.isNull(key)) {
            return null;
        }
        try (Jedis jedis = pool.getResource()) {
            if (expire > 0) {
                jedis.set(key, value, SetParams.setParams().ex(expire));
            } else {
                jedis.set(key, value);
            }
            return value;
        }
    }

    @Override
    public void del(byte[] key) {
        if (Objects.isNull(key)) {
            return;
        }
        try (Jedis jedis = pool.getResource()) {
            jedis.del(key);
        }
    }

    @Override
    public Long dbSize(byte[] pattern) {
        return scan(pattern, null);
    }

    @Override
    public Set<byte[]> keys(byte[] pattern) {
        Set<byte[]> keys = new HashSet<>();
        scan(pattern, keys);
        return keys;
    }

    private long scan(byte[] pattern, Set<byte[]> keys) {
        ScanParams params = new ScanParams().match(pattern).count(SCAN_BATCH_SIZE);
        long count = 0;
        try (Jedis jedis = pool.getResource()) {
            byte[] cursor = ScanParams.SCAN_POINTER_START_BINARY;
            ScanResult<byte[]> page;
            do {
                page = jedis.scan(cursor, params);
                count += page.getResult().size();
                if (Objects.nonNull(keys)) {
                    keys.addAll(page.getResult());
                }
                cursor = page.getCursorAsBytes();
            } while (!page.isCompleteIteration());
        }
        return count;
    }
}
