-- 开发环境应用账号授权：MySQL 192.168.3.44:3306
-- 生产环境不要使用 ALL PRIVILEGES，应按库表最小权限拆分账号。
CREATE USER IF NOT EXISTS 'company_app'@'%' IDENTIFIED BY 'Company@2026';
ALTER USER 'company_app'@'%' IDENTIFIED BY 'Company@2026';
GRANT ALL PRIVILEGES ON company_platform.* TO 'company_app'@'%';
GRANT ALL PRIVILEGES ON company_order0.* TO 'company_app'@'%';
FLUSH PRIVILEGES;
