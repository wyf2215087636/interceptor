package com.xd.base.sql.interceptor;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;

/**
 * @author WangYifei
 * @date 2021-06-04 13:41
 * @describe 对Page 类的扩展，新增id，让id 作为有序的查询条件
 */
@Data
public class HPage<T> extends Page<T> {

    /**
     * 条件id
     */
    private Long id;

    public HPage(Long id, Integer pageSize) {
        super(( id / pageSize ) + 1, pageSize);
        this.id = id;
    }
}
