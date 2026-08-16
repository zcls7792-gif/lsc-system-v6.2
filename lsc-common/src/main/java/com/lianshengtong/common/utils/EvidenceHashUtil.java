package com.lianshengtong.common.utils;

import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 存证哈希计算工具
 * 严格遵循第14.2节序列化规范：
 * 1. 字段按英文字母升序排列
 * 2. 日期统一 yyyy-MM-dd HH:mm:ss.SSS
 * 3. decimal固定保留两位小数
 * 4. JSON去空格换行
 */
public class EvidenceHashUtil {

    private static final Logger log = LoggerFactory.getLogger(EvidenceHashUtil.class);

    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static String sha256Hex(Object obj) {
        String serialized = serialize(obj);
        return DigestUtil.sha256Hex(serialized);
    }

    public static String sha256Hex(String raw) {
        if (raw == null) {
            raw = "";
        }
        return DigestUtil.sha256Hex(raw);
    }

    public static String serialize(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof CharSequence) return obj.toString();
        if (obj instanceof Number || obj instanceof Boolean) return obj.toString();
        // Map类型：直接使用FastJSON序列化
        if (obj instanceof Map) {
            return JSON.toJSONString(obj,
                    JSONWriter.Feature.WriteNulls);
        }
        // 对象：反射获取字段，按字母排序后构造成JSON
        Map<String, Object> sortedMap = new TreeMap<>();
        List<Field> fields = getAllFields(obj.getClass());
        fields.sort(Comparator.comparing(Field::getName));
        for (Field f : fields) {
            try {
                f.setAccessible(true);
                Object v = f.get(obj);
                sortedMap.put(f.getName(), formatValue(v));
            } catch (IllegalAccessException e) {
                log.debug("[serialize] 字段访问失败 field={} class={}", f.getName(), obj.getClass().getSimpleName(), e);
            }
        }
        return JSON.toJSONString(sortedMap,
                JSONWriter.Feature.WriteNulls);
    }

    private static Object formatValue(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal bd) {
            return bd.setScale(2, RoundingMode.HALF_UP);
        }
        if (v instanceof LocalDateTime ldt) {
            return ldt.format(DATE_TIME_FMT);
        }
        if (v instanceof LocalDate ld) {
            return ld.format(DATE_FMT);
        }
        if (v instanceof Date d) {
            return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(d);
        }
        return v;
    }

    private static List<Field> getAllFields(Class<?> clazz) {
        List<Field> list = new ArrayList<>();
        while (clazz != null && clazz != Object.class) {
            list.addAll(Arrays.asList(clazz.getDeclaredFields()));
            clazz = clazz.getSuperclass();
        }
        return list;
    }

    /**
     * 计算Merkle树根哈希
     */
    public static String merkleRoot(List<String> hashes) {
        if (hashes == null || hashes.isEmpty()) return sha256Hex("");
        List<String> level = new ArrayList<>(hashes);
        while (level.size() > 1) {
            List<String> next = new ArrayList<>();
            for (int i = 0; i < level.size(); i += 2) {
                String left = level.get(i);
                String right = (i + 1 < level.size()) ? level.get(i + 1) : left;
                next.add(sha256Hex(left + right));
            }
            level = next;
        }
        return level.get(0);
    }
}
