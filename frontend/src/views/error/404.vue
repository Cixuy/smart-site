<template>
  <div class="error-page h-screen flex flex-col items-center justify-center bg-[#f0f2f5]">
    <div class="text-center">
      <h1 class="text-[120px] font-bold text-[#1890ff] leading-none select-none">404</h1>
      <p class="text-xl text-gray-500 mt-4 mb-8">抱歉，您访问的页面不存在</p>
      <el-button type="primary" size="large" @click="$router.push('/home')">
        <el-icon><HomeFilled /></el-icon> 返回首页
      </el-button>
    </div>
  </div>
</template>
