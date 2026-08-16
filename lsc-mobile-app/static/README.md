# static 静态资源说明

本目录存放应用所需的图标、占位图等静态资源。UniApp 编译时会原样拷贝至各平台产物。

> 说明：本仓库不提交实际图片二进制，仅以本文件说明各资源用途与规格，便于设计师/前端补充。

## 资源清单

### 1. tabBar 图标 `tabbar/`
小程序 tabBar 图标要求 PNG，建议尺寸 81×81px（约 162×162rpx）。

| 文件 | 用途 | 建议颜色 |
| --- | --- | --- |
| tabbar/home.png | 首页-未选中 | #999999 |
| tabbar/home-active.png | 首页-选中 | #FF6B00 |
| tabbar/mall.png | 商城-未选中 | #999999 |
| tabbar/mall-active.png | 商城-选中 | #FF6B00 |
| tabbar/cart.png | 购物车-未选中 | #999999 |
| tabbar/cart-active.png | 购物车-选中 | #FF6B00 |
| tabbar/mine.png | 我的-未选中 | #999999 |
| tabbar/mine-active.png | 我的-选中 | #FF6B00 |

### 2. 占位图 `placeholder/`

| 文件 | 用途 | 建议尺寸 |
| --- | --- | --- |
| placeholder/product.png | 商品默认图 | 750×750 |
| placeholder/banner.png | 首页轮播默认图 | 750×320 |
| placeholder/avatar.png | 用户默认头像 | 200×200（圆形裁剪） |

### 3. 图标 `icons/`（可选，可用 emoji 替代）
- icons/scan.png 扫码
- icons/location.png 定位
- icons/service.png 客服

## 注意事项
1. tabBar 图标为微信小程序必填项，缺省会导致 tabBar 不显示图标（仅文字）。
2. 占位图建议使用浅灰底 + 居中 logo/文字，避免大面积空白。
3. 实际项目中可使用 iconfont 字体图标替代部分 PNG，减小包体积。
4. 微信小程序主包体积限制 2MB，静态大图建议走 CDN（接口返回 url）。
