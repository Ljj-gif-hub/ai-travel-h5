<script setup>
import { ref, reactive, onMounted } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { showToast, showLoadingToast, closeToast } from 'vant';
import { getToken } from '../utils/auth';
import { noteApi, uploadApi } from '../api';

const router = useRouter();
const route = useRoute();
const { t } = useI18n();

const goBack = () => {
  router.back();
};

const noteForm = reactive({
  id: null,
  title: '',
  content: '',
  cover: '',
  tags: [],
});

const isLoading = ref(false);
const isFormLoading = ref(false);  // BUGID L-WRITE-1 修复：编辑详情加载中标志，加载期间禁用表单交互
const isEdit = ref(false);
const isCoverUploading = ref(false);
const isContentImageUploading = ref(false);
const isContentVideoUploading = ref(false);
const contentImageList = ref([]);  // 正文插图列表
const contentVideoList = ref([]);  // 正文视频列表

// 从HTML中提取图片URL列表
const extractContentImages = (html) => {
  if (!html) return []
  const regex = /<img[^>]*\bsrc="([^">]+)"[^>]*>/gi
  const result = []
  let match
  while ((match = regex.exec(html)) !== null) {
    if (match[1]) result.push(match[1])
  }
  return result
}

// 从HTML中提取视频URL列表
const extractContentVideos = (html) => {
  if (!html) return []
  const regex = /<video[^>]*src="([^">]+)"[^>]*>/gi
  const result = []
  let match
  while ((match = regex.exec(html)) !== null) {
    if (match[1]) result.push(match[1])
  }
  return result
}

// 去掉HTML标签，保留纯文本
const stripContentHtml = (html) => {
  if (!html) return ''
  let text = html.replace(/<img[^>]*>/gi, '[图片]')
  text = text.replace(/<video[^>]*>[\s\S]*?<\/video>/gi, '[视频]')
  text = text.replace(/<video[^>]*\/?>/gi, '[视频]')
  text = text.replace(/<br\s*\/?>/gi, '\n')
  text = text.replace(/<[^>]+>/g, '')
  text = text.replace(/&nbsp;/g, ' ').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&')
  return text.trim()
}

// 上传封面图
const handleCoverUpload = async (e) => {
  const file = e.target.files?.[0];
  if (!file) return;
  isCoverUploading.value = true;
  try {
    const res = await uploadApi.uploadFile(file);
    if (res.code === 0 && res.data.type === 'image') {
      noteForm.cover = res.data.url;
      showToast(t('note.coverUploadSuccess'));
    } else {
      showToast(res.message || t('note.pleaseUploadImage'));
    }
  } catch (e) {
    showToast(t('note.uploadFailed'));
  } finally {
    isCoverUploading.value = false;
    if (e.target) e.target.value = '';
  }
};

// 正文插图上传 — 存入图片列表而非拼接HTML到textarea
const handleContentImageUpload = async (e) => {
  const file = e.target.files?.[0];
  if (!file) return;
  isContentImageUploading.value = true;
  try {
    const res = await uploadApi.uploadFile(file);
    if (res.code === 0 && res.data.type === 'image') {
      contentImageList.value.push(res.data.url);
      showToast(t('note.imageAdded'));
    } else {
      showToast(res.message || t('note.pleaseUploadImage'));
    }
  } catch (e) {
    showToast(t('note.uploadFailed'));
  } finally {
    isContentImageUploading.value = false;
    if (e.target) e.target.value = '';
  }
};

// 正文视频上传
const handleContentVideoUpload = async (e) => {
  const file = e.target.files?.[0];
  if (!file) return;
  // 前端预检：文件大小（与后端 200MB 对齐，超限快速提示避免白传）
  if (file.size > 200 * 1024 * 1024) {
    showToast(t('note.videoTooLarge'));
    if (e.target) e.target.value = '';
    return;
  }
  isContentVideoUploading.value = true;
  try {
    const res = await uploadApi.uploadFile(file);
    if (res.code === 0 && res.data.type === 'video') {
      contentVideoList.value.push(res.data.url);
      showToast(t('note.videoAdded'));
    } else {
      showToast(res.message || t('note.pleaseUploadVideo'));
    }
  } catch (err) {
    console.error('视频上传失败:', err);
    showToast(err.message || t('note.uploadFailedNetwork'));
  } finally {
    isContentVideoUploading.value = false;
    if (e.target) e.target.value = '';
  }
};

// 移除已添加的图片
const removeContentImage = (index) => {
  contentImageList.value.splice(index, 1);
};

// 移除已添加的视频
const removeContentVideo = (index) => {
  contentVideoList.value.splice(index, 1);
};

// 构建最终保存的HTML内容
const buildContentHtml = () => {
  let html = noteForm.content || '';
  let imgIdx = 0;
  let videoIdx = 0;
  // 原位插回占位符，保证编辑后图片/视频位置不变（不再全部追加到文末）
  html = html.replace(/\[图片\]/g, () => {
    const url = contentImageList.value[imgIdx++];
    return url
      ? `<img src="${url}" style="max-width:100%;border-radius:8px;margin:8px 0;" />`
      : '';
  });
  html = html.replace(/\[视频\]/g, () => {
    const url = contentVideoList.value[videoIdx++];
    return url
      ? `<video src="${url}" controls style="width:100%;max-height:400px;border-radius:8px;margin:8px 0;background:#000;"></video>`
      : '';
  });
  // 新建时正文无占位符，媒体追加到文末
  for (let i = imgIdx; i < contentImageList.value.length; i++) {
    html += `\n<img src="${contentImageList.value[i]}" style="max-width:100%;border-radius:8px;margin:8px 0;" />`;
  }
  for (let i = videoIdx; i < contentVideoList.value.length; i++) {
    html += `\n<video src="${contentVideoList.value[i]}" controls style="width:100%;max-height:400px;border-radius:8px;margin:8px 0;background:#000;"></video>`;
  }
  return html.trim();
};

const loadNote = async () => {
  const id = route.query.id;
  if (id) {
    isEdit.value = true;
    // BUGID L-WRITE-1 修复：加载期间禁用表单交互，避免响应晚到覆盖用户已输入内容
    isFormLoading.value = true;
    try {
      const response = await noteApi.getNoteDetail(id);
      if (response.code === 0) {
        const data = response.data;
        noteForm.id = data.id;
        noteForm.title = data.title || '';
        // 拆分内容：图片/视频列表 + 纯文本
        contentImageList.value = extractContentImages(data.content);
        contentVideoList.value = extractContentVideos(data.content);
        noteForm.content = stripContentHtml(data.content);
        noteForm.cover = data.cover || '';
        noteForm.tags = data.tags || [];
      }
    } catch (error) {
      console.log('获取游记详情失败:', error);
    } finally {
      isFormLoading.value = false;
    }
  }
};

const saveNote = async () => {
  if (!noteForm.title.trim()) {
    showToast(t('note.titleRequired'));
    return;
  }
  if (!noteForm.content.trim()) {
    showToast(t('note.contentRequired'));
    return;
  }

  isLoading.value = true;
  const toast = showLoadingToast({
    message: t('note.saving'),
    duration: 0,
    position: 'middle',
    forbidClick: true,
  });

  try {
    let response;
    const saveContent = buildContentHtml();
    if (isEdit.value && noteForm.id) {
      response = await noteApi.updateNote(noteForm.id, {
        title: noteForm.title,
        content: saveContent,
        cover: noteForm.cover,
        tags: noteForm.tags,
      });
    } else {
      response = await noteApi.createNote({
        title: noteForm.title,
        content: saveContent,
        cover: noteForm.cover,
        tags: noteForm.tags,
      });
    }

    if (response.code === 0) {
      closeToast();
      showToast({
        message: isEdit.value ? t('note.updateSuccess') : t('note.publishSuccess'),
        position: 'middle',
      });
      setTimeout(() => {
        const noteId = response.data?.id || noteForm.id;
        if (noteId) {
          router.push(`/note-detail?id=${noteId}`);
        } else {
          router.push('/notes');
        }
      }, 1000);
    } else {
      closeToast();
      showToast(response.message || t('note.saveFailed'));
    }
  } catch (error) {
    closeToast();
    showToast(t('note.saveFailed'));
  } finally {
    isLoading.value = false;
  }
};

onMounted(() => {
  if (getToken()) {
    loadNote();
  }
});
</script>

<template>
  <div class="write-note-page">
    <van-nav-bar
      :title="isEdit ? t('note.editNote') : t('community.write')"
      :left-text="t('common.back')"
      left-arrow
      safe-area-inset-top
      @click-left="goBack"
    />

    <div class="page-content">
      <van-cell-group inset class="form-group">
        <van-field
          v-model="noteForm.title"
          :label="t('note.titleLabel')"
          :placeholder="t('note.titlePlaceholder')"
          maxlength="50"
          :disabled="isFormLoading"
        />

        <van-field
          v-model="noteForm.content"
          :label="t('note.contentLabel')"
          :placeholder="t('note.shareTravelStory')"
          type="textarea"
          :rows="10"
          maxlength="2000"
          :disabled="isFormLoading"
        />
        <!-- 正文图片/视频预览 + 上传按钮 -->
        <div class="content-media-section">
          <!-- 图片预览 -->
          <div class="content-images-list" v-if="contentImageList.length">
            <div class="content-media-item" v-for="(img, idx) in contentImageList" :key="'img-'+idx">
              <van-image :src="img" fit="cover" width="80" height="80" radius="8" />
              <van-icon name="close" size="14" color="#fff" class="media-remove-btn" @click="removeContentImage(idx)" />
            </div>
          </div>
          <!-- 视频预览 -->
          <div class="content-videos-list" v-if="contentVideoList.length">
            <div class="content-media-item" v-for="(vid, idx) in contentVideoList" :key="'vid-'+idx">
              <video :src="vid" class="preview-video-thumb" />
              <van-icon name="close" size="14" color="#fff" class="media-remove-btn" @click="removeContentVideo(idx)" />
            </div>
          </div>
          <!-- 上传按钮：label 包裹 input + span，原生触发 -->
          <div class="upload-btns-row">
            <label class="upload-label">
              <input type="file" accept="image/*" hidden @change="handleContentImageUpload" />
              <van-icon name="photograph" size="16" />
              <span>{{ t('note.addImage') }}</span>
            </label>
            <label class="upload-label">
              <input type="file" accept="video/*" hidden @change="handleContentVideoUpload" />
              <van-icon name="video-o" size="16" />
              <span>{{ t('note.addVideo') }}</span>
            </label>
          </div>
          <span class="toolbar-hint">{{ t('note.formatHint') }}</span>
        </div>

        <van-cell :title="t('note.cover')">
          <template #right-icon>
            <label class="cover-upload">
              <input
                type="file"
                accept="image/*"
                hidden
                @change="handleCoverUpload"
              />
              <van-loading v-if="isCoverUploading" size="24px" />
              <van-image
                v-else-if="noteForm.cover"
                width="80px"
                height="80px"
                :src="noteForm.cover"
                fit="cover"
                round
              />
              <van-icon v-else name="plus" size="40" color="#ccc" />
            </label>
          </template>
        </van-cell>

        <van-cell :title="t('note.tags')">
          <template #right-icon>
            <div class="tags-wrap">
              <van-tag
                v-for="(tag, index) in noteForm.tags"
                :key="index"
                closable
                @close="noteForm.tags.splice(index, 1)"
              >
                {{ tag }}
              </van-tag>
              <van-button size="small" type="default" v-if="noteForm.tags.length < 5">{{ t('note.add') }}</van-button>
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
          :disabled="isFormLoading"
          @click="saveNote"
        >
          {{ isEdit ? t('note.update') : t('note.publish') }}
        </van-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.write-note-page {
  width: 100%;
  min-height: 100vh;
  background: transparent;
  padding-bottom: calc(var(--tabbar-height) + 20px + var(--safe-area-bottom));
}

.page-content {
  padding: 16px;
  box-sizing: border-box;
}

.form-group {
  background:
    linear-gradient(160deg, rgba(255,255,255,0.5) 0%, rgba(255,255,255,0.12) 35%, rgba(255,255,255,0.3) 100%),
    rgba(255, 255, 255, 0.4);
  backdrop-filter: blur(14px) saturate(160%);
  -webkit-backdrop-filter: blur(14px) saturate(160%);
  border: 1px solid rgba(255,255,255,0.5);
  border-radius: 16px;
  overflow: hidden;
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.45);
}
/* 玻璃化 Vant 单元格 */
.form-group :deep(.van-cell-group) { background: transparent !important; }
.form-group :deep(.van-cell) { background: transparent !important; }
.form-group :deep(.van-cell::after) { border-color: rgba(0,0,0,0.04) !important; }
.form-group :deep(.van-field__control) { color: #1E293B; }
.form-group :deep(.van-field__control::placeholder) { color: #94A3B8; }

.content-media-section {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: rgba(255,255,255,0.3);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
  border-top: 1px solid rgba(255,255,255,0.3);
}
.content-images-list,
.content-videos-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  width: 100%;
}
.content-media-item {
  position: relative;
}
.preview-video-thumb {
  width: 80px;
  height: 80px;
  object-fit: cover;
  border-radius: 8px;
  background: #000;
}
.media-remove-btn {
  position: absolute;
  top: 2px;
  right: 2px;
  background: rgba(0,0,0,0.5);
  border-radius: 50%;
  padding: 2px;
  cursor: pointer;
}
.upload-btns-row {
  display: flex;
  gap: 8px;
}
.upload-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border: 1px solid #dcdee0;
  border-radius: 6px;
  font-size: 12px;
  color: #323233;
  background: rgba(255,255,255,0.5); backdrop-filter: blur(6px); -webkit-backdrop-filter: blur(6px);
  cursor: pointer;
  transition: all 0.2s;
}
.upload-label:active {
  background: rgba(255,255,255,0.7);
  transform: scale(0.96);
}
.toolbar-hint {
  font-size: 11px;
  color: #bbb;
  width: 100%;
}

.cover-upload {
  width: 80px;
  height: 80px;
  border: 2px dashed #e5e7eb;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: border-color 0.2s;
}
.cover-upload:hover {
  border-color: #8B5CF6;
}

.tags-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
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
