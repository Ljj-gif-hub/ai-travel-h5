<script setup>
import { ref, reactive, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { showToast, showLoadingToast, closeToast } from 'vant';
import { getToken } from '../utils/auth';
import { userApi } from '../api';
import { getMyData, setMyData } from '../utils/userAccountStorage';

const router = useRouter();
const { t } = useI18n();

const goBack = () => {
  router.back();
};

const getDefaultAvatar = () => {
  return 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100"><rect width="100" height="100" fill="#E2E8F0"/><text x="50" y="55" text-anchor="middle" fill="#94A3B8" font-size="14" font-family="sans-serif">No Photo</text></svg>')
}

const userInfo = reactive({
  nickname: '',
  avatar: '',
  bio: '',
  phone: '',
  email: '',
});

const isLoading = ref(false);

const saveProfile = async () => {
  if (!userInfo.nickname.trim()) {
    showToast(t('profile.nicknameRequired'));
    return;
  }

  isLoading.value = true;
  const toast = showLoadingToast({
    message: t('profile.saving'),
    duration: 0,
    position: 'middle',
    forbidClick: true,
  });

  try {
    const response = await userApi.updateProfile({
      nickname: userInfo.nickname,
      avatar: userInfo.avatar,
      bio: userInfo.bio,
      phone: userInfo.phone,
      email: userInfo.email,
    });

    if (response.code === 0) {
      // 【多账号隔离】写入当前账号独立存储
      setMyData('userInfo', response.data)
      localStorage.setItem('userInfo', JSON.stringify(response.data));
      closeToast();
      showToast({ message: t('profile.saveSuccess'), position: 'middle' });
      setTimeout(() => { router.back(); }, 1000);
    } else {
      closeToast();
      showToast(response.message || t('profile.saveFailed'));
    }
  } catch (error) {
    closeToast();
    // 保存失败：不写本地、不回退，提示重试（避免误报"已保存到本地"导致数据丢失）
    showToast({ message: t('profile.saveFailedRetry'), position: 'middle' });
  } finally {
    isLoading.value = false;
  }
};

const loadProfile = async () => {
  try {
    const response = await userApi.getProfile();
    if (response.code === 0) {
      const data = response.data;
      userInfo.nickname = data.nickname || '';
      userInfo.avatar = data.avatar || '';
      userInfo.bio = data.bio || '';
      userInfo.phone = data.phone || '';
      userInfo.email = data.email || '';
    }
  } catch (error) {
    console.log('获取用户资料失败:', error);
    // 【多账号隔离】优先从当前账号本地数据恢复
    const accountData = getMyData('userInfo')
    if (accountData) {
      Object.assign(userInfo, accountData)
    } else {
      const savedUserInfo = localStorage.getItem('userInfo');
      if (savedUserInfo) {
        try { const saved = JSON.parse(savedUserInfo); Object.assign(userInfo, saved); } catch (e) {}
      }
    }
  }
};

onMounted(() => {
  if (getToken()) {
    loadProfile();
  }
});
</script>

<template>
  <div class="edit-profile-page">
    <van-nav-bar
      :title="t('profile.editProfile')"
      :left-text="t('common.back')"
      left-arrow
      safe-area-inset-top
      @click-left="goBack"
    />

    <div class="page-content">
      <div class="avatar-section">
        <van-image
          round
          width="100px"
          height="100px"
          :src="userInfo.avatar || getDefaultAvatar()"
          class="avatar-xl"
        />
        <div class="avatar-tip">{{ t('profile.changeAvatar') }}</div>
      </div>

      <van-cell-group inset class="form-group">
        <van-cell :title="t('profile.nickname')">
          <template #right-icon>
            <van-field
              v-model="userInfo.nickname"
              :placeholder="t('profile.enterNickname')"
              maxlength="20"
              class="field-input"
            />
          </template>
        </van-cell>

        <van-cell :title="t('profile.bio')">
          <template #right-icon>
            <van-field
              v-model="userInfo.bio"
              :placeholder="t('profile.enterBio')"
              maxlength="100"
              class="field-input"
            />
          </template>
        </van-cell>

        <van-cell :title="t('auth.phone')">
          <template #right-icon>
            <van-field
              v-model="userInfo.phone"
              :placeholder="t('auth.enterPhone')"
              type="number"
              maxlength="11"
              class="field-input"
            />
          </template>
        </van-cell>

        <van-cell :title="t('profile.email')">
          <template #right-icon>
            <van-field
              v-model="userInfo.email"
              :placeholder="t('profile.enterEmail')"
              class="field-input"
            />
          </template>
        </van-cell>
      </van-cell-group>

      <div class="submit-area">
        <van-button
          type="primary"
          block
          class="submit-btn"
          :loading="isLoading"
          @click="saveProfile"
        >
          {{ t('common.save') }}
        </van-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.edit-profile-page {
  width: 100%;
  min-height: 100vh;
  background: transparent;
  padding-bottom: calc(var(--tabbar-height) + 20px + var(--safe-area-bottom));
}

.page-content {
  padding: 24px 16px;
  box-sizing: border-box;
}

.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 32px;
}

.avatar-xl {
  border: 3px solid #e9d5ff;
  margin-bottom: 12px;
}

.avatar-tip {
  font-size: 13px;
  color: #9ca3af;
}

.form-group {
  background: #fff;
  border-radius: 16px;
  overflow: hidden;
}

.field-input {
  text-align: right;
  flex: 1;
}

.submit-area {
  padding-top: 32px;
}

.submit-btn {
  background: linear-gradient(135deg, #9333ea 0%, #6366f1 100%) !important;
  border: none !important;
  border-radius: 16px !important;
  font-weight: 600;
  font-size: 16px;
  padding: 14px 0;
}
</style>
