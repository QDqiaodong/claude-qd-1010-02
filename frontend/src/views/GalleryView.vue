<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const emit = defineEmits(['changed'])

const rows = ref([])
const dialog = ref(false)
const saving = ref(false)
const STATUS = ['开放', '维护', '关闭']

const form = reactive({ id: null, galleryCode: '', name: '', capacity: 1, status: '开放' })

async function load() { rows.value = await api.listGalleries() }
function openCreate() {
  Object.assign(form, { id: null, galleryCode: '', name: '', capacity: 1, status: '开放' })
  dialog.value = true
}
async function submit() {
  saving.value = true
  try {
    if (form.id) { await api.updateGallery(form.id, { ...form }); ElMessage.success('已更新') }
    else { await api.createGallery({ ...form }); ElMessage.success('展厅已创建') }
    dialog.value = false
    await load(); emit('changed')
  } catch (e) { ElMessage.error(e.message) }
  finally { saving.value = false }
}
async function transfer(row, target) {
  try { await api.transferGallery(row.id, target); ElMessage.success('状态已更新'); await load(); emit('changed') }
  catch (e) { ElMessage.error(e.message) }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="v-head">
      <h3>展厅管理</h3>
      <el-button type="primary" size="small" @click="openCreate">新建展厅</el-button>
    </div>

    <el-table :data="rows" size="small" height="270">
      <el-table-column prop="galleryCode" label="编号" width="92" />
      <el-table-column prop="name" label="名称" min-width="110" />
      <el-table-column prop="capacity" label="容量" width="70" />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-select :model-value="row.status" size="small" @change="(v) => transfer(row, v)">
            <el-option v-for="s in STATUS" :key="s" :label="s" :value="s" />
          </el-select>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" title="新建展厅" width="420px">
      <el-form :model="form" label-width="72px">
        <el-form-item label="编号"><el-input v-model="form.galleryCode" placeholder="如 GL-09" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="容量"><el-input v-model="form.capacity" type="number" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status"><el-option v-for="s in STATUS" :key="s" :label="s" :value="s" /></el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="dialog = false">取消</el-button>
        <el-button size="small" type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.v-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.v-head h3 { margin: 0; font-size: 15px; color: var(--primary-deep); }
</style>
