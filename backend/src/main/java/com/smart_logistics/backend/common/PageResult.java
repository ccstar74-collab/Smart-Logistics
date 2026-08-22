package com.smart_logistics.backend.common;

import java.util.List;

public class PageResult<T> {

    private final List<T> records;
    private final long total;
    private final long page;
    private final long pageSize;

    public PageResult(List<T> records, long total, long page, long pageSize) {
        this.records = records;
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
    }

    public List<T> getRecords() {
        return records;
    }

    public long getTotal() {
        return total;
    }

    public long getPage() {
        return page;
    }

    public long getPageSize() {
        return pageSize;
    }
}
