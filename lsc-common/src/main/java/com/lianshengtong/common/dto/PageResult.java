package com.lianshengtong.common.dto;

import java.io.Serializable;
import java.util.List;

public class PageResult<T> implements Serializable {

    private List<T> records;
    private long total;
    private int pageNo;
    private int pageSize;

    public PageResult() {}

    public PageResult(List<T> records, long total, int pageNo, int pageSize) {
        this.records = records;
        this.total = total;
        this.pageNo = pageNo;
        this.pageSize = pageSize;
    }

    public static <T> Builder<T> builder() { return new Builder<>(); }

    public static class Builder<T> {
        private PageResult<T> obj = new PageResult<>();
        public Builder<T> records(List<T> v) { obj.records = v; return this; }
        public Builder<T> total(long v) { obj.total = v; return this; }
        public Builder<T> pageNo(int v) { obj.pageNo = v; return this; }
        public Builder<T> pageSize(int v) { obj.pageSize = v; return this; }
        public PageResult<T> build() { return obj; }
    }



    public static <T> PageResult<T> of(List<T> records, long total, int pageNo, int pageSize) {
        PageResult<T> pr = new PageResult<>();
        pr.setRecords(records);
        pr.setTotal(total);
        pr.setPageNo(pageNo);
        pr.setPageSize(pageSize);
        return pr;
    }


    public List<T> getRecords() { return records; }
    public void setRecords(List<T> v) { this.records = v; }
    public long getTotal() { return total; }
    public void setTotal(long v) { this.total = v; }
    public int getPageNo() { return pageNo; }
    public void setPageNo(int v) { this.pageNo = v; }
    public int getPageSize() { return pageSize; }
    public void setPageSize(int v) { this.pageSize = v; }
}
