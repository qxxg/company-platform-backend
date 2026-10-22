package com.company.platform.customer.mapper;

import com.company.platform.customer.entity.BizInvoice;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 手工发票 Mapper。
 *
 * <p>只使用 MyBatis-Flex 的通用 CRUD；发票号码唯一性由数据库唯一索引兜底，
 * 服务层在插入前做友好校验。</p>
 */
@Mapper
public interface BizInvoiceMapper extends BaseMapper<BizInvoice> {
}
