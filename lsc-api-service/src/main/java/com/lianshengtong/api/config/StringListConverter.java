package com.lianshengtong.api.config;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * List<String> <-> 数据库字符串 转换器
 * 存储格式：逗号分隔。读取时按逗号切分。
 * 用于商品图片列表等字段的持久化。
 */
@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {

    private static final String SEP = ",";

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "";
        }
        return String.join(SEP, attribute);
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(dbData.split(SEP, -1)));
    }
}
