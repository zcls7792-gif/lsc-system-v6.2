<template>
  <div class="page">
    <div class="profile-card">
      <div class="avatar">👤</div>
      <div class="username">LSC 用户</div>
      <div class="user-sub">链盛通会员</div>
    </div>

    <van-cell-group inset style="margin-top: 16px">
      <van-cell title="权益中心" is-link to="/account" icon="balance-o" />
      <van-cell title="我的订单" is-link to="/orders" icon="orders-o" />
      <van-cell title="优惠券" is-link icon="coupon-o" />
      <van-cell title="设置" is-link icon="setting-o" />
    </van-cell-group>
  </div>
</template>

<script setup lang="ts">
</script>

<style scoped>
.page { padding-bottom: 50px; }
.profile-card {
  background: linear-gradient(135deg, #1989fa, #4fc08d);
  border-radius: 12px; padding: 40px 20px;
  color: #fff; text-align: center; margin: 16px;
}
.avatar { font-size: 56px; }
.username { font-size: 20px; font-weight: bold; margin-top: 12px; }
.user-sub { font-size: 12px; opacity: 0.8; margin-top: 4px; }
</style>
