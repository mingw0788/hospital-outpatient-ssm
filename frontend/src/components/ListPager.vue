<script setup>defineProps({ page: Number, length: Number, size: { type: Number, default: 20 } }); defineEmits(['change'])</script>
<template><div class="pager"><span>第 {{ page }} 页 · 本页 {{ length }} 条</span><div><el-button :disabled="page <= 1" @click="$emit('change', -1)">上一页</el-button><el-button :disabled="length < size" @click="$emit('change', 1)">下一页</el-button></div></div></template>
