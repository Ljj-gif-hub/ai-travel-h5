<script setup>
import { ref, reactive, onMounted } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { showToast, showLoadingToast, closeToast } from 'vant';
import { getToken } from '../utils/auth';
import { noteApi, uploadApi } from '../api';

const router = useRouter();
const route = useRoute();

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
      showToast('封面上传成功');
    } else {
      showToast(res.message || '请上传图片文件');
    }
  } catch (e) {
    showToast('上传失败');
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
      showToast('图片已添加');
    } else {
      showToast(res.message || '请上传图片文件');
    }
  } catch (e) {
    showToast('上传失败');
  } finally {
    isContentImageUploading.value = false;
    if (e.target) e.target.value = '';
  }
};

// 正文视频上传
const handleContentVideoUpload = async (e) => {
  const file = e.target.files?.[0];
  if (!file) return;
  // 前端预检：文件大小
  if (file.size > 1024 * 1024 * 1024) {
    showToast('视频大小不能超过1GB');
    if (e.target) e.target.value = '';
    return;
  }
  isContentVideoUploading.value = true;
  try {
    const res = await uploadApi.uploadFile(file);
    if (res.code === 0 && res.data.type === 'video') {
      contentVideoList.value.push(res.data.url);
      showToast('视频已添加');
    } else {
      showToast(res.message || '请上传视频文件');
    }
  } catch (err) {
    console.error('视频上传失败:', err);
    showToast(err.message || '上传失败，请检查网络连接');
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
  // 图片
  contentImageList.value.forEach(url => {
    html += `\n<img src="${url}" style="max-width:100%;border-radius:8px;margin:8px 0;" />`;
  });
  // 视频
  contentVideoList.value.forEach(url => {
    html += `\n<video src="${url}" controls style="width:100%;max-height:400px;border-radius:8px;margin:8px 0;background:#000;"></video>`;
  });
  return html.trim();
};

const loadNote = async () => {
  const id = route.query.id;
  if (id) {
    isEdit.value = true;
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
    }
  }
};

const saveNote = async () => {
  if (!noteForm.title.trim()) {
    showToast('标题不能为空');
    return;
  }
  if (!noteForm.content.trim()) {
    showToast('内容不能为空');
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
        message: isEdit.value ? '更新成功' : '发布成功',
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
      showToast(response.message || '保存失败');
    }
  } catch (error) {
    closeToast();
    showToast('保存失败');
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
      :title="isEdit ? '编辑游记' : '写游记'"
      left-text="返回"
      left-arrow
      safe-area-inset-top
      @click-left="goBack"
    />

    <div class="page-content">
      <van-cell-group inset class="form-group">
        <van-field
          v-model="noteForm.title"
          label="标题"
          placeholder="请输入游记标题"
          maxlength="50"
        />

        <van-field
          v-model="noteForm.content"
          label="内容"
          placeholder="分享你的旅行故事..."
          type="textarea"
          :rows="10"
          maxlength="2000"
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
              <span>添加图片</span>
            </label>
            <label class="upload-label">
              <input type="file" accept="video/*" hidden @change="handleContentVideoUpload" />
              <van-icon name="video-o" size="16" />
              <span>添加视频</span>
            </label>
          </div>
          <span class="toolbar-hint">支持 jpg/png/gif/webp/mp4，可添加多个</span>
        </div>

        <van-cell title="封面">
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

        <van-cell title="标签">
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
              <van-button size="small" type="default" v-if="noteForm.tags.length < 5">添加</van-button>
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
          @click="saveNote"
        >
          {{ isEdit ? '更新' : '发布' }}
        </van-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.write-note-page {
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

.content-media-section {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: #fafafa;
  border-top: 1px solid #f0f0f0;
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
  background: #fff;
  cursor: pointer;
  transition: all 0.2s;
}
.upload-label:active {
  background: #f5f5f5;
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
