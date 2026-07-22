<script setup>
import { ref, reactive, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { showToast, showLoadingToast, closeToast } from 'vant';
import { getToken } from '../utils/auth';
import { userApi } from '../api';
import { getMyData, setMyData } from '../utils/userAccountStorage';

const router = useRouter();

const goBack = () => {
  router.back();
};

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
    showToast('昵称不能为空');
    return;
  }

  isLoading.value = true;
  const toast = showLoadingToast({
    message: '保存中...',
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
      showToast({ message: '保存成功', position: 'middle' });
      setTimeout(() => { router.back(); }, 1000);
    } else {
      closeToast();
      showToast(response.message || '保存失败');
    }
  } catch (error) {
    closeToast();
    // 【多账号隔离】离线保存到当前账号
    setMyData('userInfo', { ...userInfo })
    localStorage.setItem('userInfo', JSON.stringify(userInfo));
    showToast({ message: '已保存到本地', position: 'middle' });
    setTimeout(() => { router.back(); }, 1000);
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
      title="编辑资料"
      left-text="返回"
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
          :src="userInfo.avatar || 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=professional%20portrait%20photo%20of%20a%20young%20asian%20person%20with%20clean%20background&image_size=square'"
          class="avatar-xl"
        />
        <div class="avatar-tip">点击更换头像</div>
      </div>

      <van-cell-group inset class="form-group">
        <van-cell title="昵称">
          <template #right-icon>
            <van-field
              v-model="userInfo.nickname"
              placeholder="请输入昵称"
              maxlength="20"
              class="field-input"
            />
          </template>
        </van-cell>

        <van-cell title="个性签名">
          <template #right-icon>
            <van-field
              v-model="userInfo.bio"
              placeholder="请输入个性签名"
              maxlength="100"
              class="field-input"
            />
          </template>
        </van-cell>

        <van-cell title="手机号">
          <template #right-icon>
            <van-field
              v-model="userInfo.phone"
              placeholder="请输入手机号"
              type="number"
              maxlength="11"
              class="field-input"
            />
          </template>
        </van-cell>

        <van-cell title="邮箱">
          <template #right-icon>
            <van-field
              v-model="userInfo.email"
              placeholder="请输入邮箱"
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
          保存
        </van-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.edit-profile-page {
  width: 100%;
  min-height: 100vh;
  background: linear-gradient(180deg, #faf5ff 0%, #f0f4ff 40%, #f1f5f9 100%);
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
