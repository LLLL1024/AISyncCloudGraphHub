<template>
  <div class="about">
    <!-- 顶部导航 -->
    <div class="nav-bar">
      <div class="brand">
        <span>兮颜云图汇</span>
      </div>
      <div class="user-info">
        <span class="username">兮颜</span>
      </div>
    </div>

    <!-- 内容区 -->
    <div class="content-container">
      <!-- 关于内容 -->
      <div class="about-content">
        <a-card title="关于我们" class="about-card">
          <h2 class="section-title">平台简介</h2>
          <p class="desc-text">
            专业的云端图片管理平台，提供海量图片存储、智能分类和团队协作功能...
          </p>
          <p class="desc-text">这个平台的应用场景非常广泛，核心功能可分为 4 大类：</p>
          <p class="desc-text">
            1）所有用户都可以在平台公开上传和检索图片素材，快速找到需要的图片。可用作表情包网站、设计素材网站、壁纸网站等；
            <br />
            2）管理员可以上传、审核和管理图片，并对系统内的图片进行分析；
            <br />
            3）对于个人用户，可将图片上传至私有空间进行批量管理、检索、编辑和分析，用作个人网盘、个人相册、作品集等；
            <br />
            4）对于企业，可开通团队空间并邀请成员，共享图片并实时协同编辑图片，提高团队协作效率。可用于提供商业服务，如企业活动相册、企业内部素材库等。
          </p>

          <div class="func-tags">
            <a-tag color="blue">模板库</a-tag>
            <a-tag color="blue">以图搜图</a-tag>
            <a-tag color="blue">颜色搜索</a-tag>
            <a-tag color="blue">AI 扩图</a-tag>
            <a-tag color="blue">个人空间</a-tag>
            <a-tag color="blue">团队协作</a-tag>
          </div>

          <h2 class="section-title">使用指南</h2>
          <a-steps :current="1">
            <a-step title="上传图片" description="支持本体上传和拖拽操作以及 URL 上传" />
            <a-step title="分类管理" description="自定义标签和分类模板" />
            <a-step title="个人空间" description="创建属于自己的图片空间" />
            <a-step title="团队协作" description="创建空间并邀请成员" />
          </a-steps>
        </a-card>
      </div>
    </div>
  </div>
</template>

<style scoped>
.about {
  background: #f0f2f5;
  min-height: 100vh;
}

.nav-bar {
  background: #fff;
  padding: 16px 24px;
  display: flex;
  justify-content: space-between;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.brand {
  display: flex;
  align-items: center;
  color: #1890ff;
  font-size: 18px;
}

.cloud-icon {
  width: 28px;
  margin-right: 8px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.content-container {
  display: flex;
  margin-top: 16px;
}

.side-nav {
  width: 240px;
  background: #fff;
  padding: 16px;
  margin-left: 24px;
  border-radius: 4px;
}

.nav-item {
  padding: 12px;
  cursor: pointer;
  border-radius: 4px;
}

.nav-item.active {
  background: #e6f7ff;
  color: #1890ff;
}

.team-space {
  margin-top: 24px;
  padding: 12px;
  background: #fafafa;
  border-radius: 4px;
}

.about-content {
  flex: 1;
  margin: 0 24px;
}

.about-card {
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.section-title {
  color: #1890ff;
  border-left: 4px solid #1890ff;
  padding-left: 12px;
  margin: 24px 0;
}

.func-tags {
  margin: 16px 0;
  display: flex;
  gap: 12px;
}

@media (min-width: 1024px) {
  .about-content {
    max-width: 800px;
    margin: 0 auto;
  }
}
</style>