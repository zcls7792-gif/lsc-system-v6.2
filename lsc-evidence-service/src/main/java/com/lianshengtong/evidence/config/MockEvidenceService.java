package com.lianshengtong.evidence.config;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianshengtong.evidence.entity.BlockchainRecord;
import com.lianshengtong.evidence.entity.DailySnapshotRecord;
import com.lianshengtong.evidence.service.EvidenceService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
@Primary
@Profile("standalone")
@ConditionalOnProperty(name = "lsc.evidence.mock-service", havingValue = "true", matchIfMissing = true)
public class MockEvidenceService implements EvidenceService {

    @Override
    public String saveEvidence(String bizType, String bizId, String dataHash, String payload) {
        return "MOCK-" + System.currentTimeMillis();
    }

    @Override
    public DailySnapshotRecord dailySnapshot(LocalDate date) {
        DailySnapshotRecord record = new DailySnapshotRecord();
        record.setSnapshotDate(date != null ? date : LocalDate.now().minusDays(1));
        record.setStatus(1);
        record.setMerkleRoot("0x" + "0".repeat(64));
        return record;
    }

    @Override
    public List<BlockchainRecord> query(String bizType, String bizId) {
        return Collections.emptyList();
    }

    @Override
    public boolean verify(LocalDate date) {
        return true;
    }

    @Override
    public IPage<BlockchainRecord> listPage(Integer page, Integer size, String batchNo, String hash,
                                             String txId, String startDate, String endDate) {
        return new Page<>(page, size);
    }

    @Override
    public BlockchainRecord getById(Long id) {
        BlockchainRecord record = new BlockchainRecord();
        record.setId(id);
        record.setBizType("MOCK");
        record.setBizId("MOCK-" + id);
        record.setStatus(1);
        return record;
    }

    @Override
    public Map<String, Object> verifyReport(LocalDate date) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("date", date.toString());
        report.put("total", 0);
        report.put("passed", 0);
        report.put("failed", 0);
        report.put("verified", true);
        return report;
    }
}