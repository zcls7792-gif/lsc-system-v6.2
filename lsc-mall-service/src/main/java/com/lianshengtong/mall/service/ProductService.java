package com.lianshengtong.mall.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lianshengtong.mall.dto.ProductPublishDTO;
import com.lianshengtong.mall.entity.Product;
import com.lianshengtong.mall.entity.ProductCategory;

import java.util.List;

/**
 * 商品服务接口
 * <p>人民币价格与 LSC 价格强制一致，共用 price 字段(1:1)。</p>
 */
public interface ProductService {

    /**
     * 商品发布
     * <p>发布后置为审核中(status=2)，异步提交 AI 审核。</p>
     *
     * @param dto 发布请求
     * @return 商品ID
     */
    Long publishProduct(ProductPublishDTO dto);

    /**
     * 商品更新
     *
     * @param id  商品ID
     * @param dto 更新请求
     */
    void updateProduct(Long id, ProductPublishDTO dto);

    /**
     * 下架
     */
    void offShelf(Long id);

    /**
     * 上架(需 AI 审核通过或人工通过)
     */
    void onShelf(Long id);

    /**
     * 商品分页列表(类目筛选)
     *
     * @param page       页码
     * @param size       每页条数
     * @param categoryId 类目ID(可空)
     * @param status     状态(可空)
     * @return 分页结果
     */
    IPage<Product> listProducts(Integer page, Integer size, Long categoryId, Integer status);

    /**
     * 商品分页列表(管理后台，支持关键词/商家/状态筛选)
     *
     * @param page       页码
     * @param size       每页条数
     * @param keyword    商品名称关键词(可空)
     * @param merchantId 商家ID(可空)
     * @param status     状态(可空)
     * @return 分页结果
     */
    IPage<Product> listProductsAdmin(Integer page, Integer size, String keyword, Long merchantId, Integer status);

    /**
     * 商品详情
     */
    Product getProductDetail(Long id);

    /**
     * 待审核商品列表(aiReview=2 AI可疑)
     *
     * @param page     页码
     * @param size     每页条数
     * @param aiReview AI审核结果(可空，默认2=AI可疑)
     * @return 分页结果
     */
    IPage<Product> auditList(Integer page, Integer size, Integer aiReview);

    /**
     * 查询商品AI审核结果
     *
     * @param productId 商品ID
     * @return 商品(含aiReview/aiReviewRemark)
     */
    Product getAiReviewResult(Long productId);

    /**
     * 人工复核商品
     *
     * @param productId 商品ID
     * @param pass      true=人工通过(3) false=人工拒绝(4)
     * @param reason    审核备注
     */
    void manualReview(Long productId, Boolean pass, String reason);

    /**
     * 修改商品状态(上架/下架统一入口)
     *
     * @param productId 商品ID
     * @param status    目标状态 0下架 1上架
     */
    void toggleStatus(Long productId, Integer status);

    /**
     * AI 审核结果回调更新
     *
     * @param productId 商品ID
     * @param aiReview  AI审核结果(对应 AiReviewResultEnum)
     * @param remark    审核备注
     */
    void updateAiReview(Long productId, Integer aiReview, String remark);

    /**
     * 全部类目列表(类目树)
     *
     * @return 类目列表
     */
    List<ProductCategory> listAllCategories();

    /**
     * 子类目列表
     *
     * @param parentId 父类目ID(0表示顶级)
     * @return 子类目列表
     */
    List<ProductCategory> listSubCategories(Long parentId);
}
