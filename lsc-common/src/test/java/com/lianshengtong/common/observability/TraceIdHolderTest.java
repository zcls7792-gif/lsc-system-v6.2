package com.lianshengtong.common.observability;

import com.lianshengtong.common.result.R;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdHolderTest {

    @AfterEach void clear() { TraceIdHolder.clear(); }

    @Test void createAndSetAndGet() {
        String t = TraceIdHolder.create();
        assertThat(t).hasSize(32);
        TraceIdHolder.set(t);
        assertThat(TraceIdHolder.get()).isEqualTo(t);
        TraceIdHolder.clear();
        assertThat(TraceIdHolder.get()).isNull();
    }

    @Test void currentOrCreate_idempotent() {
        String a = TraceIdHolder.currentOrCreate();
        String b = TraceIdHolder.currentOrCreate();
        assertThat(a).isSameAs(b);
    }

    @Test void okDataAndFailIncludeTraceIdInBody() {
        TraceIdHolder.set("traceA");
        R<String> ok = R.ok("data");
        assertThat(ok.getTraceId()).isEqualTo("traceA");

        TraceIdHolder.clear();
        R<String> fail = R.fail(400, "bad");
        assertThat(fail.getTraceId()).isNotNull().hasSize(32);
    }
}
