/** 标签页状态管理 */
const state = () => ({
  visitedViews: [],
  cachedViews: []
})

const mutations = {
  ADD_VISITED_VIEW(state, view) {
    if (state.visitedViews.some(v => v.path === view.path)) return
    state.visitedViews.push({ ...view })
  },
  DEL_VISITED_VIEW(state, view) {
    state.visitedViews = state.visitedViews.filter(v => v.path !== view.path)
  },
  DEL_OTHERS_VISITED_VIEWS(state, view) {
    state.visitedViews = state.visitedViews.filter(
      v => v.meta?.affix || v.path === view.path
    )
  },
  DEL_ALL_VISITED_VIEWS(state) {
    state.visitedViews = state.visitedViews.filter(v => v.meta?.affix)
  },
  ADD_CACHED_VIEW(state, view) {
    if (state.cachedViews.includes(view.name)) return
    if (view.meta?.keepAlive) {
      state.cachedViews.push(view.name)
    }
  },
  DEL_CACHED_VIEW(state, view) {
    const index = state.cachedViews.indexOf(view.name)
    if (index > -1) state.cachedViews.splice(index, 1)
  }
}

const actions = {
  addView({ commit }, view) {
    commit('ADD_VISITED_VIEW', view)
    commit('ADD_CACHED_VIEW', view)
  },
  delView({ commit }, view) {
    commit('DEL_VISITED_VIEW', view)
    commit('DEL_CACHED_VIEW', view)
  },
  delOthersViews({ commit }, view) {
    commit('DEL_OTHERS_VISITED_VIEWS', view)
    // TODO: clear cachedViews for removed views
  },
  delAllViews({ commit }) {
    commit('DEL_ALL_VISITED_VIEWS')
    // TODO: clear cachedViews
  }
}

export default { namespaced: true, state, mutations, actions }
