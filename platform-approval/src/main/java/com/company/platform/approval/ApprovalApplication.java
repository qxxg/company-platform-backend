package com.company.platform.approval;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 流程审批中心启动类。
 *
 * <p>本服务承载 V1.4 的流程审批模块：审批定义（含金额档位）、审批实例、审批任务
 * 和审批记录，并对外提供待办、我的申请与审批动作接口。审批通过后由业务动作
 * 执行器回调对应业务服务落地实际变更，例如客户信用额度调整。</p>
 *
 * <p>服务名 {@code platform-approval}，端口 9104。数据保存在 {@code company_platform}，
 * 与客户、合同和财务主数据同库。</p>
 */
@SpringBootApplication(scanBasePackages = "com.company.platform")
@EnableDiscoveryClient
public class ApprovalApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApprovalApplication.class, args);
    }
}
