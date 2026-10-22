package com.company.platform.approval.service;

import com.company.platform.common.core.api.BizException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 审批业务参数快照读取器。
 *
 * <p>独立成 Bean 是为了避免审批服务与业务动作执行器之间的循环依赖：执行器需要
 * 读取参数快照，但不应依赖审批服务本身。</p>
 */
@Component
public class ApprovalPayloadReader {

    private final ObjectMapper objectMapper;

    public ApprovalPayloadReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 解析参数快照。
     *
     * @param payload 快照 JSON 文本，可为空
     * @return 参数键值；为空或解析失败时返回空 Map
     */
    public Map<String, Object> read(String payload) {
        if (payload == null || payload.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payload, new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw new BizException(500, "审批业务参数解析失败");
        }
    }
}
