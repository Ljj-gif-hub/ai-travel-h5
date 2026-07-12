<script setup>
</script>

<template>
  <div class="container">
    <van-cell-group>
      <van-cell title="About Page" value="关于页面" />
    </van-cell-group>
    
    <van-button type="primary" block @click="$router.back()">
      返回首页
    </van-button>
  </div>
</template>

<style scoped>
.container {
  padding: 20px;
}
</style>
