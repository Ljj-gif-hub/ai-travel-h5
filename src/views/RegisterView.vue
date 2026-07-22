<script setup>
/**
 * RegisterView.vue — 已合并至 LoginView.vue
 *
 * 路由 /register 现在直接指向 LoginView.vue（initialTab='register'）
 * 本文件作为兜底保留，以防旧版引用；实际使用时直接跳转到 /register
 */
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

onMounted(() => {
  router.replace('/register')
})
</script>

<template>
  <div class="redirect-placeholder" />
</template>

<style scoped>
.redirect-placeholder {
  min-height: 100vh;
  background: linear-gradient(180deg, #5B67CA 0%, #C8F0E8 100%);
}
</style>
