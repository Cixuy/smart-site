/**
 * 工作台 API
 *
 * 参考《2.11.12数据可设计.docx》《软件需求规格说明书》及 Result.java 设计。
 * 所有业务接口统一返回 Result<T>：code、msg、data。
 */
import request from '@/utils/request'

/**
 * 当前系统仅有一个项目，projectId 固定为 1。
 * 后端 overview/get、list-latest、list-todo 中 projectId 为必传参数（@RequestParam），
 * 因此这里统一兜底：调用方未传或传空值时自动使用 1。
 */
const DEFAULT_PROJECT_ID = 1

const workbenchApi = {
  /** 查询工作台首页概览（在线/离线设备数、当日告警、未处理告警、快捷入口、最新告警及角色差异化卡片数据） */
  getOverview({ projectId } = {}) {
    return request({
      url: '/api/workbench/overview/get',
      method: 'get',
      params: { projectId: projectId || DEFAULT_PROJECT_ID }
    })
  },

  /** 查询最新未处置告警滚动列表（工作台默认 5 条） */
  listLatestAlarms({ projectId, limit = 5 } = {}) {
    return request({
      url: '/api/workbench/alarm/list-latest',
      method: 'get',
      params: { projectId: projectId || DEFAULT_PROJECT_ID, limit }
    })
  },

  /** 查询安全管理员待办告警（按告警级别和发生时间排序，默认 5 条） */
  listTodoAlarms({ projectId, limit = 5 } = {}) {
    return request({
      url: '/api/workbench/alarm/list-todo',
      method: 'get',
      params: { projectId: projectId || DEFAULT_PROJECT_ID, limit }
    })
  },

  /** 全局搜索（searchType: 1设备 2告警 3用户；为空表示全部） */
  globalSearch({ keyword, searchType } = {}) {
    return request({
      url: '/api/workbench/search/query',
      method: 'get',
      params: { keyword, searchType }
    })
  },

  /** 查询当前用户搜索历史 */
  getSearchHistory() {
    return request({
      url: '/api/workbench/search-history/list',
      method: 'get'
    })
  },

  /** 清空当前用户搜索历史 */
  clearSearchHistory() {
    return request({
      url: '/api/workbench/search-history/clear',
      method: 'delete'
    })
  }
}

export default workbenchApi
