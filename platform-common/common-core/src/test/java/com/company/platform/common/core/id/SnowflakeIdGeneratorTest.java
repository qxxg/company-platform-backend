package com.company.platform.common.core.id;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 雪花 ID 测试。
 *
 * <p>订单号同时承担分片键职责：分库取 {@code order_no % 2}，分表取
 * {@code (order_no >> 1) % 4}。因此除了唯一性，还必须验证低两位在连续生成时
 * 会变化，确保双库八表都能被使用。</p>
 */
class SnowflakeIdGeneratorTest {

    @Test
    void idsAreUniqueAndMonotonic() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, 1);
        long previous = -1L;
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 5_000; i++) {
            long id = generator.nextId();
            assertTrue(id > 0, "id must be positive");
            assertTrue(id > previous, "ids must increase for one generator");
            assertTrue(ids.add(id), "id must be unique");
            previous = id;
        }
        assertEquals(5_000, ids.size());
    }

    @Test
    void idsCoverAllEightPhysicalShards() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(2, 3);
        Set<Integer> shards = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            long id = generator.nextId();
            int database = (int) (id % 2);
            int table = (int) ((id >> 1) % 4);
            shards.add(database * 4 + table);
        }
        // ds_0/t_order_0..3 与 ds_1/t_order_0..3 必须全部出现。
        assertEquals(8, shards.size());
    }

    @Test
    void workerAndDatacenterAreEncodedInExpectedBits() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(7, 3);
        long id = generator.nextId();
        assertEquals(7L, (id >> 12) & 31L);
        assertEquals(3L, (id >> 17) & 31L);
    }

    @Test
    void rejectsOutOfRangeWorkerConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(32, 0));
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(0, 32));
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(-1, 0));
    }
}
