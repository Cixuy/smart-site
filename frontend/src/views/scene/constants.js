/**
 * 场景模块共享常量（2026-08-14 新增）
 * 说明：编辑器（editor.vue）3D 预览与列表页（index.vue）全屏查看都需要按坐标渲染设备，
 * 后端暂无实时设备坐标时统一回退到本兜底数据，保证 3D 预览始终有设备展示。
 * 结构说明：iconType（1塔吊 2升降机 3摄像头 4环境监测 5喷淋），坐标为平铺结构（x/y/z/rotation/scale），
 * 后续后端接口就绪后可删除本常量，改为接口实时数据。
 */
export const COORD_FALLBACK = {
  1: [
    { id: 1, deviceCode: 'DEV-0001', deviceName: '塔吊TC001', x: 50, y: 0, z: 0, rotation: 30, scale: 1 },
    { id: 2, deviceCode: 'DEV-0002', deviceName: '塔吊TC002', x: 100, y: 0, z: 90, rotation: 90, scale: 1 },
    { id: 3, deviceCode: 'DEV-0003', deviceName: '塔吊TC003', x: 100, y: 0, z: -90, rotation: 150, scale: 1 },
    { id: 4, deviceCode: 'DEV-0004', deviceName: '塔吊TC004', x: -60, y: 0, z: 40, rotation: 0, scale: 0.8 }
  ],
  2: [
    { id: 5, deviceCode: 'DEV-0005', deviceName: '升降机SC001', x: -20, y: 0, z: 60, rotation: 0, scale: 1 },
    { id: 6, deviceCode: 'DEV-0006', deviceName: '升降机SC002', x: 40, y: 0, z: -30, rotation: 90, scale: 1 }
  ],
  3: [
    { id: 7, deviceCode: 'DEV-0007', deviceName: '摄像头CAM001', x: 50, y: 0, z: 50, rotation: 0, scale: 1 },
    { id: 8, deviceCode: 'DEV-0008', deviceName: '摄像头CAM002', x: -40, y: 0, z: -40, rotation: 0, scale: 1 },
    { id: 9, deviceCode: 'DEV-0009', deviceName: '摄像头CAM003', x: 0, y: 0, z: 80, rotation: 0, scale: 1 }
  ],
  4: [
    { id: 10, deviceCode: 'DEV-0010', deviceName: '环境监测ENV001', x: -60, y: 0, z: -50, rotation: 0, scale: 1 },
    { id: 11, deviceCode: 'DEV-0011', deviceName: '环境监测ENV002', x: 30, y: 0, z: 20, rotation: 0, scale: 1 }
  ],
  5: [
    { id: 12, deviceCode: 'DEV-0012', deviceName: '喷淋SPR001', x: 80, y: 0, z: -60, rotation: 0, scale: 1 },
    { id: 13, deviceCode: 'DEV-0013', deviceName: '喷淋SPR002', x: -80, y: 0, z: 10, rotation: 0, scale: 1 }
  ]
}
