<template>
  <van-tabbar v-model="active" route>
    <van-tabbar-item to="/home" icon="home-o">首页</van-tabbar-item>
    <van-tabbar-item to="/products" icon="shop-o">商品</van-tabbar-item>
    <van-tabbar-item to="/orders" icon="orders-o">订单</van-tabbar-item>
    <van-tabbar-item to="/mine" icon="user-o">我的</van-tabbar-item>
  </van-tabbar>
</template>

<script setup lang="ts">
import { ref } from 'vue'
const active = ref(0)
</script>
