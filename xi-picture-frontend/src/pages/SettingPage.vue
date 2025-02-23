<template>
  <div id="profilePage">
    <!-- 动态星空背景 -->
    <div class="star-background">
      <div v-for="(star, index) in stars" :key="index" class="star" :style="star.style"></div>
    </div>
    <a-row :gutter="[16, 16]">
      <!-- 个人信息展示 -->
      <a-col :span="24">
        <a-card title="个人信息" :bordered="false" class="neon-title">
          <!-- 磨砂玻璃效果卡片 -->
          <div class="glass-card">
            <!-- 3D头像区 -->
            <div class="avatar-container">
              <div class="fluid-border"></div>
              <a-avatar
                :size="160"
                :src="userData?.userAvatar"
                class="hologram-avatar"
                @mouseenter="startParticles"
                @mouseleave="stopParticles"
              />
            </div>

            <a-descriptions :column="1">
              <a-descriptions-item label="账号">{{ userData?.userAccount }}</a-descriptions-item>
              <a-descriptions-item label="昵称">{{ userData?.userName }}</a-descriptions-item>
              <a-descriptions-item label="简介">{{
                userData?.userProfile || '-'
              }}</a-descriptions-item>
            </a-descriptions>

            <!-- 发光进度条 -->
            <div class="achievement-bar">
              <div class="progress-glow" :style="{ width: `${achievementProgress}%` }"></div>
            </div>
          </div>

          <template #extra>
            <a-button type="primary" @click="showEditModal">
              <template #icon><EditOutlined /></template>
              编辑资料
            </a-button>
          </template>
        </a-card>
      </a-col>
    </a-row>

    <!-- 编辑模态框 -->
    <a-modal
      v-model:visible="modalVisible"
      title="编辑资料"
      @ok="handleEditSubmit"
      :confirm-loading="submitting"
    >
      <a-form :model="editFormState" :label-col="{ span: 6 }" :wrapper-col="{ span: 18 }">
        <a-form-item
          label="用户昵称"
          name="userName"
          :rules="[{ required: false, message: '请输入昵称' }]"
        >
          <a-input v-model:value="editFormState.userName" />
        </a-form-item>

        <a-form-item
          label="头像地址"
          name="userAvatar"
          :rules="[{ required: false, message: '请输入头像地址' }]"
        >
          <!-- todo 重新创建一个上传图片的组件 -->
          <!-- 先简单一点，直接通过填写 url，因为修改头像的操作不会很频繁，因此先简单一点，后续可以创建一个上传用户头像的组件-->
          <a-input v-model:value="editFormState.userAvatar" />
        </a-form-item>

        <a-form-item label="个人简介" name="userProfile">
          <a-textarea v-model:value="editFormState.userProfile" :rows="4" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { reactive, computed, ref, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'
import { EditOutlined } from '@ant-design/icons-vue'
import { getLoginUserUsingGet, updateUserUsingPost } from '@/api/userController.ts'

const router = useRouter() // 实现页面（路由）的跳转
const route = useRoute() // 与useRouter不一样，获取信息（用于访问当前的路由信息）

// 用户数据
const userData = ref<API.UserVO>()
// 编辑表单状态
// 语法：Partial<T>
// 作用：将类型 T 的所有属性变为可选属性
// 使用场景：当我们想创建一个对象，但不需要立即提供所有属性时非常有用
// Partial<UserVO> 创建了一个新类型，这个类型拥有 UserVO 的所有属性，但都是可选的
// ref<Partial<UserVO>>({}) 创建了一个 Vue 的响应式引用，初始值为空对象 {}
// 这样我们就可以逐步添加属性到 editFormState 中，而不需要一开始就提供所有属性
const editFormState = ref<Partial<UserVO>>({})
// 模态框显示状态
const modalVisible = ref(false)
// 提交状态
const submitting = ref(false)

// 获取用户数据
const fetchUserData = async () => {
  try {
    const res = await getLoginUserUsingGet()
    if (res.data?.code === 0 && res.data.data) {
      userData.value = res.data.data
    }
  } catch (error) {
    message.error('获取用户信息失败')
  }
}

// 显示编辑模态框
const showEditModal = () => {
  editFormState.value = { ...userData.value }
  modalVisible.value = true
}

// 提交编辑表单
const handleEditSubmit = async () => {
  submitting.value = true
  try {
    const res = await updateUserUsingPost({
      ...editFormState.value,
      id: userData.value?.id,
    })

    if (res.data?.code === 0) {
      message.success('更新成功')
      modalVisible.value = false
      await fetchUserData()
    }
    // 更新 GlobalHeader 的头像数据
    refreshPage()
  } catch (error) {
    message.error('更新失败')
  } finally {
    submitting.value = false
  }
}

// 初始化获取数据
onMounted(() => {
  fetchUserData()
})

// 最直接的方式，会强制刷新整个页面
const refreshPage = () => {
  window.location.reload()
}

// 新增特效相关代码

// 星空背景
const stars = ref([])
const initStars = () => {
  for (let i = 0; i < 100; i++) {
    stars.value.push({
      style: {
        left: `${Math.random() * 100}%`,
        top: `${Math.random() * 100}%`,
        animationDelay: `${Math.random() * 3}s`,
      },
    })
  }
}

// 粒子动画
const startParticles = () => {
  // 使用WebGL实现粒子效果
}

// 主题切换
const themeMode = ref('dark')

// 初始化
onMounted(() => {
  initStars()
})
</script>

<style scoped>
#profilePage {
  padding: 24px;
}

.profile-avatar {
  transition: transform 0.3s;
}

.profile-avatar:hover {
  transform: scale(1.1);
}

:deep(.ant-descriptions-item-label) {
  font-weight: 500;
  color: rgba(0, 0, 0, 0.85);
}

/* 新增特效样式 */
#profilePage {
  min-height: 100vh;
  background: radial-gradient(circle at 50% 50%, #1a1a2e 0%, #16213e 100%);
  position: relative;
  overflow: hidden;
}

/* 星空动画 */
.star {
  position: absolute;
  width: 2px;
  height: 2px;
  background: white;
  animation: twinkle 1.5s infinite;
}

@keyframes twinkle {
  0% {
    opacity: 0.2;
  }
  50% {
    opacity: 1;
  }
  100% {
    opacity: 0.2;
  }
}

/* 磨砂玻璃效果 */
.glass-card {
  backdrop-filter: blur(12px);
  background: rgba(255, 255, 255, 0.08);
  border-radius: 24px;
  padding: 40px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
}

/* 全息头像 */
.hologram-avatar {
  border: 2px solid;
  border-image: linear-gradient(45deg, #ff6b6b, #4ecdc4) 1;
  animation: hologram 3s infinite;
  transition: transform 0.3s;
}

@keyframes hologram {
  0% {
    filter: drop-shadow(0 0 5px rgba(78, 205, 196, 0.5));
  }
  50% {
    filter: drop-shadow(0 0 15px rgba(255, 107, 107, 0.8));
  }
  100% {
    filter: drop-shadow(0 0 5px rgba(78, 205, 196, 0.5));
  }
}

/* 霓虹标题 */
.neon-title {
  color: #fff;
  text-shadow: 0 0 10px #4ecdc4, 0 0 20px #4ecdc4, 0 0 30px #4ecdc4;
  animation: neonPulse 2s infinite;
}

/* 进度条辉光 */
.achievement-bar {
  height: 8px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 4px;
  position: relative;
}

.progress-glow {
  height: 100%;
  background: linear-gradient(90deg, #ff6b6b, #4ecdc4);
  border-radius: 4px;
  box-shadow: 0 0 15px rgba(78, 205, 196, 0.5);
  animation: glow 2s infinite;
}
</style>