<!-- 全局通用布局：基础布局结构 -->
<template>
  <div id="basicLayout">
    <a-layout style="min-height: 100vh">
      <!-- 全局顶部栏-->
      <a-layout-header class="header">
        <GlobalHeader />
      </a-layout-header>
      <!-- 全局侧边栏-->
      <a-layout>
        <GlobalSider class="sider" />
        <a-layout-content class="content">
          <!-- 动态路由替换-->
          <router-view />
        </a-layout-content>
      </a-layout>
      <!-- 全局底部栏-->
      <a-layout-footer class="footer">
        <a href="https://github.com/LLLL1024/AISyncCloudGraphHub" target="_blank">
          云图汇 by 程序员兮颜
        </a>
      </a-layout-footer>
    </a-layout>
  </div>
</template>

<script setup lang="ts">
import GlobalHeader from '@/components/GlobalHeader.vue'
import GlobalSider from '@/components/GlobalSider.vue'
</script>

<style scoped>
/**
#:id选择器，.:类选择器，另外一个是元素选择器
#basicLayout .footer:这个选择器表示选择ID为basicLayout的元素内的所有类名为footer的元素。这是一种后代选择器，意味着.footer元素必须是#basicLayout元素的子元素、孙元素等，但不一定是直接子元素。
**/

#basicLayout .header {
  padding-inline: 20px;
  margin-bottom: 1px;
  color: unset;
  background: white;
}

#basicLayout .sider {
  background: #fff;
  border-right: 0.5px solid #eee;
  padding-top: 20px;
}

#basicLayout :deep(.ant-menu-root) {
  border-bottom: none !important;
  border-inline-end: none !important;
}

#basicLayout .content {
  background: linear-gradient(to right, #fefefe, #fff);
  margin-bottom: 28px;
  padding: 20px;
}

#basicLayout .footer {
  background: #efefef;
  padding: 16px;
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  text-align: center;
}
</style>

