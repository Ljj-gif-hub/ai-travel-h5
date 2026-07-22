<script setup>
import { ref, reactive } from 'vue';
import { useRouter } from 'vue-router';
import { showToast, showLoadingToast, closeToast, RadioGroup, Radio } from 'vant';
import { getToken } from '../utils/auth';
import { feedbackApi } from '../api';

const router = useRouter();

const goBack = () => {
  router.back();
};

const feedbackForm = reactive({
  type: '',
  content: '',
  images: [],
  contact: '',
});

const isLoading = ref(false);

const feedbackTypes = [
  { label: '功能建议', value: 'suggestion' },
  { label: 'Bug反馈', value: 'bug' },
  { label: '体验优化', value: 'experience' },
  { label: '其他', value: 'other' },
];

const maxImages = 3;

const addImage = () => {
  if (feedbackForm.images.length >= maxImages) {
    showToast(`最多只能上传${maxImages}张图片`);
    return;
  }
  const mockImage = `https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=feedback%20screenshot${feedbackForm.images.length}&image_size=square`;
  feedbackForm.images.push(mockImage);
};

const removeImage = (index) => {
  feedbackForm.images.splice(index, 1);
};

const submitFeedback = async () => {
  if (!feedbackForm.type) {
    showToast('请选择反馈类型');
    return;
  }
  if (!feedbackForm.content.trim()) {
    showToast('请描述您的问题');
    return;
  }

  isLoading.value = true;
  const toast = showLoadingToast({
    message: '提交中...',
    duration: 0,
    position: 'middle',
    forbidClick: true,
  });

  try {
    const response = await feedbackApi.createFeedback({
      type: feedbackForm.type,
      content: feedbackForm.content,
      images: feedbackForm.images,
      contact: feedbackForm.contact,
    });

    if (response.code === 0) {
      closeToast();
      showToast({
        message: '提交成功',
        position: 'middle',
      });
      setTimeout(() => {
        router.push('/profile');
      }, 1000);
    } else {
      closeToast();
      showToast(response.message || '提交失败');
    }
  } catch (error) {
    closeToast();
    showToast('提交失败');
  } finally {
    isLoading.value = false;
  }
};
</script>

<template>
  <div class="feedback-page">
    <van-nav-bar
      title="反馈建议"
      left-text="返回"
      left-arrow
      safe-area-inset-top
      @click-left="goBack"
    />

    <div class="page-content">
      <van-cell-group inset class="form-group">
        <van-cell title="反馈类型">
          <template #right-icon>
            <van-radio-group v-model="feedbackForm.type" direction="horizontal">
              <van-radio
                v-for="item in feedbackTypes"
                :key="item.value"
                :value="item.value"
                :label="item.label"
              />
            </van-radio-group>
          </template>
        </van-cell>

        <van-field
          v-model="feedbackForm.content"
          label="问题描述"
          placeholder="请详细描述您的问题或建议..."
          type="textarea"
          :rows="5"
          maxlength="500"
        />

        <van-cell title="截图">
          <template #right-icon>
            <div class="images-wrap">
              <div
                v-for="(img, index) in feedbackForm.images"
                :key="index"
                class="image-item"
              >
                <van-image
                  width="80px"
                  height="80px"
                  :src="img"
                  fit="cover"
                  round
                />
                <van-icon
                  name="cross"
                  size="20"
                  color="#fff"
                  class="image-delete"
                  @click="removeImage(index)"
                />
              </div>
              <div
                v-if="feedbackForm.images.length < maxImages"
                class="image-add"
                @click="addImage"
              >
                <van-icon name="plus" size="30" color="#ccc" />
              </div>
            </div>
          </template>
        </van-cell>

        <van-field
          v-model="feedbackForm.contact"
          label="联系方式"
          placeholder="请输入手机号或邮箱（选填）"
        />
      </van-cell-group>

      <div class="submit-area">
        <van-button
          type="primary"
          block
          class="submit-btn"
          :loading="isLoading"
          @click="submitFeedback"
        >
          提交反馈
        </van-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.feedback-page {
  width: 100%;
  min-height: 100vh;
  background: linear-gradient(180deg, #faf5ff 0%, #f0f4ff 40%, #f1f5f9 100%);
  padding-bottom: calc(var(--tabbar-height) + 20px + var(--safe-area-bottom));
}

.page-content {
  padding: 16px;
  box-sizing: border-box;
}

.form-group {
  background: #fff;
  border-radius: 16px;
  overflow: hidden;
}

.images-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.image-item {
  position: relative;
}

.image-delete {
  position: absolute;
  top: -8px;
  right: -8px;
  background: #ef4444;
  border-radius: 50%;
  padding: 2px;
}

.image-add {
  width: 80px;
  height: 80px;
  border: 2px dashed #e5e7eb;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
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
