import { createRouter, createWebHistory } from 'vue-router'

import Layout from '../views/Layout.vue'
import Discover from '../views/Discover.vue'
import Search from '../views/Search.vue'
import Player from '../views/Player.vue'
import Empty from '../views/Empty.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: Layout,
      children: [
        { path: '', redirect: { name: 'Discover' } },
        { path: 'discover', name: 'Discover', component: Discover },
        { path: 'search', name: 'Search', component: Search },
      ],
    },
    { path: '/player', name: 'Player', component: Player },
    { path: '/:pathMatch(.*)*', name: 'Empty', component: Empty },
  ],
})

export default router
