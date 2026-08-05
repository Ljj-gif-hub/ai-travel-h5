<script setup>
import { ref, reactive, computed } from 'vue';
import { useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { showToast, showLoadingToast, closeToast } from 'vant';
import { getToken } from '../utils/auth';
import { postApi, uploadApi } from '../api';

const router = useRouter();
const { t } = useI18n();

const goBack = () => { router.back() };

const postForm = reactive({
  content: '',
  images: [],
  location: '',
});

const isLoading = ref(false);
const isUploading = ref(false);
const fileInput = ref(null);
const maxImages = 9;
const maxLength = 500;
const charCount = computed(() => postForm.content.length);

const triggerUpload = () => {
  fileInput.value?.click();
};

const handleUpload = async (e) => {
  const file = e.target.files?.[0];
  if (!file) return;
  if (postForm.images.length >= maxImages) {
    showToast(t('community.maxImages', { n: maxImages }));
    return;
  }
  isUploading.value = true;
  try {
    const res = await uploadApi.uploadFile(file);
    if (res.code === 0) {
      postForm.images.push(res.data.url);
    } else {
      showToast(res.message || t('community.uploadFailed'));
    }
  } catch {
    showToast(t('community.uploadFailedRetry'));
  } finally {
    isUploading.value = false;
    if (fileInput.value) fileInput.value.value = '';
  }
};

const removeImage = (index) => {
  postForm.images.splice(index, 1);
};

const submitPost = async () => {
  if (!postForm.content.trim() && postForm.images.length === 0) {
    showToast(t('community.enterContentOrAddImage'));
    return;
  }
  if (!getToken()) {
    showToast(t('common.notLoggedIn'));
    return;
  }

  isLoading.value = true;
  showLoadingToast({ message: t('community.publishing'), duration: 0, forbidClick: true });

  try {
    const res = await postApi.createPost({
      content: postForm.content.trim(),
      images: postForm.images,
    });

    if (res.code === 0) {
      closeToast();
      showToast(t('community.publishSuccess'));
      setTimeout(() => router.push('/community'), 800);
    } else {
      closeToast();
      showToast(res.message || t('community.publishFailed'));
    }
  } catch {
    closeToast();
    showToast(t('community.networkErrorRetry'));
  } finally {
    isLoading.value = false;
  }
};
</script>

<template>
  <div class="post-page">
    <!-- 顶部栏 -->
    <div class="post-nav" :style="{ paddingTop: 'env(safe-area-inset-top, 0px)' }">
      <div class="nav-left" @click="goBack">
        <van-icon name="arrow-left" size="22" color="#1f2937" />
      </div>
      <span class="nav-title">{{ t('community.postTitle') }}</span>
      <button
        type="button"
        class="nav-publish"
        :class="{ active: postForm.content.trim() || postForm.images.length }"
        :disabled="isLoading"
        @click="submitPost"
      >
        {{ isLoading ? t('community.publishing') : t('community.publish') }}
      </button>
    </div>

    <!-- 内容区 -->
    <div class="post-content">
      <textarea
        v-model="postForm.content"
        class="post-textarea"
        :placeholder="t('community.shareTravelMood')"
        :maxlength="maxLength"
        rows="6"
      ></textarea>
      <div class="char-hint" :class="{ warn: charCount > maxLength - 50 }">
        {{ charCount }}/{{ maxLength }}
      </div>

      <!-- 图片区 -->
      <div class="images-grid">
        <div v-for="(img, i) in postForm.images" :key="i" class="img-cell">
          <img :src="img" class="img-thumb" loading="lazy" />
          <div class="img-remove" @click="removeImage(i)">
            <van-icon name="cross" size="12" color="#fff" />
          </div>
        </div>
        <div
          v-if="postForm.images.length < maxImages"
          class="img-add"
          :class="{ disabled: isUploading }"
          @click="triggerUpload"
        >
          <van-icon name="plus" size="28" color="#c0c4cc" />
          <template v-if="postForm.images.length === 0">
            <span class="img-add-text">{{ t('community.addImage') }}</span>
          </template>
        </div>
      </div>

      <input
        ref="fileInput"
        type="file"
        accept="image/*"
        style="display:none"
        @change="handleUpload"
      />
    </div>
  </div>
</template>

<style scoped>
.post-page {
  width: 100%; min-height: 100vh;
  background: transparent;
}

/* 顶部栏 */
.post-nav {
  display: flex; align-items: center; justify-content: space-between;
  padding: 8px 12px 8px 4px;
  background: #fff; border-bottom: 1px solid #f3f4f6;
  position: sticky; top: 0; z-index: 100;
}
.nav-left {
  display: flex; align-items: center; justify-content: center;
  width: 36px; height: 36px; cursor: pointer;
}
.nav-title {
  font-size: 17px; font-weight: 600; color: #1e293b;
}
.nav-publish {
  padding: 6px 18px; border: none; border-radius: 18px;
  background: #e5e7eb; color: #9ca3af;
  font-size: 14px; font-weight: 600; cursor: pointer;
  transition: all 0.2s;
}
.nav-publish.active {
  background: linear-gradient(135deg, #8B5CF6, #6366F1);
  color: #fff;
}
.nav-publish:active { transform: scale(0.95); }
.nav-publish:disabled { opacity: 0.6; }

/* 内容 */
.post-content {
  padding: 16px;
}
.post-textarea {
  width: 100%; border: none; outline: none; resize: none;
  font-size: 16px; color: #1e293b; line-height: 1.6;
  font-family: inherit; background: transparent;
}
.post-textarea::placeholder { color: #c0c4cc; }

.char-hint {
  text-align: right; font-size: 12px; color: #c0c4cc;
  margin-top: 4px; padding-right: 4px;
}
.char-hint.warn { color: #f59e0b; }

/* 图片网格 */
.images-grid {
  display: grid; grid-template-columns: repeat(3, 1fr);
  gap: 8px; margin-top: 16px;
}
.img-cell {
  position: relative; aspect-ratio: 1;
  border-radius: 10px; overflow: hidden; background: #f5f5f5;
}
.img-thumb {
  width: 100%; height: 100%; object-fit: cover;
}
.img-remove {
  position: absolute; top: 4px; right: 4px;
  width: 20px; height: 20px; border-radius: 50%;
  background: rgba(0,0,0,0.5); display: flex;
  align-items: center; justify-content: center; cursor: pointer;
}
.img-add {
  aspect-ratio: 1; border: 1.5px dashed #d1d5db;
  border-radius: 10px; display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  gap: 4px; cursor: pointer; transition: all 0.15s;
  color: #c0c4cc;
}
.img-add:active { background: #f8f8f8; border-color: #8B5CF6; }
.img-add.disabled { opacity: 0.4; pointer-events: none; }
.img-add-text { font-size: 11px; }
</style>
