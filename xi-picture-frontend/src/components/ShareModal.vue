<template>
  <div>
    <a-modal v-model:visible="visible" :title="title" :footer="false" @cancel="closeModal">
      <h4>复制分享链接</h4>
      <a-typography-link copyable>
        {{ link }}
      </a-typography-link>
      <div style="margin-bottom: 16px" />
      <h4>手机扫码查看</h4>
      <a-qrcode :value="link" />
    </a-modal>
  </div>
</template>

<script lang="ts" setup>
import { ref } from 'vue'

// todo 8.4 后端记录分享次数，后端可以记录点击分享按钮的次数、以及分享链接的点击次数，以便进行数据分析和优化。
// todo 8.4 生成自定义邀请码，可以为每个用户生成自己的邀请码，还可以支持自助修改。
// todo 8.4 微信卡片分享功能，接入微信 JS-SDK，实现微信卡片分享功能。通过该功能，用户分享时可以展示自定义的标题、图片等内容，而非简单的链接，提高点击率。

interface Props {
  title: string
  link: string
}

const props = withDefaults(defineProps<Props>(), {
  title: '分享图片',
  link: 'https://github.com',
})

// 是否可见
const visible = ref(false)

// 打开弹窗
const openModal = () => {
  visible.value = true
}

// 关闭弹窗
const closeModal = () => {
  visible.value = false
}

// 暴露函数给父组件
defineExpose({
  openModal,
})
</script>
