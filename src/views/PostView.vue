<script setup>
import { ref, reactive } from 'vue';
import { useRouter } from 'vue-router';
import { showToast, showLoadingToast, closeToast } from 'vant';
import { getToken } from '../utils/auth';
import { postApi } from '../api';

const router = useRouter();

const goBack = () => {
  router.back();
};

const postForm = reactive({
  content: '',
  images: [],
});

const isLoading = ref(false);

const maxImages = 9;

const addImage = () => {
  if (postForm.images.length >= maxImages) {
    showToast(`最多只能上传${maxImages}张图片`);
    return;
  }
  const mockImage = `https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=beautiful%20travel%20photo${postForm.images.length}&image_size=square`;
  postForm.images.push(mockImage);
};

const removeImage = (index) => {
  postForm.images.splice(index, 1);
};

const submitPost = async () => {
  if (!postForm.content.trim() && postForm.images.length === 0) {
    showToast('内容不能为空');
    return;
  }

  isLoading.value = true;
  const toast = showLoadingToast({
    message: '发布中...',
    duration: 0,
    position: 'middle',
    forbidClick: true,
  });

  try {
    const response = await postApi.createPost({
      content: postForm.content,
      images: postForm.images,
    });

    if (response.code === 0) {
      closeToast();
      showToast({
        message: '发布成功',
        position: 'middle',
      });
      setTimeout(() => {
        router.push('/profile');
      }, 1000);
    } else {
      closeToast();
      showToast(response.message || '发布失败');
    }
  } catch (error) {
    closeToast();
    showToast('发布失败');
  } finally {
    isLoading.value = false;
  }
};
</script>

<template>
  <div class="post-page">
    <van-nav-bar
      title="发动态"
      left-text="返回"
      left-arrow
      safe-area-inset-top
      @click-left="goBack"
    />

    <div class="page-content">
      <van-cell-group inset class="form-group">
        <van-field
          v-model="postForm.content"
          label="内容"
          placeholder="分享你的旅行心情..."
          type="textarea"
          :rows="5"
          maxlength="500"
        />

        <van-cell title="图片">
          <template #right-icon>
            <div class="images-wrap">
              <div
                v-for="(img, index) in postForm.images"
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
                v-if="postForm.images.length < maxImages"
                class="image-add"
                @click="addImage"
              >
                <van-icon name="plus" size="30" color="#ccc" />
              </div>
            </div>
          </template>
        </van-cell>
      </van-cell-group>

      <div class="submit-area">
        <van-button
          type="primary"
          block
          class="submit-btn"
          :loading="isLoading"
          @click="submitPost"
        >
          发布
        </van-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.post-page {
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
