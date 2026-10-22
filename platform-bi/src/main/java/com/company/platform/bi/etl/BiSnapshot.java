package com.company.platform.bi.etl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 一次 ETL 抽取的不可变内存快照。
 *
 * <p>抽取阶段只读业务库，写库阶段再把整个快照替换到 {@code company_bi}。这样
 * 业务库查询变慢或临时不可用时，不会把 BI 事实表清空，也不会让大屏看到半批数据。</p>
 */
public record BiSnapshot(
        List<FactoryRow> factories,
        List<CustomerRow> customers,
        List<OrderRow> orders,
        List<ReceivableRow> receivables,
        List<ReceiptRow> receipts,
        List<ExpenseRow> expenses,
        List<LoanRow> loans,
        List<WorkOrderRow> workOrders,
        List<EquipmentRow> equipment,
        List<QueueRow> queue,
        List<InventoryRow> inventory,
        List<TransferRow> transfers,
        List<ApprovalRow> approvals,
        List<CreditRow> credits
) {

    public BiSnapshot {
        factories = List.copyOf(factories);
        customers = List.copyOf(customers);
        orders = List.copyOf(orders);
        receivables = List.copyOf(receivables);
        receipts = List.copyOf(receipts);
        expenses = List.copyOf(expenses);
        loans = List.copyOf(loans);
        workOrders = List.copyOf(workOrders);
        equipment = List.copyOf(equipment);
        queue = List.copyOf(queue);
        inventory = List.copyOf(inventory);
        transfers = List.copyOf(transfers);
        approvals = List.copyOf(approvals);
        credits = List.copyOf(credits);
    }

    /** 本次快照写入的事实行数，用于 ETL 运行记录。 */
    public long rowCount() {
        return factories.size() + customers.size() + orders.size() + receivables.size()
                + receipts.size() + expenses.size() + loans.size() + workOrders.size()
                + equipment.size() + queue.size() + inventory.size() + transfers.size()
                + approvals.size() + credits.size();
    }

    public record FactoryRow(Long factoryId,
                             String factoryCode,
                             String factoryName,
                             Integer status,
                             LocalDateTime updatedAt) {
    }

    public record CustomerRow(Long customerId,
                              String customerName,
                              Long ownerUserId,
                              String ownerName,
                              Long defaultFactoryId,
                              String status,
                              Long creditLimit,
                              LocalDateTime createdAt,
                              LocalDateTime updatedAt) {
    }

    public record OrderRow(Long orderNo,
                           Long customerId,
                           Long factoryId,
                           Long ownerUserId,
                           String orderStatus,
                           Long totalAmount,
                           LocalDateTime createdAt,
                           LocalDateTime completedAt,
                           LocalDateTime updatedAt) {
    }

    public record ReceivableRow(Long receivableId,
                                Long customerId,
                                Long orderNo,
                                Long factoryId,
                                Long ownerUserId,
                                Long amount,
                                Long receivedAmount,
                                Long outstandingAmount,
                                LocalDate dueDate,
                                String status,
                                boolean overdue,
                                int overdueDays,
                                LocalDateTime createdAt) {
    }

    public record ReceiptRow(Long receiptId,
                             Long customerId,
                             Long factoryId,
                             Long ownerUserId,
                             Long amount,
                             Long allocatedAmount,
                             LocalDate receivedDate,
                             String status,
                             LocalDateTime createdAt) {
    }

    public record ExpenseRow(Long claimId,
                             Long claimantUserId,
                             Long factoryId,
                             Long amount,
                             LocalDate expenseDate,
                             String status,
                             Long paidAmount,
                             LocalDateTime createdAt) {
    }

    public record LoanRow(Long loanId,
                          Long borrowerUserId,
                          Long factoryId,
                          Long amount,
                          LocalDate loanDate,
                          String status,
                          Long outstandingAmount,
                          LocalDateTime createdAt) {
    }

    public record WorkOrderRow(Long workOrderId,
                               Long orderNo,
                               Long customerId,
                               Long factoryId,
                               Long ownerUserId,
                               Long deviceId,
                               String status,
                               BigDecimal plannedMeters,
                               LocalDateTime actualStart,
                               LocalDateTime actualEnd,
                               LocalDateTime createdAt) {
    }

    public record EquipmentRow(Long deviceId,
                               Long factoryId,
                               String deviceType,
                               String status,
                               BigDecimal capacityMetersPerHour,
                               LocalDateTime updatedAt) {
    }

    public record QueueRow(Long queueId,
                           Long deviceId,
                           Long workOrderId,
                           Long factoryId,
                           String status,
                           LocalDateTime enqueuedAt,
                           LocalDateTime plannedStart,
                           LocalDateTime actualStart) {
    }

    public record InventoryRow(Long inventoryId,
                               Long warehouseId,
                               Long factoryId,
                               Long materialId,
                               String materialName,
                               BigDecimal quantity,
                               BigDecimal unitCost,
                               Long amount,
                               BigDecimal safetyStock,
                               LocalDateTime updatedAt) {
    }

    public record TransferRow(Long transferId,
                              Long fromFactoryId,
                              Long toFactoryId,
                              String status,
                              LocalDateTime shippedAt,
                              LocalDateTime receivedAt,
                              LocalDateTime createdAt) {
    }

    public record ApprovalRow(Long instanceId,
                              String businessType,
                              String businessId,
                              Long factoryId,
                              Long applicantUserId,
                              Long amount,
                              String status,
                              LocalDateTime createdAt,
                              LocalDateTime finishedAt,
                              Long durationSeconds) {
    }

    public record CreditRow(Long customerId,
                            String customerName,
                            Long ownerUserId,
                            Long creditLimit,
                            Long receivableOpen,
                            Long orderOpen,
                            Long usedAmount,
                            boolean overLimit,
                            LocalDateTime updatedAt) {
    }
}
