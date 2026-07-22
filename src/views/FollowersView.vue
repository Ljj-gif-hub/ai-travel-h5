<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { showToast, showLoadingToast, closeToast } from 'vant';
import { getToken } from '../utils/auth';
import { followApi } from '../api';

const router = useRouter();

const goBack = () => {
  router.back();
};

const followers = ref([]);
const isLoading = ref(false);

const loadFollowers = async () => {
  isLoading.value = true;
  try {
    const response = await followApi.getFollowers();
    if (response.code === 0) {
      followers.value = response.data;
    } else {
      followers.value = [];
    }
  } catch (error) {
    console.log('获取粉丝列表失败:', error);
    followers.value = [];
  } finally {
    isLoading.value = false;
  }
};

const handleFollow = async (id) => {
  try {
    const response = await followApi.follow(id);
    if (response.code === 0) {
      showToast('关注成功');
      loadFollowers();
    } else {
      showToast(response.message || '关注失败');
    }
  } catch (error) {
    showToast('关注失败');
  }
};

const handleUnfollow = async (id) => {
  try {
    const response = await followApi.unfollow(id);
    if (response.code === 0) {
      showToast('取消关注成功');
      loadFollowers();
    } else {
      showToast(response.message || '取消关注失败');
    }
  } catch (error) {
    showToast('取消关注失败');
  }
};

onMounted(() => {
  if (getToken()) {
    loadFollowers();
  }
});
</script>

<template>
  <div class="followers-page">
    <van-nav-bar
      title="我的粉丝"
      left-text="返回"
      left-arrow
      safe-area-inset-top
      @click-left="goBack"
    />

    <div class="content-area">
      <van-skeleton v-if="isLoading" title row="3" />

      <template v-else-if="followers.length === 0">
        <div class="empty-state">
          <van-icon name="user-o" size="60" color="#ccc" />
          <div class="empty-text">暂无粉丝</div>
          <van-button type="primary" size="small" @click="goBack">去看看</van-button>
        </div>
      </template>

      <div v-else class="followers-list">
        <div
          v-for="user in followers"
          :key="user.id"
          class="user-item"
        >
          <van-image
            round
            width="60px"
            height="60px"
            :src="user.avatar || 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=professional%20portrait%20photo&image_size=square'"
            class="user-avatar"
          />
          <div class="user-info">
            <div class="user-name">{{ user.nickname }}</div>
            <div v-if="user.bio" class="user-bio">{{ user.bio }}</div>
          </div>
          <van-button
            size="small"
            :type="user.isFollowed ? 'default' : 'primary'"
            @click="user.isFollowed ? handleUnfollow(user.id) : handleFollow(user.id)"
          >
            {{ user.isFollowed ? '已关注' : '+ 关注' }}
          </van-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.followers-page {
  width: 100%;
  min-height: 100vh;
  background: linear-gradient(180deg, #faf5ff 0%, #f0f4ff 40%, #f1f5f9 100%);
  padding-bottom: calc(var(--tabbar-height) + 20px + var(--safe-area-bottom));
}

.content-area {
  padding: 16px;
  box-sizing: border-box;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
}

.empty-text {
  margin-top: 16px;
  font-size: 15px;
  color: #9ca3af;
}

.followers-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.user-item {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff;
  border-radius: 12px;
  padding: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.user-avatar {
  flex-shrink: 0;
}

.user-info {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.user-name {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
}

.user-bio {
  font-size: 12px;
  color: #9ca3af;
  margin-top: 2px;
}
</style>
