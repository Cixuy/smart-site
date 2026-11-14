import { createStore } from 'vuex'
import app from './modules/app'
import user from './modules/user'
import tagsView from './modules/tagsView'
import getters from './getters'

export default createStore({
  state: () => ({
    imgShowRoad: import.meta.env.VITE_APP_Address,
    fileUploadRoad: import.meta.env.VITE_APP_Address
  }),
  getters,
  mutations: {},
  actions: {},
  modules: {
    app,
    user,
    tagsView
  }
})
