<script setup>
import { label, statusType } from '../utils'
defineProps({ value: [String, Boolean, Number] })
</script>
<template><el-tag v-if="typeof value === 'boolean' || typeof value === 'number'" :type="value ? 'success' : 'info'" effect="light">{{ value ? '启用' : '停用' }}</el-tag><el-tag v-else :type="statusType(value)" effect="light">{{ label(value) }}</el-tag></template>
