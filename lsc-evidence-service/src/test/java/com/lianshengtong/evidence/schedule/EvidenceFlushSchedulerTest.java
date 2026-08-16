package com.lianshengtong.evidence.schedule;

import com.lianshengtong.evidence.service.AsyncChainWriter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.mockito.Mockito.*;

/**
 * EvidenceFlushScheduler 单元测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("存证定时刷新调度器测试")
class EvidenceFlushSchedulerTest {

    @Mock
    private AsyncChainWriter asyncChainWriter;

    private EvidenceFlushScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new EvidenceFlushScheduler(asyncChainWriter);
    }

    @Test
    @DisplayName("flushCheck - 队列为空时不调用 flushAsyncBatch")
    void testFlushCheck_EmptyQueue_DoesNotFlush() {
        when(asyncChainWriter.getQueueSize()).thenReturn(0);

        scheduler.flushCheck();

        verify(asyncChainWriter, never()).flushAsyncBatch();
    }

    @Test
    @DisplayName("flushCheck - 队列有元素时触发 flushAsyncBatch")
    void testFlushCheck_NonEmptyQueue_Flushes() {
        when(asyncChainWriter.getQueueSize()).thenReturn(5);

        scheduler.flushCheck();

        verify(asyncChainWriter).flushAsyncBatch();
    }

    @Test
    @DisplayName("flushCheck - 队列持续积压时多次触发 flush")
    void testFlushCheck_PersistentBacklog() {
        when(asyncChainWriter.getQueueSize()).thenReturn(10, 8, 3);

        scheduler.flushCheck();
        scheduler.flushCheck();
        scheduler.flushCheck();

        verify(asyncChainWriter, times(3)).flushAsyncBatch();
    }

    @Test
    @DisplayName("flushCheck - 刚好队列清空则不再 flush")
    void testFlushCheck_DrainsThenStops() {
        when(asyncChainWriter.getQueueSize()).thenReturn(1, 0);

        scheduler.flushCheck();
        scheduler.flushCheck();

        verify(asyncChainWriter, times(1)).flushAsyncBatch();
    }

    @Test
    @DisplayName("getAsyncChainWriter - 返回构造器传入的 writer")
    void testGetAsyncChainWriter_ReturnsInjected() {
        Assertions.assertSame(asyncChainWriter, scheduler.getAsyncChainWriter());
    }
}
