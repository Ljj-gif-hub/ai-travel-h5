<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'

const router = useRouter()

const goBack = () => {
  if (window.history.length <= 1) {
    router.push('/')
  } else {
    router.back()
  }
}

const userInfo = ref({
  avatar: 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=professional%20portrait%20photo%20of%20a%20young%20asian%20man%20with%20clean%20background&image_size=square',
  nickname: '旅行者',
  level: 'VIP会员',
  points: 2680,
})

const menuList = [
  { name: '我的订单', icon: 'orders', badge: 3 },
  { name: '我的收藏', icon: 'star-o', badge: 12 },
  { name: '优惠券', icon: 'ticket', badge: 5 },
  { name: '地址管理', icon: 'location-o', badge: 0 },
  { name: '我的足迹', icon: 'footprint', badge: 0 },
  { name: '设置', icon: 'setting-o', badge: 0 },
]

const handleMenuClick = (item) => {
  showToast(`点击了 ${item.name}`)
}

const handleLogout = async () => {
  try {
    await showConfirmDialog({
      title: '确认退出',
      message: '确定要退出登录吗？',
    })
    showToast('退出成功')
    setTimeout(() => {
      window.location.href = '/'
    }, 1500)
  } catch (e) {
    console.log('取消退出')
  }
}
</script>

<template>
  <div class="profile-page">
    <!-- 顶部导航栏 -->
    <van-nav-bar
      title="个人主页"
      left-text="返回"
      left-arrow
      safe-area-inset-top
      class="nav-bar"
      @click-left="goBack"
    />

    <!-- 用户信息卡片 -->
    <div class="user-card">
      <van-image
        round
        width="64px"
        height="64px"
        :src="userInfo.avatar"
        class="avatar"
      />
      <div class="user-details">
        <div class="nickname">{{ userInfo.nickname }}</div>
        <div class="meta-row">
          <span class="level-tag">{{ userInfo.level }}</span>
          <span class="points">积分: {{ userInfo.points }}</span>
        </div>
      </div>
    </div>

    <!-- 功能菜单列表 -->
    <van-cell-group inset class="menu-list">
      <van-cell
        v-for="(item, index) in menuList"
        :key="index"
        :title="item.name"
        @click="handleMenuClick(item)"
      >
        <template #icon>
          <van-icon :name="item.icon" size="20" color="#667eea" class="menu-icon" />
        </template>
        <template #extra>
          <van-badge v-if="item.badge > 0" :content="item.badge" class="menu-badge" />
          <!-- 使用自定义浅色箭头替代默认箭头 -->
          <van-icon name="arrow" size="16" color="#ccc" />
        </template>
      </van-cell>
    </van-cell-group>

    <!-- 退出登录按钮 -->
    <div class="action-area">
      <van-button type="primary" block class="logout-btn" @click="handleLogout">
        退出登录
      </van-button>
    </div>
  </div>
</template>

<style scoped>
.profile-page {
  min-height: 100vh;
  background-color: #f7f8fa; /* 页面基础底色 */
  padding-bottom: calc(20px + env(safe-area-inset-bottom));
}

/* 导航栏样式统一 */
:deep(.nav-bar) {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
:deep(.nav-bar .van-nav-bar__title) {
  color: #fff;
  font-weight: 500;
}
:deep(.nav-bar .van-nav-bar__left) {
  color: #fff;
}

/* 用户信息卡片优化 */
.user-card {
  margin: 16px 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 16px;
  padding: 20px;
  display: flex;
  align-items: center;
  color: #fff;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
}

.avatar {
  border: 2px solid rgba(255, 255, 255, 0.3);
  flex-shrink: 0;
}

.user-details {
  margin-left: 16px;
  flex: 1;
}

.nickname {
  font-size: 18px;
  font-weight: bold;
  margin-bottom: 8px;
}

.meta-row {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
}

.level-tag {
  background: rgba(255, 255, 255, 0.25);
  padding: 2px 8px;
  border-radius: 10px;
}

.points {
  opacity: 0.9;
}

/* 列表组优化 */
.menu-list {
  margin: 0 20px;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

/* 移除单元格自带的分隔线视觉干扰，使用背景色块区分 */
.menu-list :deep(.van-cell) {
  background-color: #fff;
  border-bottom: 1px solid #f5f6f7; /* 使用极浅的分割线 */
}

.menu-list :deep(.van-cell:last-child) {
  border-bottom: none;
}

/* 调整图标和文字间距 */
.menu-icon {
  margin-right: 8px;
}

/* 调整角标位置 */
.menu-badge {
  margin-right: 4px;
}

/* 底部操作区 */
.action-area {
  padding: 24px 20px 0;
}

.logout-btn {
  background: linear-gradient(135deg, #ff9a56 0%, #ff6b6b 100%) !important;
  border: none !important;
  border-radius: 20px;
  font-weight: 500;
  letter-spacing: 1px;
}
</style>67=-0