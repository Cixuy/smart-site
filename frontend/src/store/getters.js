/** 全局 Getters */
const getters = {
  token: state => state.user.token,
  userId: state => state.user.userId,
  username: state => state.user.username,
  realName: state => state.user.realName,
  email: state => state.user.email,
  phone: state => state.user.phone,
  avatar: state => state.user.avatar,
  roles: state => state.user.roles,
  permissions: state => state.user.permissions,
  sidebarCollapsed: state => state.app.sidebarCollapsed,
  menuList: state => state.app.menuList,
  visitedViews: state => state.tagsView.visitedViews,
  cachedViews: state => state.tagsView.cachedViews,
  projectId: state => state.app.projectId
}

export default getters
