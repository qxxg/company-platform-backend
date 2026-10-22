package com.company.platform.bi.etl;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 把一次 ETL 快照整体替换到 {@code company_bi}。
 *
 * <p>使用本地事务执行“先 DELETE 再批量 INSERT”。InnoDB 的 MVCC 保证未提交前
 * 查询方仍看到上一版数据，提交后一次性切换到新快照，避免大屏看到空表或半批数据。
 * 这里只允许平台 ETL 调用，不对业务服务开放。</p>
 */
@Component
public class BiSnapshotWriter {

    private final JdbcTemplate jdbcTemplate;

    public BiSnapshotWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 整体替换维度、事实和信用占用表。 */
    @Transactional
    public void replace(BiSnapshot snapshot) {
        clearFacts();
        writeFactories(snapshot.factories());
        writeCustomers(snapshot.customers());
        writeOrders(snapshot.orders());
        writeReceivables(snapshot.receivables());
        writeReceipts(snapshot.receipts());
        writeExpenses(snapshot.expenses());
        writeLoans(snapshot.loans());
        writeWorkOrders(snapshot.workOrders());
        writeEquipment(snapshot.equipment());
        writeQueue(snapshot.queue());
        writeInventory(snapshot.inventory());
        writeTransfers(snapshot.transfers());
        writeApprovals(snapshot.approvals());
        writeCredits(snapshot.credits());
    }

    private void clearFacts() {
        jdbcTemplate.update("DELETE FROM bi_customer_credit");
        jdbcTemplate.update("DELETE FROM bi_fact_approval");
        jdbcTemplate.update("DELETE FROM bi_fact_transfer");
        jdbcTemplate.update("DELETE FROM bi_fact_inventory");
        jdbcTemplate.update("DELETE FROM bi_fact_queue");
        jdbcTemplate.update("DELETE FROM bi_fact_equipment");
        jdbcTemplate.update("DELETE FROM bi_fact_work_order");
        jdbcTemplate.update("DELETE FROM bi_fact_loan");
        jdbcTemplate.update("DELETE FROM bi_fact_expense");
        jdbcTemplate.update("DELETE FROM bi_fact_receipt");
        jdbcTemplate.update("DELETE FROM bi_fact_receivable");
        jdbcTemplate.update("DELETE FROM bi_fact_order");
        jdbcTemplate.update("DELETE FROM bi_dim_customer");
        jdbcTemplate.update("DELETE FROM bi_dim_factory");
    }

    private void writeFactories(List<BiSnapshot.FactoryRow> rows) {
        String sql = "INSERT INTO bi_dim_factory(factory_id, factory_code, factory_name, status, updated_at) "
                + "VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, rows.stream().map(row -> new Object[]{
                row.factoryId(), row.factoryCode(), row.factoryName(), row.status(), row.updatedAt()
        }).toList());
    }

    private void writeCustomers(List<BiSnapshot.CustomerRow> rows) {
        String sql = "INSERT INTO bi_dim_customer(customer_id, customer_name, owner_user_id, owner_name, "
                + "default_factory_id, status, credit_limit, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, rows.stream().map(row -> new Object[]{
                row.customerId(), row.customerName(), row.ownerUserId(), row.ownerName(),
                row.defaultFactoryId(), row.status(), row.creditLimit(), row.createdAt(), row.updatedAt()
        }).toList());
    }

    private void writeOrders(List<BiSnapshot.OrderRow> rows) {
        String sql = "INSERT INTO bi_fact_order(order_no, customer_id, factory_id, owner_user_id, order_status, "
                + "total_amount, created_at, completed_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, rows.stream().map(row -> new Object[]{
                row.orderNo(), row.customerId(), row.factoryId(), row.ownerUserId(), row.orderStatus(),
                row.totalAmount(), row.createdAt(), row.completedAt(), row.updatedAt()
        }).toList());
    }

    private void writeReceivables(List<BiSnapshot.ReceivableRow> rows) {
        String sql = "INSERT INTO bi_fact_receivable(receivable_id, customer_id, order_no, factory_id, "
                + "owner_user_id, amount, received_amount, outstanding_amount, due_date, status, "
                + "overdue_flag, overdue_days, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, rows.stream().map(row -> new Object[]{
                row.receivableId(), row.customerId(), row.orderNo(), row.factoryId(), row.ownerUserId(),
                row.amount(), row.receivedAmount(), row.outstandingAmount(), row.dueDate(), row.status(),
                row.overdue() ? 1 : 0, row.overdueDays(), row.createdAt()
        }).toList());
    }

    private void writeReceipts(List<BiSnapshot.ReceiptRow> rows) {
        String sql = "INSERT INTO bi_fact_receipt(receipt_id, customer_id, factory_id, owner_user_id, amount, "
                + "allocated_amount, received_date, status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, rows.stream().map(row -> new Object[]{
                row.receiptId(), row.customerId(), row.factoryId(), row.ownerUserId(), row.amount(),
                row.allocatedAmount(), row.receivedDate(), row.status(), row.createdAt()
        }).toList());
    }

    private void writeExpenses(List<BiSnapshot.ExpenseRow> rows) {
        String sql = "INSERT INTO bi_fact_expense(claim_id, claimant_user_id, factory_id, amount, "
                + "expense_date, status, paid_amount, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, rows.stream().map(row -> new Object[]{
                row.claimId(), row.claimantUserId(), row.factoryId(), row.amount(), row.expenseDate(),
                row.status(), row.paidAmount(), row.createdAt()
        }).toList());
    }

    private void writeLoans(List<BiSnapshot.LoanRow> rows) {
        String sql = "INSERT INTO bi_fact_loan(loan_id, borrower_user_id, factory_id, amount, loan_date, "
                + "status, outstanding_amount, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, rows.stream().map(row -> new Object[]{
                row.loanId(), row.borrowerUserId(), row.factoryId(), row.amount(), row.loanDate(),
                row.status(), row.outstandingAmount(), row.createdAt()
        }).toList());
    }

    private void writeWorkOrders(List<BiSnapshot.WorkOrderRow> rows) {
        String sql = "INSERT INTO bi_fact_work_order(work_order_id, order_no, customer_id, factory_id, "
                + "owner_user_id, device_id, status, planned_meters, actual_start, actual_end, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, rows.stream().map(row -> new Object[]{
                row.workOrderId(), row.orderNo(), row.customerId(), row.factoryId(), row.ownerUserId(),
                row.deviceId(), row.status(), row.plannedMeters(), row.actualStart(), row.actualEnd(),
                row.createdAt()
        }).toList());
    }

    private void writeEquipment(List<BiSnapshot.EquipmentRow> rows) {
        String sql = "INSERT INTO bi_fact_equipment(device_id, factory_id, device_type, status, "
                + "capacity_meters_per_hour, updated_at) VALUES (?, ?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, rows.stream().map(row -> new Object[]{
                row.deviceId(), row.factoryId(), row.deviceType(), row.status(),
                row.capacityMetersPerHour(), row.updatedAt()
        }).toList());
    }

    private void writeQueue(List<BiSnapshot.QueueRow> rows) {
        String sql = "INSERT INTO bi_fact_queue(queue_id, device_id, work_order_id, factory_id, status, "
                + "enqueued_at, planned_start, actual_start) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, rows.stream().map(row -> new Object[]{
                row.queueId(), row.deviceId(), row.workOrderId(), row.factoryId(), row.status(),
                row.enqueuedAt(), row.plannedStart(), row.actualStart()
        }).toList());
    }

    private void writeInventory(List<BiSnapshot.InventoryRow> rows) {
        String sql = "INSERT INTO bi_fact_inventory(inventory_id, warehouse_id, factory_id, material_id, "
                + "material_name, quantity, unit_cost, amount, safety_stock, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, rows.stream().map(row -> new Object[]{
                row.inventoryId(), row.warehouseId(), row.factoryId(), row.materialId(),
                row.materialName(), row.quantity(), row.unitCost(), row.amount(), row.safetyStock(),
                row.updatedAt()
        }).toList());
    }

    private void writeTransfers(List<BiSnapshot.TransferRow> rows) {
        String sql = "INSERT INTO bi_fact_transfer(transfer_id, from_factory_id, to_factory_id, status, "
                + "shipped_at, received_at, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, rows.stream().map(row -> new Object[]{
                row.transferId(), row.fromFactoryId(), row.toFactoryId(), row.status(),
                row.shippedAt(), row.receivedAt(), row.createdAt()
        }).toList());
    }

    private void writeApprovals(List<BiSnapshot.ApprovalRow> rows) {
        String sql = "INSERT INTO bi_fact_approval(instance_id, business_type, business_id, factory_id, "
                + "applicant_user_id, amount, status, created_at, finished_at, duration_seconds) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, rows.stream().map(row -> new Object[]{
                row.instanceId(), row.businessType(), row.businessId(), row.factoryId(),
                row.applicantUserId(), row.amount(), row.status(), row.createdAt(), row.finishedAt(),
                row.durationSeconds()
        }).toList());
    }

    private void writeCredits(List<BiSnapshot.CreditRow> rows) {
        String sql = "INSERT INTO bi_customer_credit(customer_id, customer_name, owner_user_id, credit_limit, "
                + "receivable_open, order_open, used_amount, over_limit, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, rows.stream().map(row -> new Object[]{
                row.customerId(), row.customerName(), row.ownerUserId(), row.creditLimit(),
                row.receivableOpen(), row.orderOpen(), row.usedAmount(), row.overLimit() ? 1 : 0,
                row.updatedAt()
        }).toList());
    }
}
