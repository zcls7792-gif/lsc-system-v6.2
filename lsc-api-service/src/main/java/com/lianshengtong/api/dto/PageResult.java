package com.lianshengtong.api.dto;

import lombok.Data;
import java.util.List;

@Data
public class PageResult<T> {
    private List<T> records;
    private long total;
    private int current;
    private int size;

    public static <T> PageResult<T> of(List<T> all, int page, int size) {
        int total = all.size();
        int from = Math.min((page - 1) * size, total);
        int to = Math.min(from + size, total);
        PageResult<T> r = new PageResult<>();
        r.records = all.subList(from, to);
        r.total = total;
        r.current = page;
        r.size = size;
        return r;
    }
}
