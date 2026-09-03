package com.lianshengtong.gateway.gray.spi;

import com.lianshengtong.gateway.gray.GrayPolicyStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 纯内存 SPI 实现（等价于把 GrayPolicyStore 拆出一个只读/写入代理的 InMemory 持久化，
 * 主要用来给 {@code GrayPolicyService} 提供统一的双写模型：即使运行期没有数据库，
 * Service 层仍能正常工作——重启会丢策略，但至少维持"可运行、接口不报错"的退化模式。
 *
 * <p>注意：若未来接入 Nacos/Redis，请新增实现并在 GrayReleaseConfig 中优先注册。
 */
public class InMemoryGrayPolicyRepository implements GrayPolicyRepository {

    private final Map<String, GrayPolicyStore.Policy> data = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<GrayPolicyStore.History> log = new ConcurrentLinkedDeque<>();

    @Override public boolean isAvailable() { return true; }

    @Override public List<GrayPolicyStore.Policy> loadAll() { return new ArrayList<>(data.values()); }

    @Override public void save(GrayPolicyStore.Policy policy) { data.put(policy.policyId(), policy); }

    @Override public Optional<GrayPolicyStore.Policy> findById(String policyId) {
        return Optional.ofNullable(data.get(policyId));
    }

    @Override public void delete(String policyId) { data.remove(policyId); }

    @Override public void appendHistory(GrayPolicyStore.History history) { log.addFirst(history); }

    @Override public List<GrayPolicyStore.History> listHistory(String policyId, int limit) {
        List<GrayPolicyStore.History> out = new ArrayList<>();
        for (GrayPolicyStore.History h : log) {
            if (policyId == null || policyId.equals(h.policyId())) {
                out.add(h);
                if (out.size() >= limit) break;
            }
        }
        return out;
    }
}
