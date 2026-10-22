#!/usr/bin/env bash
set -euo pipefail

MYSQL_BASE=(mysql -uroot -proot -h127.0.0.1 --protocol=TCP -N)

echo "== company_platform tables =="
"${MYSQL_BASE[@]}" -P3306 -e "show tables from company_platform;"

echo "== company_bi tables =="
"${MYSQL_BASE[@]}" -P3306 -e "show tables from company_bi;"
"${MYSQL_BASE[@]}" -P3306 -e "select 'bi_metric', count(*) from company_bi.bi_metric;"
"${MYSQL_BASE[@]}" -P3306 -e "select 'bi_metric_pending', count(*) from company_bi.bi_metric where status='PENDING_SOURCE';"
"${MYSQL_BASE[@]}" -P3306 -e "select 'bi_etl_run', count(*) from company_bi.bi_etl_run;"

echo "== approval center tables =="
"${MYSQL_BASE[@]}" -P3306 -e "select 'approval_definition', count(*) from company_platform.approval_definition;"
"${MYSQL_BASE[@]}" -P3306 -e "select 'approval_instance', count(*) from company_platform.approval_instance;"
"${MYSQL_BASE[@]}" -P3306 -e "select 'approval_task', count(*) from company_platform.approval_task;"
"${MYSQL_BASE[@]}" -P3306 -e "select 'approval_record', count(*) from company_platform.approval_record;"

echo "== customer/contract seed counts =="
"${MYSQL_BASE[@]}" -P3306 -e "select 'biz_customer', count(*) from company_platform.biz_customer;"
"${MYSQL_BASE[@]}" -P3306 -e "select 'biz_contract', count(*) from company_platform.biz_contract;"

echo "== finance receivable/receipt tables =="
"${MYSQL_BASE[@]}" -P3306 -e "select 'biz_receivable', count(*) from company_platform.biz_receivable;"
"${MYSQL_BASE[@]}" -P3306 -e "select 'biz_receipt', count(*) from company_platform.biz_receipt;"
"${MYSQL_BASE[@]}" -P3306 -e "select 'biz_receipt_allocation', count(*) from company_platform.biz_receipt_allocation;"

echo "== finance invoice/statement tables =="
"${MYSQL_BASE[@]}" -P3306 -e "select 'biz_invoice', count(*) from company_platform.biz_invoice;"
"${MYSQL_BASE[@]}" -P3306 -e "select 'biz_statement', count(*) from company_platform.biz_statement;"
"${MYSQL_BASE[@]}" -P3306 -e "select 'biz_statement_line', count(*) from company_platform.biz_statement_line;"

echo "== receivable finance columns =="
"${MYSQL_BASE[@]}" -P3306 -e "select column_name, column_type from information_schema.columns where table_schema='company_platform' and table_name='biz_receivable' and column_name in ('source_type','currency','remark','created_by','updated_at','version') order by column_name;"

echo "== order contract column =="
"${MYSQL_BASE[@]}" -P3306 -e "select table_name, column_name, column_type from information_schema.columns where table_schema='company_order0' and column_name='contract_id' order by table_name;"
"${MYSQL_BASE[@]}" -P3307 -e "select table_name, column_name, column_type from information_schema.columns where table_schema='company_order1' and column_name='contract_id' order by table_name;"

echo "== equipment / scheduling tables =="
"${MYSQL_BASE[@]}" -P3306 -e "select 't_device', count(*) from company_order0.t_device;"
"${MYSQL_BASE[@]}" -P3306 -e "select 't_device_status_log', count(*) from company_order0.t_device_status_log;"
"${MYSQL_BASE[@]}" -P3306 -e "select 't_work_order', count(*) from company_order0.t_work_order;"
"${MYSQL_BASE[@]}" -P3306 -e "select 't_device_queue', count(*) from company_order0.t_device_queue;"
"${MYSQL_BASE[@]}" -P3306 -e "select 't_queue_jump_request', count(*) from company_order0.t_queue_jump_request;"
"${MYSQL_BASE[@]}" -P3306 -e "select 't_station_message', count(*) from company_order0.t_station_message;"

echo "== equipment maintenance tables =="
"${MYSQL_BASE[@]}" -P3306 -e "select 't_maintenance_order', count(*) from company_order0.t_maintenance_order;"
"${MYSQL_BASE[@]}" -P3306 -e "select 't_inspection_record', count(*) from company_order0.t_inspection_record;"

echo "== warehouse tables =="
"${MYSQL_BASE[@]}" -P3306 -e "select 't_warehouse', count(*) from company_order0.t_warehouse;"
"${MYSQL_BASE[@]}" -P3306 -e "select 't_material', count(*) from company_order0.t_material;"
"${MYSQL_BASE[@]}" -P3306 -e "select 't_inventory', count(*) from company_order0.t_inventory;"
"${MYSQL_BASE[@]}" -P3306 -e "select 't_purchase_receipt', count(*) from company_order0.t_purchase_receipt;"
"${MYSQL_BASE[@]}" -P3306 -e "select 't_stock_transfer', count(*) from company_order0.t_stock_transfer;"

echo "== material issue tables =="
"${MYSQL_BASE[@]}" -P3306 -e "select 't_material_issue', count(*) from company_order0.t_material_issue;"
"${MYSQL_BASE[@]}" -P3306 -e "select 't_material_issue_line', count(*) from company_order0.t_material_issue_line;"

echo "== expense / loan tables =="
"${MYSQL_BASE[@]}" -P3306 -e "select 'biz_expense_claim', count(*) from company_platform.biz_expense_claim;"
"${MYSQL_BASE[@]}" -P3306 -e "select 'biz_loan', count(*) from company_platform.biz_loan;"
"${MYSQL_BASE[@]}" -P3306 -e "select 'biz_loan_repayment', count(*) from company_platform.biz_loan_repayment;"
"${MYSQL_BASE[@]}" -P3306 -e "select 'EXPENSE 定义', count(*) from company_platform.approval_definition where business_type='EXPENSE';"
"${MYSQL_BASE[@]}" -P3306 -e "select 'LOAN 定义', count(*) from company_platform.approval_definition where business_type='LOAN';"

echo "== AI assistant table / permission =="
"${MYSQL_BASE[@]}" -P3306 -e "select 'ai_chat_log', count(*) from company_platform.ai_chat_log;"
"${MYSQL_BASE[@]}" -P3306 -e "select 'ai:assistant:use 权限', count(*) from company_platform.sys_permission where code='ai:assistant:use';"

echo "== device reporting tables =="
"${MYSQL_BASE[@]}" -P3306 -e "select 't_device_credential', count(*) from company_order0.t_device_credential;"
"${MYSQL_BASE[@]}" -P3306 -e "select 't_device_order_report', count(*) from company_order0.t_device_order_report;"
"${MYSQL_BASE[@]}" -P3306 -e "select 't_device_image', count(*) from company_order0.t_device_image;"
"${MYSQL_BASE[@]}" -P3306 -e "select 't_device_report_staging', count(*) from company_order0.t_device_report_staging;"
"${MYSQL_BASE[@]}" -P3306 -e "select 't_device_access_log', count(*) from company_order0.t_device_access_log;"

echo "== company_order0 tables =="
"${MYSQL_BASE[@]}" -P3306 -e "show tables from company_order0;"

echo "== company_order1 tables =="
"${MYSQL_BASE[@]}" -P3307 -e "show tables from company_order1;"

echo "== table counts =="
"${MYSQL_BASE[@]}" -P3306 -e "select 'company_platform', count(*) from information_schema.tables where table_schema='company_platform';"
"${MYSQL_BASE[@]}" -P3306 -e "select 'company_order0', count(*) from information_schema.tables where table_schema='company_order0';"
"${MYSQL_BASE[@]}" -P3307 -e "select 'company_order1', count(*) from information_schema.tables where table_schema='company_order1';"

echo "== app user grants (3306) =="
"${MYSQL_BASE[@]}" -P3306 -e "show grants for 'company_app'@'%';"

echo "== app user grants (3307) =="
"${MYSQL_BASE[@]}" -P3307 -e "show grants for 'company_app'@'%';"
