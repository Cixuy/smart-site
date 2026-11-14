<template>
  <div class="page-container space-y-4">
    <div class="flex items-center justify-between flex-wrap gap-4">
      <div><h2 class="text-xl font-bold text-gray-800">3D场景配置</h2><p class="text-sm text-gray-400 mt-1">配置场景信息、模型与设备坐标</p></div>
      <el-button type="primary" :icon="Check" :loading="saving" @click="saveConfig">保存配置</el-button>
    </div>

    <el-row :gutter="16">
      <el-col :span="15">
        <div class="bg-white rounded-xl p-5 shadow-sm border border-gray-100">
          <p class="font-semibold text-gray-700 mb-4">3D模型预览</p>
          <div class="rounded-lg overflow-hidden" style="height: 460px">
            <!-- 2026-08-14 需求调整：上传 3D 模型与模型类型选择已注释；
                 预览恢复原逻辑——底图用场景配置的 modelFileUrl（mock 场景为空则不显示），
                 选中类型（塔吊 iconType=1）的设备用内置模型 tdcf.glb 作为模板 clone 渲染（device-model-url 兜底为 tdcfModel） -->
            <Scene3DPreview :model-url="sceneForm.modelFileUrl" :devices="deviceProps" :model-type="modelType" :device-model-url="deviceModelUrl" />
          </div>
        </div>
      </el-col>

      <el-col :span="9">
        <div class="bg-white rounded-xl p-5 shadow-sm border border-gray-100 h-full">
          <p class="font-semibold text-gray-700 mb-4">场景基本信息</p>
          <el-form :model="sceneForm" label-width="80px">
            <el-form-item label="场景名称" prop="sceneName"><el-input v-model="sceneForm.sceneName" placeholder="请输入场景名称" /></el-form-item>
            <el-form-item label="项目ID" prop="projectId"><el-input-number v-model="sceneForm.projectId" :min="1" style="width:100%" :disabled="!!sceneId" /></el-form-item>
            <!-- 2026-08-14 需求调整：模型类型选择与 3D 模型上传已注释，仅展示内置模型 tdcf.glb。
                 如需恢复：取消注释下方两个 el-form-item，并同步恢复脚本中 selectedFile/previewUrl/uploading/modelType(ref)/onModelTypeChange 等相关逻辑
            <el-form-item label="模型类型" required>
              <el-select v-model="modelType" placeholder="请选择模型类型" style="width:100%" @change="onModelTypeChange">
                <el-option v-for="t in ICON_TYPES" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
              <p class="text-xs text-gray-400 mt-1">选择类型后自动获取该类设备坐标并渲染到 3D 预览，模型随上传提交</p>
            </el-form-item>
            <el-form-item label="3D模型">
              <el-upload
                drag
                :auto-upload="false"
                :limit="1"
                accept=".glb,.gltf,.obj,.fbx"
                :on-change="handleFileChange"
                :on-remove="handleFileRemove"
              >
                <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
                <div class="el-upload__text">拖拽或<em>点击上传</em></div>
                <template #tip>
                  <div class="el-upload__tip">支持 .glb / .gltf / .obj / .fbx，选择后立即在左侧预览</div>
                </template>
              </el-upload>
              <div class="flex items-center gap-2 mt-2">
                <el-button size="small" type="primary" plain :loading="uploading" :disabled="!selectedFile || !modelType" @click="uploadModel">上传模型</el-button>
                <span v-if="sceneForm.modelFileUrl" class="text-xs text-green-600 truncate">{{ sceneForm.modelFileUrl }}</span>
              </div>
              <p v-if="sceneForm.modelFileMd5" class="text-xs text-gray-400 mt-1">MD5：{{ sceneForm.modelFileMd5 }}</p>
            </el-form-item>
            -->
          </el-form>
        </div>
      </el-col>
    </el-row>

    <!-- 2026-08-13 需求调整：移除"设备摄像头"面板。
         获取视频流/摄像头管理归属视频监控模块（monitor/video），场景模块只保留 3D 可视化与设备坐标绑定 -->
    <div class="bg-white rounded-xl p-5 shadow-sm border border-gray-100">
      <div class="flex items-center justify-between mb-4">
        <!-- 2026-08-18 需求调整：设备坐标绑定与设备台账保持一致——设备名称/类型来自设备台账，
             X/Y/Z坐标、旋转角度、缩放由用户编辑，"保存"将坐标同步到场景坐标接口 -->
        <span class="font-semibold text-gray-700">设备坐标绑定（来源：设备台账）</span>
      </div>
      <el-table :data="coordList" border stripe style="width:100%">
        <el-table-column prop="deviceName" label="设备名称" width="150" show-overflow-tooltip />
        <el-table-column prop="typeName" label="类型" width="110" show-overflow-tooltip />
        <el-table-column label="X坐标" width="110">
          <template #default="{ row }">
            <!-- 2026-08-18 修复：清空输入(null)时自动归零，避免显示空白 -->
            <el-input-number :model-value="row.posX ?? 0" :step="1" size="small" controls-position="right" style="width:100%" @update:model-value="v => row.posX = coordFallback('posX', v)" />
          </template>
        </el-table-column>
        <el-table-column label="Y坐标" width="110">
          <template #default="{ row }">
            <el-input-number :model-value="row.posY ?? 0" :step="1" size="small" controls-position="right" style="width:100%" @update:model-value="v => row.posY = coordFallback('posY', v)" />
          </template>
        </el-table-column>
        <el-table-column label="Z坐标" width="110">
          <template #default="{ row }">
            <el-input-number :model-value="row.posZ ?? 0" :step="1" size="small" controls-position="right" style="width:100%" @update:model-value="v => row.posZ = coordFallback('posZ', v)" />
          </template>
        </el-table-column>
        <el-table-column label="旋转角度" width="110">
          <template #default="{ row }">
            <el-input-number :model-value="row.rotation ?? 0" :step="5" size="small" controls-position="right" style="width:100%" @update:model-value="v => row.rotation = coordFallback('rotation', v)" />
          </template>
        </el-table-column>
        <el-table-column label="缩放" width="100">
          <template #default="{ row }">
            <!-- 缩放清空后恢复默认 1（归零会导致模型不可见） -->
            <el-input-number :model-value="row.scale ?? 1" :min="0.01" :step="0.1" size="small" controls-position="right" style="width:100%" @update:model-value="v => row.scale = coordFallback('scale', v)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" align="center" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" :loading="savingId === row.deviceId" @click="saveCoord(row)">保存</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
// 2026-08-18 需求调整：sceneId 硬编码为 8，不再从路由读取，useRoute 已移除
import { ElMessage } from 'element-plus'
// 2026-08-13 移除 videoApi 引用："设备摄像头"面板已删除，摄像头管理归视频监控模块，场景模块不再依赖视频接口
// 2026-08-18 需求调整：设备坐标绑定的设备来自设备台账（assetApi.getDevicePage），名称/类型以台账为准
import { sceneApi, assetApi } from '@/api'
import Scene3DPreview from './components/Scene3DPreview.vue'
// 本地默认模型：开发演示用（打包后作为静态资源输出，返回可访问 URL）
import tdcfModel from '@/assets/models/tdcf.glb'

// 2026-08-18 需求调整：sceneId 硬编码为 8——对应数据库中已存在的场景「测试场景A」(id=8, projectId=1)。
// 注意：create/{sceneId} 保存坐标时后端会 selectById 强校验场景存在，因此该值必须与库中实际场景 id 一致；
// 若数据库重建后场景 id 变化，需同步修改此处（与 sql/20260818_scene_3d_config_seed.sql 保持一致）
const sceneId = ref(8)

const sceneForm = reactive({
  sceneName: '',
  projectId: undefined,
  modelFileUrl: '',
  modelFileMd5: '',
  centerX: 0,
  centerY: 0,
  centerZ: 0,
  isSimpleMode: false
})

// 2026-08-14 需求调整：模型上传相关状态已注释（selectedFile/previewUrl/uploading），仅展示内置模型 tdcf.glb
// const selectedFile = ref(null)
// const previewUrl = ref('')
// const uploading = ref(false)
// 2026-08-14 需求调整：模型类型固定为塔吊（iconType=1），不再允许用户切换。
// 原代码为 ref(null)，通过右侧下拉选择后拉取设备坐标；现固定后仅用于设备坐标绑定面板展示与 3D 预览的设备类型
const modelType = 1
const saving = ref(false)

// 2026-08-18 需求调整：设备坐标绑定列表改为从设备台账（assetApi.getDevicePage）拉取全部设备，
// 设备名称/类型直接使用台账字段；X/Y/Z坐标、旋转角度、缩放由用户编辑后通过保存按钮同步到场景坐标接口
const coordList = ref([])
/** 正在保存的行（按 deviceId 标记，用于按钮 loading） */
const savingId = ref(null)

/** 2026-08-18 修复：坐标输入框清空(null)时的兜底默认值——坐标/旋转归零、缩放恢复1（归零会导致模型不可见） */
function coordFallback(key, v) {
  return v ?? (key === 'scale' ? 1 : 0)
}

/** 转换为 3D 预览所需结构（与后端 DeviceCoordinate 字段对齐），驱动 Scene3DPreview 渲染设备。
 * 2026-08-18 修复：Y/Z 轴语义调整——表格中 Z 坐标为高度（上下飞）、Y 坐标为水平前后；
 * Three.js 渲染时 Y 轴向上，故渲染坐标 y 取表格 posZ（高度）、z 取表格 posY（水平） */
const deviceProps = computed(() =>
  coordList.value.map(c => ({
    deviceName: c.deviceName,
    iconType: c.iconType || modelType || 1,
    position: { x: c.posX || 0, y: c.posZ || 0, z: c.posY || 0 },
    rotation: { x: 0, y: c.rotation || 0, z: 0 },
    scale: { x: c.scale || 1, y: c.scale || 1, z: c.scale || 1 }
  }))
)

// 2026-08-14 需求调整：恢复 deviceModelUrl——上传 3D 模型功能虽已注释，但 4 个塔吊设备仍需用内置模型 tdcf.glb
// 作为模板渲染（device-model-url 兜底为 tdcfModel），保持预览效果不变
const deviceModelUrl = computed(() => sceneForm.modelFileUrl || tdcfModel)

// 2026-08-18 需求调整：设备坐标绑定列表改为从设备台账拉取设备（与设备台账保持一致），
// 只保留塔吊设备（typeId=2，与塔吊监控模块口径一致）；名称/类型用台账字段 typeName/deviceName；
// 坐标字段由用户编辑后保存到场景坐标接口；进入页面时优先用接口已保存坐标回填（getDeviceList），
// 无保存记录时默认 0/0/0/0/1
async function loadCoordList() {
  let records = []
  try {
    const res = await assetApi.getDevicePage({ typeId: 2, pageNum: 1, pageSize: 500 })
    records = (res.data && (res.data.records || res.data.list)) || []
  } catch (_) { records = [] }
  if (!records.length) { coordList.value = []; return }

  // 读取场景坐标接口已保存的坐标（对应 GET /api/scene/device/list/{sceneId}?iconType=），按 deviceId 建索引用于回填。
  // 2026-08-18 修复：后端实际返回 data 为数组(List<DeviceCoordinateVO>)，而接口文档写的是 {records:[...]}，
  // 因此同时兼容两种结构，否则 coordinateId 永远恢复不到、每次保存都会走 create 触发后端 409 重复校验
  const savedMap = new Map()
  if (sceneId.value) {
    try {
      const res = await sceneApi.getDeviceList(sceneId.value, modelType || 1)
      const saved = Array.isArray(res.data) ? res.data : ((res.data && res.data.records) || [])
      saved.forEach(s => savedMap.set(s.deviceId, s))
    } catch (_) { /* 接口失败时无回填，坐标按默认值展示 */ }
  }

  coordList.value = records.map(d => {
    const s = savedMap.get(d.id)
    // 已保存坐标读取：position/rotation/scale 为 Vector3 对象；兼容后端返回平铺字段(posX/rotationY/scaleX)的情况。
    // Y/Z 轴语义：接口的 Y 为高度(Three.js 约定) → 表格 posZ；接口的 Z 为水平 → 表格 posY
    return {
      deviceId: d.id,
      coordinateId: s?.coordinateId ?? null, // 从接口恢复绑定ID，二次保存走 update 接口
      deviceName: d.deviceName,
      typeName: d.typeName,
      iconType: modelType || 1, // 台账设备统一按当前模型类型（塔吊）渲染
      posX: s ? (s.position?.x ?? s.posX ?? 0) : 0,
      posY: s ? (s.position?.z ?? s.posZ ?? 0) : 0,
      posZ: s ? (s.position?.y ?? s.posY ?? 0) : 0,
      rotation: s ? (s.rotation?.y ?? s.rotationY ?? 0) : 0,
      scale: s ? (s.scale?.x ?? s.scaleX ?? 1) : 1
    }
  })
}

/** 保存单行设备坐标到场景坐标接口：无绑定ID时走新增(create)，已有绑定ID时走更新(update)。
 * 2026-08-18 调整：
 * 1) 请求体对齐后端 DTO 的平铺字段(posX/posY/posZ、rotationX/rotationY/rotationZ、scaleX/scaleY/scaleZ)；
 * 2) create 成功时后端返回 Result<Long>(data 为数字 id，非文档的 {id})，此处兼容两种结构回填 coordinateId；
 * 3) create 触发 409（后端按 deviceId 全局唯一校验，已绑定过的设备重复 create 会冲突）时，
 *    自动从设备列表接口查出 coordinateId 转走 update，保证任意状态下保存都能成功入库。
 * Y/Z 轴语义与 deviceProps 保持一致：表格 Z 为高度 → 提交 posY；表格 Y 为水平 → 提交 posZ */
async function saveCoord(row) {
  if (!sceneId.value) { ElMessage.warning('请先创建场景后再保存坐标'); return }
  savingId.value = row.deviceId
  const payload = {
    deviceId: row.deviceId,
    iconType: row.iconType || modelType || 1,
    posX: row.posX ?? 0,
    posY: row.posZ ?? 0, // 表格 Z(高度) → 接口 posY(Three.js Y 轴向上)
    posZ: row.posY ?? 0, // 表格 Y(水平) → 接口 posZ
    rotationX: 0,
    rotationY: row.rotation ?? 0,
    rotationZ: 0,
    scaleX: row.scale ?? 1,
    scaleY: row.scale ?? 1,
    scaleZ: row.scale ?? 1
  }
  try {
    if (row.coordinateId) {
      await sceneApi.updateDeviceCoordinate(row.coordinateId, payload)
    } else {
      try {
        const res = await sceneApi.createDeviceCoordinate(sceneId.value, payload)
        // 后端返回 Result<Long>：data 为数字 id；兼容文档 {id} 对象结构
        row.coordinateId = typeof res.data === 'number'
          ? res.data
          : ((res.data && (res.data.id || res.data.coordinateId)) || null)
      } catch (err) {
        // 409：设备已绑定过（后端按 deviceId 全局唯一校验），查列表恢复 coordinateId 后转 update
        if (err?.response?.status === 409 || err?.code === 409) {
          const res = await sceneApi.getDeviceList(sceneId.value, modelType || 1)
          const arr = Array.isArray(res.data) ? res.data : ((res.data && res.data.records) || [])
          const found = arr.find(s => s.deviceId === row.deviceId)
          if (found?.coordinateId) {
            row.coordinateId = found.coordinateId
            await sceneApi.updateDeviceCoordinate(row.coordinateId, payload)
          } else {
            throw err // 未找到已绑定记录，保持原错误由拦截器提示
          }
        } else {
          throw err
        }
      }
    }
    ElMessage.success(`已保存 ${row.deviceName} 的坐标`)
  } catch (err) {
    console.error('保存设备坐标失败', err) // 具体错误提示已由 request 拦截器统一弹出
  }
  finally { savingId.value = null }
}

// 2026-08-18 调整：原 ICON_TYPES/iconTypeText 已删除——设备坐标绑定表格的"类型"列直接显示台账的 typeName，
// 不再依赖 iconType 映射文案；如需恢复模型类型选择可重新引入
// 2026-08-14 需求调整：本地模型文件选择/移除逻辑已注释（上传 3D 模型功能停用，仅展示内置模型 tdcf.glb）
// function handleFileChange(file) {
//   selectedFile.value = file.raw
//   // 撤销旧的本地预览地址，避免内存泄漏
//   if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
//   // 用本地文件生成 ObjectURL，立即在 3D 视口预览，不依赖后端
//   previewUrl.value = URL.createObjectURL(file.raw)
// }
//
// function handleFileRemove() {
//   selectedFile.value = null
//   if (previewUrl.value) { URL.revokeObjectURL(previewUrl.value); previewUrl.value = '' }
// }

// 2026-08-17 需求调整："添加设备"按钮已隐藏，addCoord 不再被引用，注释保留便于后续恢复
// function addCoord() {
//   // 新建设备默认归入当前模型类型（固定为塔吊 iconType=1），与 3D 预览渲染的设备类型保持一致
//   coordList.value.push({ deviceName: '', iconType: modelType || 1, posX: 0, posY: 0, posZ: 0, rotation: 0, scale: 1 })
// }

// mock 兜底场景（后端场景接口未就绪时展示用）：
// 2026-08-14 需求调整：上传/服务端模型功能已停用，modelFileUrl 仅作为场景配置字段保留；
// 3D 预览的底图用 sceneForm.modelFileUrl（mock 场景为空则不显示），4 个塔吊设备用内置模型 tdcf.glb 模板渲染
const mockScene = {
  id: 1,
  sceneName: '主工地3D场景',
  projectId: 1001,
  modelFileUrl: '',
  modelFileMd5: '9e107d9d372bb6826bd81d3542a419d6',
  centerX: 0,
  centerY: 0,
  centerZ: 0,
  isSimpleMode: false
}

function applyScene(d) {
  Object.assign(sceneForm, {
    sceneName: d.sceneName || '',
    projectId: d.projectId,
    modelFileUrl: d.modelFileUrl || '',
    modelFileMd5: d.modelFileMd5 || '',
    centerX: d.centerX || 0,
    centerY: d.centerY || 0,
    centerZ: d.centerZ || 0,
    isSimpleMode: !!d.isSimpleMode
  })
}

async function loadScene() {
  // 2026-08-18 需求调整：sceneId 固定为 8，直接查询场景详情；查询失败仅回退 mock 场景展示，
  // 但不再把 sceneId 改成 mock 值（避免保存坐标时后端查不到场景报「场景不存在」）
  try {
    const res = await sceneApi.getConfigDetail(sceneId.value)
    if (res.data && res.data.id) { applyScene(res.data); return }
  } catch (_) { /* 接口异常，回退 mock 演示场景 */ }
  applyScene(mockScene)
}

// 2026-08-14 需求调整：模型上传函数已注释（上传 3D 模型功能停用，仅展示内置模型 tdcf.glb）
// async function uploadModel() {
//   if (!sceneId.value) { ElMessage.warning('请先创建场景后再上传模型'); return }
//   if (!selectedFile.value) { ElMessage.warning('请先选择模型文件'); return }
//   if (!modelType.value) { ElMessage.warning('请选择模型类型'); return }
//   uploading.value = true
//   try {
//     const formData = new FormData()
//     formData.append('file', selectedFile.value)
//     // 模型类型随上传接口提交（文档 POST /api/scene/model/upload/{sceneId} 的 modeltype 参数）
//     formData.append('modeltype', modelType.value)
//     const res = await sceneApi.uploadModel(sceneId.value, formData)
//     const d = res.data || {}
//     sceneForm.modelFileUrl = d.modelFileUrl || ''
//     sceneForm.modelFileMd5 = d.modelFileMd5 || ''
//     ElMessage.success('模型上传成功')
//   } catch (_) { /* ignore */ }
//   finally { uploading.value = false }
// }

async function saveConfig() {
  if (!sceneId.value) { ElMessage.warning('请先创建场景后再保存配置'); return }
  if (!sceneForm.sceneName) { ElMessage.warning('请填写场景名称'); return }
  saving.value = true
  try {
    await sceneApi.updateConfig(sceneId.value, { ...sceneForm })
    ElMessage.success('保存成功')
  } catch (_) { /* ignore */ }
  finally { saving.value = false }
}

onMounted(async () => {
  await loadScene()
  // 2026-08-18 需求调整：设备坐标绑定与设备台账保持一致——从设备台账拉取全部设备用于展示与编辑
  await loadCoordList()
  // 2026-08-13 已移除 loadDeviceCameras 调用（"设备摄像头"面板删除，归视频监控模块）
})
</script>

<style scoped>.page-container { padding: 0; }</style>
