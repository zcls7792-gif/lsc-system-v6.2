package com.lianshengtong.evidence.schedule;

import com.lianshengtong.evidence.service.AsyncChainWriter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * 存证定时刷新调度器
 * <p>
 * 每 100ms 检查队列，有积压则立即刷新。
 * </p>
 */
@Component
public class EvidenceFlushScheduler {

    private static final Logger log = LoggerFactory.getLogger(EvidenceFlushScheduler.class);

    private final AsyncChainWriter asyncChainWriter;

    @Scheduled(fixedRate = 100)
    public void flushCheck() {
        int queueSize = asyncChainWriter.getQueueSize();
        if (queueSize > 0) {
            asyncChainWriter.flushAsyncBatch();
        }
    }


    public EvidenceFlushScheduler(AsyncChainWriter asyncChainWriter) {
        this.asyncChainWriter = asyncChainWriter;
    }

    public AsyncChainWriter getAsyncChainWriter() { return asyncChainWriter; }
}
