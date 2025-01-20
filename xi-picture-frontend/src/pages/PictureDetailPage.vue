<template>
  <div id="pictureDetailPage">
    <a-row :gutter="[16, 16]">
      <!-- 图片预览 -->
      <a-col :sm="24" :md="16" :xl="18">
        <a-card title="图片预览">
          <!-- todo 可以在这里将图片改成预览图，只需该这一次即可，这样还可以优化资源，那么下载还是原图 -->
          <a-image :src="picture.url" style="max-height: 600px; object-fit: contain" />
        </a-card>
      </a-col>
      <!-- 图片信息区域（栅格） -->
      <a-col :sm="24" :md="8" :xl="6">
        <a-card title="图片信息">
          <a-descriptions :column="1">
            <a-descriptions-item label="作者">
              <a-space>
                <a-avatar :size="24" :src="picture.user?.userAvatar" />
                <div>{{ picture.user?.userName }}</div>
              </a-space>
            </a-descriptions-item>
            <a-descriptions-item label="名称">
              {{ picture.name ?? '未命名' }}
            </a-descriptions-item>
            <a-descriptions-item label="简介">
              {{ picture.introduction ?? '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="分类">
              {{ picture.category ?? '默认' }}
            </a-descriptions-item>
            <a-descriptions-item label="标签">
              <a-tag v-for="tag in picture.tags" :key="tag">
                {{ tag }}
              </a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="格式">
              {{ picture.picFormat ?? '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="宽度">
              {{ picture.picWidth ?? '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="高度">
              {{ picture.picHeight ?? '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="宽高比">
              {{ picture.picScale ?? '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="大小">
              {{ formatSize(picture.picSize) }}
            </a-descriptions-item>
            <a-descriptions-item label="主色调">
              <a-space>
                {{ picture.picColor ?? '-' }}
                <div
                  v-if="picture.picColor"
                  :style="{
                    width: '16px',
                    height: '16px',
                    backgroundColor: toHexColor(picture.picColor),
                  }"
                />
              </a-space>
            </a-descriptions-item>
          </a-descriptions>
          <!-- 图片操作 -->
          <a-space wrap>
            <!-- 没必要有审核通过，因为管理员编辑或上传会自动审核，其他用户上传图片不会在主页显示，要管理员在图片管理页面审核即可，因此没有必要有审核通过 -->
            <!-- <a-button
              v-if="picture.reviewStatus !== PIC_REVIEW_STATUS_ENUM.PASS && canReview"
              :icon="h(CheckOutlined)"
              type="primary"
              @click="handleReview(picture, PIC_REVIEW_STATUS_ENUM.PASS)"
            >
              审核通过
            </a-button> -->
            <a-button type="primary" @click="doDownload">
              免费下载
              <template #icon>
                <DownloadOutlined />
              </template>
            </a-button>
            <a-button :icon="h(ShareAltOutlined)" type="primary" ghost @click="doShare">
              分享
            </a-button>
            <a-button v-if="canEdit" :icon="h(EditOutlined)" type="default" @click="doEdit">
              编辑
            </a-button>
            <!-- <a-button v-if="canEdit" :icon="h(DeleteOutlined)" danger @click="doDelete">
              删除
            </a-button> -->
            <a-popconfirm
              title="确认要删除这张图片吗?"
              ok-text="确定"
              cancel-text="取消"
              @confirm="doDelete"
              @visibleChange="onVisibleChange"
              :visible="visible"
            >
              <a-button v-if="canEdit" :icon="h(DeleteOutlined)" danger>删除</a-button>
            </a-popconfirm>
            <a-popconfirm
              title="确认要拒接审核这张图片吗?"
              ok-text="确定"
              cancel-text="取消"
              @confirm="handleReview(picture, PIC_REVIEW_STATUS_ENUM.REJECT)"
              @visibleChange="onVisibleChange"
              :visible="visible"
            >
              <a-button
                v-if="picture.reviewStatus !== PIC_REVIEW_STATUS_ENUM.REJECT && canReview"
                :icon="h(CloseOutlined)"
                danger
              >
                拒绝
              </a-button>
            </a-popconfirm>
          </a-space>
        </a-card>
      </a-col>
    </a-row>
    <ShareModal ref="shareModalRef" :link="shareLink" />
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import {
  deletePictureUsingPost,
  getPictureVoByIdUsingGet,
  listPictureByPageUsingPost,
  doPictureReviewUsingPost,
} from '@/api/pictureController.ts'
import {
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  CheckOutlined,
  CloseOutlined,
  ShareAltOutlined,
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import {
  PIC_REVIEW_STATUS_ENUM,
  PIC_REVIEW_STATUS_MAP,
  PIC_REVIEW_STATUS_OPTIONS,
} from '../constants/picture.ts'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import { useRouter } from 'vue-router'
import { downloadImage, formatSize, toHexColor } from '@/utils'
import ShareModal from '@/components/ShareModal.vue'

interface Props {
  id: string | number
}

//在页面中可以使用 props 获取到动态的参数
const props = defineProps<Props>()
const picture = ref<API.PictureVO>({})
const loginUserStore = useLoginUserStore()

// 是否具有编辑权限，loginUserStore 有可能会变化，所以要用计算属性 computed
const canEdit = computed(() => {
  const loginUser = loginUserStore.loginUser
  // 未登录不可编辑
  if (!loginUser.id) {
    return false
  }
  // 仅本人或管理员可编辑
  const user = picture.value.user || {}
  return loginUser.id === user.id || loginUser.userRole === 'admin'
})

// 是否具有审核权限，loginUserStore 有可能会变化，所以要用计算属性 computed
const canReview = computed(() => {
  const loginUser = loginUserStore.loginUser
  // 未登录不可审核
  if (!loginUser.id) {
    return false
  }
  // 仅管理员可审核
  return loginUser.userRole === 'admin'
})

// 获取图片详情
const fetchPictureDetail = async () => {
  try {
    const res = await getPictureVoByIdUsingGet({
      id: props.id,
    })
    if (res.data.code === 0 && res.data.data) {
      picture.value = res.data.data
    } else {
      message.error('获取图片详情失败，' + res.data.message)
    }
  } catch (e: any) {
    message.error('获取图片详情失败：' + e.message)
  }
}

onMounted(() => {
  fetchPictureDetail()
})

const router = useRouter()

// 编辑
// 可以直接复用创建页面，在页面后增加 URL 查询参数 ?id=xx 表示要修改对应 id 的图片
const doEdit = () => {
  // router.push('/add_picture?id=' + picture.value.id)
  router.push({
    path: '/add_picture',
    query: {
      id: picture.value.id,
      spaceId: picture.value.spaceId,
    },
  })
}

// 显示或隐藏该组件
const visible = ref(false)
// 发送变化的时候，判断是否要隐藏
function onVisibleChange(v: boolean) {
  if (!v) {
    // 希望隐藏
    visible.value = false
  } else {
    // 希望显示
    visible.value = true
  }
}

// 删除数据
const doDelete = async () => {
  const id = picture.value.id
  if (!id) {
    return
  }
  const res = await deletePictureUsingPost({ id })
  if (res.data.code === 0) {
    message.success('删除成功')
    router.push('/')
  } else {
    message.error('删除失败')
  }
}

// 下载图片
const doDownload = () => {
  downloadImage(picture.value.originalUrl)
}

// 审核图片
const handleReview = async (record: API.Picture, reviewStatus: number) => {
  const reviewMessage =
    reviewStatus === PIC_REVIEW_STATUS_ENUM.PASS ? '管理员操作通过' : '管理员操作拒绝'
  const res = await doPictureReviewUsingPost({
    id: record.id,
    reviewStatus,
    reviewMessage,
  })
  if (res.data.code === 0) {
    message.success('审核操作成功')
    router.push('/')
  } else {
    message.error('审核操作失败，' + res.data.message)
  }
}

// ----- 分享操作 ----
const shareModalRef = ref()
// 分享链接
const shareLink = ref<string>()
// 分享
const doShare = () => {
  shareLink.value = `${window.location.protocol}//${window.location.host}/picture/${picture.value.id}`
  if (shareModalRef.value) {
    shareModalRef.value.openModal()
  }
}
</script>

<style scoped>
#pictureDetailPage {
  margin-bottom: 16px;
}
</style>