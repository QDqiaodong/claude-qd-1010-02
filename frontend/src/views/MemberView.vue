<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const emit = defineEmits(['changed'])

const LEVELS = ['普通', '银卡', '金卡']
const ADJ_STATUS = ['待复核', '已通过', '已驳回', '已撤回']
const TAG_TYPE = { 待复核: 'warning', 已通过: 'success', 已驳回: 'danger', 已撤回: 'info' }

const tab = ref('members')
const members = ref([])
const adjustments = ref([])
const filterStatus = ref('')
const filterMemberId = ref(null)

const memberDialog = ref(false)
const applyDialog = ref(false)
const reviewDialog = ref(false)
const memberDetailDialog = ref(false)
const adjDetailDialog = ref(false)
const saving = ref(false)

const form = reactive({ id: null, memberNo: '', name: '', phone: '', level: '普通', points: 0, joinedAt: '' })
const applyForm = reactive({ id: null, memberId: null, delta: 100, reason: '', voucher: '', applicant: '' })
const reviewForm = reactive({ id: null, applyNo: '', delta: 0, memberName: '', reviewer: '', comment: '' })
const memberDetail = ref({})
const memberHistory = ref([])
const adjDetail = ref({})

const fmt = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '—')

async function load() {
  const [ms, pas] = await Promise.all([
    api.listMembers(),
    api.listPointAdjustments({
      ...(filterStatus.value ? { status: filterStatus.value } : {}),
      ...(filterMemberId.value ? { memberId: filterMemberId.value } : {})
    })
  ])
  members.value = ms
  adjustments.value = pas
}

// ---------------- 会员 ----------------

function openCreate() {
  Object.assign(form, { id: null, memberNo: '', name: '', phone: '', level: '普通', points: 0, joinedAt: '' })
  memberDialog.value = true
}

async function submit() {
  saving.value = true
  try {
    if (form.id) { await api.updateMember(form.id, { ...form }); ElMessage.success('已更新') }
    else { await api.createMember({ ...form }); ElMessage.success('会员已录入') }
    memberDialog.value = false
    await load(); emit('changed')
  } catch (e) { ElMessage.error(e.message) }
  finally { saving.value = false }
}

/** 会员详情：基本信息 + 积分调整历史（处理人 / 处理时间 / 前后积分） */
async function openMemberDetail(row) {
  memberDetail.value = row
  memberHistory.value = []
  memberDetailDialog.value = true
  try {
    memberHistory.value = await api.listPointAdjustments({ memberId: row.id })
  } catch (e) { ElMessage.error(e.message) }
}

// ---------------- 调整申请 ----------------

function openApply(row) {
  Object.assign(applyForm, {
    id: null, memberId: row ? row.id : null, delta: 100, reason: '', voucher: '', applicant: ''
  })
  applyDialog.value = true
}

function openFix(row) {
  Object.assign(applyForm, {
    id: row.id, memberId: row.memberId, delta: row.delta,
    reason: row.reason, voucher: row.voucher, applicant: row.applicant
  })
  applyDialog.value = true
}

async function submitApply() {
  saving.value = true
  try {
    const payload = {
      memberId: applyForm.memberId, delta: Number(applyForm.delta),
      reason: applyForm.reason, voucher: applyForm.voucher, applicant: applyForm.applicant
    }
    if (applyForm.id) {
      await api.updatePointAdjustment(applyForm.id, payload)
      ElMessage.success('申请已修正，待复核')
    } else {
      await api.createPointAdjustment(payload)
      ElMessage.success('申请已提交，待复核（积分暂未变动）')
    }
    applyDialog.value = false
    await load(); emit('changed')
  } catch (e) { ElMessage.error(e.message) }
  finally { saving.value = false }
}

async function withdraw(row) {
  try {
    await ElMessageBox.confirm(
      `确认撤回申请 ${row.applyNo}（${row.memberName} ${row.delta > 0 ? '+' : ''}${row.delta} 分）？撤回后不可再处理`,
      '撤回申请', { type: 'warning', confirmButtonText: '撤回', cancelButtonText: '取消' }
    )
  } catch { return }
  try {
    await api.withdrawPointAdjustment(row.id, row.applicant)
    ElMessage.success('申请已撤回')
    await load(); emit('changed')
  } catch (e) { ElMessage.error(e.message) }
}

function openReview(row) {
  Object.assign(reviewForm, {
    id: row.id, applyNo: row.applyNo, delta: row.delta, memberName: row.memberName,
    reviewer: '', comment: ''
  })
  reviewDialog.value = true
}

async function submitReview(pass) {
  if (!reviewForm.reviewer.trim()) { ElMessage.error('请填写复核人'); return }
  if (!pass && !reviewForm.comment.trim()) { ElMessage.error('驳回必须填写复核意见'); return }
  saving.value = true
  try {
    if (pass) {
      await api.approvePointAdjustment(reviewForm.id, reviewForm.reviewer, reviewForm.comment)
      ElMessage.success('复核通过，积分已更新')
    } else {
      await api.rejectPointAdjustment(reviewForm.id, reviewForm.reviewer, reviewForm.comment)
      ElMessage.success('已驳回')
    }
    reviewDialog.value = false
    await load(); emit('changed')
  } catch (e) { ElMessage.error(e.message) }
  finally { saving.value = false }
}

function openAdjDetail(row) {
  adjDetail.value = row
  adjDetailDialog.value = true
}

onMounted(load)
</script>

<template>
  <div>
    <el-tabs v-model="tab" class="m-tabs">
      <!-- ================= 会员列表 ================= -->
      <el-tab-pane label="会员列表" name="members">
        <div class="v-head">
          <span class="v-tip">等级按累计积分自动判定</span>
          <el-button type="primary" size="small" @click="openCreate">录入会员</el-button>
        </div>
        <el-table :data="members" size="small" height="252">
          <el-table-column prop="memberNo" label="编号" width="86" />
          <el-table-column prop="name" label="姓名" width="70" />
          <el-table-column prop="level" label="等级" width="60" />
          <el-table-column prop="points" label="积分" width="66" />
          <el-table-column label="操作" width="118" fixed="right">
            <template #default="{ row }">
              <el-button size="small" text type="primary" @click="openApply(row)">调分</el-button>
              <el-button size="small" text @click="openMemberDetail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ================= 积分调整申请 ================= -->
      <el-tab-pane label="积分调整申请" name="adjustments">
        <div class="v-head">
          <div class="v-filters">
            <el-select v-model="filterStatus" size="small" style="width: 96px" placeholder="全部状态" clearable @change="load">
              <el-option v-for="s in ADJ_STATUS" :key="s" :label="s" :value="s" />
            </el-select>
            <el-select v-model="filterMemberId" size="small" style="width: 118px" placeholder="全部会员" clearable filterable @change="load">
              <el-option v-for="m in members" :key="m.id" :label="`${m.memberNo} ${m.name}`" :value="m.id" />
            </el-select>
          </div>
          <el-button type="primary" size="small" @click="openApply(null)">新建申请</el-button>
        </div>
        <el-table :data="adjustments" size="small" height="252">
          <el-table-column prop="applyNo" label="单号" width="102" />
          <el-table-column prop="memberName" label="会员" width="62" />
          <el-table-column label="调整" width="62">
            <template #default="{ row }">
              <span :style="{ color: row.delta > 0 ? '#67a15f' : '#c05b4d' }">{{ row.delta > 0 ? '+' : '' }}{{ row.delta }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="reason" label="业务原因" min-width="90" show-overflow-tooltip />
          <el-table-column label="状态" width="66">
            <template #default="{ row }">
              <el-tag size="small" :type="TAG_TYPE[row.status] || 'info'">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="128" fixed="right">
            <template #default="{ row }">
              <template v-if="row.status === '待复核'">
                <el-button size="small" text type="primary" @click="openFix(row)">修正</el-button>
                <el-button size="small" text type="success" @click="openReview(row)">复核</el-button>
                <el-button size="small" text type="warning" @click="withdraw(row)">撤回</el-button>
              </template>
              <el-button size="small" text @click="openAdjDetail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 录入会员 -->
    <el-dialog v-model="memberDialog" title="录入会员" width="420px">
      <el-form :model="form" label-width="72px">
        <el-form-item label="编号"><el-input v-model="form.memberNo" placeholder="如 MB-0009" /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="手机"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="等级">
          <el-select v-model="form.level"><el-option v-for="l in LEVELS" :key="l" :label="l" :value="l" /></el-select>
        </el-form-item>
        <el-form-item label="入会日"><el-date-picker v-model="form.joinedAt" type="date" value-format="YYYY-MM-DD" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="memberDialog = false">取消</el-button>
        <el-button size="small" type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 新建 / 修正调整申请 -->
    <el-dialog v-model="applyDialog" :title="applyForm.id ? '修正调整申请' : '新建积分调整申请'" width="440px">
      <el-alert v-if="!applyForm.id" type="info" :closable="false" show-icon
        title="申请提交后为「待复核」，复核通过前不会变动会员积分" style="margin-bottom: 10px" />
      <el-alert v-else type="info" :closable="false" show-icon
        title="修正后申请仍为「待复核」，复核人可再次复核" style="margin-bottom: 10px" />
      <el-form label-width="86px">
        <el-form-item label="会员" required>
          <el-select v-model="applyForm.memberId" filterable :disabled="!!applyForm.id" style="width: 100%">
            <el-option v-for="m in members" :key="m.id" :label="`${m.memberNo} ${m.name}（当前 ${m.points} 分）`" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="调整值" required>
          <el-input-number v-model="applyForm.delta" :step="50" style="width: 180px" />
          <span class="f-tip">正数加分、负数扣分</span>
        </el-form-item>
        <el-form-item label="业务原因" required>
          <el-input v-model="applyForm.reason" type="textarea" :rows="2" placeholder="如：国庆活动签到奖励 / 积分兑换文创" />
        </el-form-item>
        <el-form-item label="关联凭据" required>
          <el-input v-model="applyForm.voucher" placeholder="活动单号 / 兑换单号等" />
        </el-form-item>
        <el-form-item label="申请人" required>
          <el-input v-model="applyForm.applicant" :disabled="!!applyForm.id" placeholder="提交人姓名" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="applyDialog = false">取消</el-button>
        <el-button size="small" type="primary" :loading="saving" @click="submitApply">{{ applyForm.id ? '保存修正' : '提交申请' }}</el-button>
      </template>
    </el-dialog>

    <!-- 复核 -->
    <el-dialog v-model="reviewDialog" :title="`复核申请 ${reviewForm.applyNo}`" width="440px">
      <el-alert type="warning" :closable="false" show-icon
        :title="`会员「${reviewForm.memberName}」调整 ${reviewForm.delta > 0 ? '+' : ''}${reviewForm.delta} 分；通过后立即更新积分并重算等级，扣减为负将被拒绝`"
        style="margin-bottom: 10px" />
      <el-form label-width="86px">
        <el-form-item label="复核人" required><el-input v-model="reviewForm.reviewer" placeholder="复核人姓名" /></el-form-item>
        <el-form-item label="复核意见">
          <el-input v-model="reviewForm.comment" type="textarea" :rows="3" placeholder="通过可空；驳回必填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="reviewDialog = false">取消</el-button>
        <el-button size="small" type="danger" :loading="saving" @click="submitReview(false)">驳回</el-button>
        <el-button size="small" type="success" :loading="saving" @click="submitReview(true)">通过</el-button>
      </template>
    </el-dialog>

    <!-- 会员详情：含积分调整历史 -->
    <el-dialog v-model="memberDetailDialog" :title="`会员 ${memberDetail.memberNo || ''} ${memberDetail.name || ''}`" width="560px">
      <el-descriptions :column="3" size="small" border style="margin-bottom: 10px">
        <el-descriptions-item label="等级">{{ memberDetail.level }}</el-descriptions-item>
        <el-descriptions-item label="当前积分">{{ memberDetail.points }}</el-descriptions-item>
        <el-descriptions-item label="手机">{{ memberDetail.phone || '—' }}</el-descriptions-item>
      </el-descriptions>
      <div class="d-sub">积分调整历史</div>
      <el-table :data="memberHistory" size="small" max-height="240">
        <el-table-column prop="applyNo" label="单号" width="98" />
        <el-table-column label="调整" width="62">
          <template #default="{ row }">
            <span :style="{ color: row.delta > 0 ? '#67a15f' : '#c05b4d' }">{{ row.delta > 0 ? '+' : '' }}{{ row.delta }}</span>
          </template>
        </el-table-column>
        <el-table-column label="前后积分" width="92">
          <template #default="{ row }">
            <span v-if="row.status === '已通过'">{{ row.pointsBefore }} → {{ row.pointsAfter }}</span>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="64">
          <template #default="{ row }">
            <el-tag size="small" :type="TAG_TYPE[row.status] || 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="处理人" width="64">
          <template #default="{ row }">{{ row.reviewer || (row.status === '已撤回' ? row.applicant + '(撤回)' : '—') }}</template>
        </el-table-column>
        <el-table-column label="处理时间" width="96">
          <template #default="{ row }">{{ fmt(row.reviewedAt || row.withdrawnAt) }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="业务原因" min-width="80" show-overflow-tooltip />
      </el-table>
      <el-empty v-if="!memberHistory.length" description="暂无调整记录" :image-size="48" />
    </el-dialog>

    <!-- 申请详情 -->
    <el-dialog v-model="adjDetailDialog" :title="`调整申请 ${adjDetail.applyNo || ''}`" width="480px">
      <el-descriptions :column="1" size="small" border>
        <el-descriptions-item label="状态"><el-tag size="small" :type="TAG_TYPE[adjDetail.status] || 'info'">{{ adjDetail.status }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="会员">{{ adjDetail.memberNo }} {{ adjDetail.memberName }}</el-descriptions-item>
        <el-descriptions-item label="调整值">{{ adjDetail.delta > 0 ? '+' : '' }}{{ adjDetail.delta }}</el-descriptions-item>
        <el-descriptions-item label="业务原因">{{ adjDetail.reason || '—' }}</el-descriptions-item>
        <el-descriptions-item label="关联凭据">{{ adjDetail.voucher || '—' }}</el-descriptions-item>
        <el-descriptions-item label="申请人">{{ adjDetail.applicant || '—' }}</el-descriptions-item>
        <el-descriptions-item label="申请时间">{{ fmt(adjDetail.createdAt) }}</el-descriptions-item>
        <template v-if="adjDetail.status === '已通过'">
          <el-descriptions-item label="前后积分">{{ adjDetail.pointsBefore }} → {{ adjDetail.pointsAfter }}</el-descriptions-item>
        </template>
        <template v-if="adjDetail.reviewer">
          <el-descriptions-item label="复核人">{{ adjDetail.reviewer }}</el-descriptions-item>
          <el-descriptions-item label="复核意见">{{ adjDetail.reviewComment || '—' }}</el-descriptions-item>
          <el-descriptions-item label="复核时间">{{ fmt(adjDetail.reviewedAt) }}</el-descriptions-item>
        </template>
        <el-descriptions-item v-if="adjDetail.status === '已撤回'" label="撤回时间">{{ fmt(adjDetail.withdrawnAt) }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<style scoped>
.m-tabs :deep(.el-tabs__header) { margin-bottom: 8px; }
.m-tabs :deep(.el-tabs__item) { font-size: 13px; }
.v-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.v-tip { font-size: 12px; color: var(--primary-deep); opacity: 0.75; }
.v-filters { display: flex; gap: 6px; align-items: center; }
.f-tip { font-size: 12px; color: #999; margin-left: 8px; }
.d-sub { font-size: 13px; font-weight: 600; color: var(--primary-deep); margin-bottom: 6px; }
</style>
