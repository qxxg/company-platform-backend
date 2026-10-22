package com.company.platform.order.web;

import com.company.platform.common.core.api.ApiResponse;
import com.company.platform.common.security.SecurityUtils;
import com.company.platform.order.service.StationMessageService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 站内消息接口。
 *
 * <p>消息只属于当前登录用户，不提供按用户 ID 查询的入口，避免越权读取。首期
 * 消息来源是设备队列插队和停机提醒。机器令牌没有用户身份，未读数固定为 0。</p>
 */
@RestController
@RequestMapping("/api/order/messages")
@Validated
public class StationMessageController {

    private final StationMessageService messageService;

    public StationMessageController(StationMessageService messageService) {
        this.messageService = messageService;
    }

    /**
     * 当前用户消息分页。
     *
     * @param page       页码
     * @param size       每页条数，最大 100
     * @param unreadOnly 是否只看未读
     */
    @GetMapping("/page")
    public ApiResponse<MessagePageView> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page must be at least 1") long page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must not exceed 100") long size,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return ApiResponse.ok(messageService.page(SecurityUtils.userId(), page, size, unreadOnly));
    }

    /** 当前用户未读消息数。 */
    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountView> unreadCount() {
        return ApiResponse.ok(new UnreadCountView(messageService.unreadCount(SecurityUtils.userId())));
    }

    /** 标记当前用户的一条消息已读。 */
    @PostMapping("/{id}/read")
    public ApiResponse<StationMessageView> markRead(@PathVariable Long id) {
        return ApiResponse.ok(messageService.markRead(id, SecurityUtils.userId()));
    }

    /** 全部标记已读，返回更新条数。 */
    @PostMapping("/read-all")
    public ApiResponse<Map<String, Integer>> markAllRead() {
        int updated = messageService.markAllRead(SecurityUtils.userId());
        return ApiResponse.ok(Map.of("updated", updated));
    }
}
