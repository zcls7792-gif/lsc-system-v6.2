package com.lianshengtong.gateway.gray.spi;

import com.lianshengtong.gateway.gray.GrayPolicyStore;

import java.util.List;
import java.util.Optional;

/**
 * 灰度策略持久化仓储 SPI。
 * <p>
 * 存在多种部署形态：
 * <ol>
 *   <li>单实例 / 本地开发 → 默认内存实现（已由 GrayPolicyStore 提供）</li>
 *   <li>多实例 / MySQL → {@code JdbcGrayPolicyRepository} 写入 gray_policy / gray_policy_history 表</li>
 *   <li>未来 Redis / Nacos / 配置中心 → 新增实现并在 {@code GrayReleaseConfig} 中注入对应 Bean 即可。</li>
 * </ol>
 * 注意：Stats（命中统计、秒级桶、QPS）**不做持久化**，仅作运行期聚合指标，由 Prometheus 在网关进程存活期抓取即可。
 */
public interface GrayPolicyRepository {

    /** 存储层是否可用（例如 JDBC 需要 datasource 存在；否则回退到纯内存）。 */
    boolean isAvailable();

    /** 启动时一次性加载所有策略到内存，按 createdAt 升序返回（若存在相同 policyId 多次版本，应返回最新）。 */
    List<GrayPolicyStore.Policy> loadAll();

    /** 写入或更新单条策略；实现须保证按 policyId 幂等 upsert。 */
    void save(GrayPolicyStore.Policy policy);

    /** 根据 policyId 查询（管理接口详情用）。 */
    Optional<GrayPolicyStore.Policy> findById(String policyId);

    /** 逻辑删除（预留；当前灰度建议保留历史审计，因此 delete 默认做标记删除或直接物理删除均可）。 */
    void delete(String policyId);

    /** 追加历史（与 Policy 解耦，rollback/setWeight/pause 都会写一条）。 */
    void appendHistory(GrayPolicyStore.History history);

    /** 拉取某策略历史，按时间倒序（最新在前），支持最近 limit 条；policyId=null 返回全局历史。 */
    List<GrayPolicyStore.History> listHistory(String policyId, int limit);
}
