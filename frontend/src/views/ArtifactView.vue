<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const emit = defineEmits(['changed'])

const rows = ref([])
const dialog = ref(false)
const saving = ref(false)
const STATUS = ['在展', '库房', '借展']
const TAG_TYPE = { 草稿: 'info', 待接收: 'warning', 已接收: 'primary', 已拒收: 'danger', 已收口: 'success', 已终止: 'info' }

const form = reactive({
  id: null, artifactNo: '', name: '', dynasty: '', acquireYear: null, status: '库房', attrs: []
})

// 从藏品端定位当前交接：该藏品的全部交接单（新的在前）
const handoverDialog = ref(false)
const handoverRows = ref([])
const handoverTitle = ref('')
const fmt = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '—')

async function openHandovers(row) {
  try {
    handoverRows.value = await api.listHandovers({ artifactId: row.id })
    handoverTitle.value = `${row.artifactNo} · ${row.name}`
    handoverDialog.value = true
  } catch (e) { ElMessage.error(e.message) }
}

async function load() {
  rows.value = await api.listArtifacts()
}
function openCreate() {
  Object.assign(form, { id: null, artifactNo: '', name: '', dynasty: '', acquireYear: null, status: '库房', attrs: [] })
  dialog.value = true
}
function addAttr() { form.attrs.push({ era: '', material: '' }) }
function removeAttr(i) { form.attrs.splice(i, 1) }

async function submit() {
  saving.value = true
  try {
    const payload = { ...form, attrs: form.attrs.filter(a => a.era || a.material) }
    if (form.id) {
      await api.updateArtifact(form.id, payload)
      ElMessage.success('已更新')
    } else {
      await api.createArtifact(payload)
      ElMessage.success('藏品已登记')
    }
    dialog.value = false
    await load()
    emit('changed')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

async function transfer(row, target) {
  try {
    await api.transferArtifact(row.id, target)
    ElMessage.success('状态已更新')
    await load()
    emit('changed')
  } catch (e) { ElMessage.error(e.message) }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="v-head">
      <h3>藏品管理</h3>
      <el-button type="primary" size="small" @click="openCreate">登记藏品</el-button>
    </div>

    <el-table :data="rows" size="small" height="270">
      <el-table-column prop="artifactNo" label="编号" width="92" />
      <el-table-column prop="name" label="名称" min-width="120" show-overflow-tooltip />
      <el-table-column prop="dynasty" label="年代" width="70" />
      <el-table-column prop="acquireYear" label="入藏年" width="70" />
      <el-table-column label="特征" min-width="120">
        <template #default="{ row }">
          <span v-for="(a, i) in (row.attrs || [])" :key="i" class="tag">{{ a.era }}/{{ a.material }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="130">
        <template #default="{ row }">
          <!-- 出展中由交接流程驱动，不允许手工改 -->
          <el-tag v-if="row.status === '出展中'" type="warning" size="small">出展中</el-tag>
          <el-select v-else :model-value="row.status" size="small" @change="(v) => transfer(row, v)">
            <el-option v-for="s in STATUS" :key="s" :label="s" :value="s" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="交接" width="60">
        <template #default="{ row }">
          <el-button size="small" text type="primary" @click="openHandovers(row)">交接</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" title="登记藏品" width="460px">
      <el-form :model="form" label-width="72px">
        <el-form-item label="编号"><el-input v-model="form.artifactNo" placeholder="如 WB-0011" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="年代"><el-input v-model="form.dynasty" placeholder="如 唐" /></el-form-item>
        <el-form-item label="入藏年份"><el-input v-model="form.acquireYear" type="number" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status"><el-option v-for="s in STATUS" :key="s" :label="s" :value="s" /></el-select>
        </el-form-item>
        <el-form-item label="特征">
          <div class="attrs">
            <div v-for="(a, i) in form.attrs" :key="i" class="attr-row">
              <el-input v-model="a.era" placeholder="年代细分" size="small" />
              <el-input v-model="a.material" placeholder="材质" size="small" />
              <el-button size="small" text type="danger" @click="removeAttr(i)">删</el-button>
            </div>
            <el-button size="small" @click="addAttr">+ 添加特征</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="dialog = false">取消</el-button>
        <el-button size="small" type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 该藏品的交接记录（与展览端看到的是同一份当前状态） -->
    <el-dialog v-model="handoverDialog" :title="`交接记录 · ${handoverTitle}`" width="520px">
      <el-table :data="handoverRows" size="small" max-height="300" empty-text="暂无交接记录">
        <el-table-column prop="handoverNo" label="单号" width="100" />
        <el-table-column prop="exTitle" label="展览" min-width="110" show-overflow-tooltip />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="TAG_TYPE[row.status] || 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="交接时间" width="140">
          <template #default="{ row }">{{ fmt(row.handoverTime) }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<style scoped>
.v-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.v-head h3 { margin: 0; font-size: 15px; color: var(--primary-deep); }
.tag { display: inline-block; margin: 1px 3px; padding: 1px 6px; background: var(--primary-wash); border-radius: 8px; font-size: 11px; }
.attrs { width: 100%; }
.attr-row { display: flex; gap: 6px; margin-bottom: 6px; }
</style>
