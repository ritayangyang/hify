package com.hify.modules.agent.web.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * Agent 分页查询请求。
 */
@Data
public class AgentPageRequest {

    @Min(value = 1, message = "page 必须从 1 开始")
    private long page = 1;

    @Min(value = 1, message = "size 最小为 1")
    @Max(value = 50, message = "size 最大为 50")
    private long size = 20;

    /** 按名称模糊查询 */
    private String name;

    private Integer status;

    /** 兼容 pageSize 参数名，与 size 等价 */
    public void setPageSize(long pageSize) {
        this.size = pageSize;
    }
}
