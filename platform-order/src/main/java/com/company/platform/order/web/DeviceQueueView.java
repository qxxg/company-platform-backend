package com.company.platform.order.web;

import java.util.List;

/**
 * 单台设备的队列视图。
 *
 * @param device  设备台账摘要
 * @param entries 活跃队列，按位置升序
 * @param current 当前生产中记录；没有则为空
 */
public record DeviceQueueView(DeviceView device, List<QueueEntryView> entries, QueueEntryView current) {
}
