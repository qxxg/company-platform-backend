package com.company.platform.common.core.id;

/**
 * 线程安全的雪花 ID 生成器。
 *
 * <p>位布局：1 位符号位不用，41 位毫秒时间戳，5 位数据中心，5 位机器号，
 * 12 位序列号。纪元为 2024-01-01T00:00:00Z。订单号、用户 ID 等 Long 主键
 * 都使用该生成器，序列化到前端时必须转成字符串。</p>
 *
 * <p><strong>序列号策略：</strong>序列号跨毫秒时不是重置为 0，而是继续加 1。
 * 订单创建是人工节奏，如果每毫秒都从 0 开始，低 12 位会长期为 0，
 * {@code (order_no >> 1) % 4} 会把所有订单压到同一张分片表。保持滚动可以让
 * 低两位自然变化，8 张物理表都能被使用。</p>
 */
public final class SnowflakeIdGenerator {

    /** 2024-01-01T00:00:00Z，减少时间戳位数。 */
    private static final long EPOCH = 1704067200000L;
    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    private final long workerId;
    private final long datacenterId;
    private long sequence;
    private long lastTimestamp = -1L;

    /**
     * @param workerId     机器号 0–31，多实例部署时必须互不相同
     * @param datacenterId 数据中心号 0–31
     * @throws IllegalArgumentException 超出位宽允许范围
     */
    public SnowflakeIdGenerator(long workerId, long datacenterId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("workerId must be between 0 and " + MAX_WORKER_ID);
        }
        if (datacenterId < 0 || datacenterId > MAX_DATACENTER_ID) {
            throw new IllegalArgumentException("datacenterId must be between 0 and " + MAX_DATACENTER_ID);
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
    }

    /**
     * 生成下一个 ID。方法整体加锁，避免同一毫秒内序列号竞争。
     *
     * <p>时钟回拨处理：回拨不超过 5 秒时等待时钟追平；超过 5 秒说明系统时间
     * 出现严重异常，直接抛错，宁可不生成也不能生成重复订单号。</p>
     */
    public synchronized long nextId() {
        long timestamp = currentTime();

        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            if (offset > 5_000L) {
                throw new IllegalStateException("Clock moved backwards by " + offset + "ms, refusing to generate id");
            }
            timestamp = waitUntil(lastTimestamp);
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                // 当前毫秒的 4096 个序列号用完，等待下一毫秒。
                timestamp = waitUntil(lastTimestamp + 1);
            }
        } else {
            // 跨毫秒继续递增序列号，而不是归零。
            // 订单生成频率低，归零会让低 12 位长期固定为 0，所有订单落到同一张分片表。
            sequence = (sequence + 1) & SEQUENCE_MASK;
        }

        lastTimestamp = timestamp;

        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    /** 自旋等待到目标毫秒，适用于短暂时钟回拨或序列号耗尽。 */
    private static long waitUntil(long target) {
        long timestamp = currentTime();
        while (timestamp < target) {
            Thread.onSpinWait();
            timestamp = currentTime();
        }
        return timestamp;
    }

    /** 当前毫秒时间戳；抽成方法便于测试替换。 */
    private static long currentTime() {
        return System.currentTimeMillis();
    }
}
