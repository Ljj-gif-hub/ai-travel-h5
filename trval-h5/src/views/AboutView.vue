<script setup>
import { useI18n } from 'vue-i18n';

const { t } = useI18n();
</script>

<template>
  <div class="container">
    <van-cell-group>
      <van-cell :title="t('about.title')" :value="t('about.desc')" />
    </van-cell-group>

    <van-button type="primary" block @click="$router.back()">
      {{ t('common.backHome') }}
    </van-button>
  </div>
</template>

<style scoped>
.container {
  padding: 20px;
}
</style>
