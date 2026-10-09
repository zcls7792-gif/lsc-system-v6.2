package com.zcls.lsc.common.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.zcls.lsc.common.money.Money;
import com.zcls.lsc.common.money.Units;

import java.io.IOException;

/**
 * 4.1：权益落库为 BIGINT，传输为十进制字符串以避免 JavaScript 大整数损失。
 * 人民币分也以字符串传输，前端 Number 避免溢出。
 */
public final class LscJacksonModule {

    private LscJacksonModule() {}

    public static SimpleModule create() {
        SimpleModule module = new SimpleModule("lsc-money-units");
        module.addSerializer(Units.class, new UnitsSerializer());
        module.addDeserializer(Units.class, new UnitsDeserializer());
        module.addSerializer(Money.class, new MoneySerializer());
        module.addDeserializer(Money.class, new MoneyDeserializer());
        return module;
    }

    public static class UnitsSerializer extends JsonSerializer<Units> {
        @Override
        public void serialize(Units value, JsonGenerator gen, SerializerProvider sp) throws IOException {
            gen.writeString(value.toDecimalString());
        }
    }

    public static class UnitsDeserializer extends JsonDeserializer<Units> {
        @Override
        public Units deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            return Units.parse(p.getValueAsString());
        }
    }

    public static class MoneySerializer extends JsonSerializer<Money> {
        @Override
        public void serialize(Money value, JsonGenerator gen, SerializerProvider sp) throws IOException {
            gen.writeString(Long.toString(value.cents()));
        }
    }

    public static class MoneyDeserializer extends JsonDeserializer<Money> {
        @Override
        public Money deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            return Money.parseCents(p.getValueAsString());
        }
    }
}
