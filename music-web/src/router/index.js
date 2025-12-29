import { createRouter, createWebHistory } from 'vue-router'

import Layout from '../views/Layout.vue'
import Discover from '../views/Discover.vue'
import Search from '../views/Search.vue'
import Favorites from '../views/Favorites.vue'
import Playlists from '../views/Playlists.vue'
import Recommended from '../views/Recommended.vue'
import PublicPlaylist from '../views/PublicPlaylist.vue'
import Player from '../views/Player.vue'
import Profile from '../views/Profile.vue'
import Admin from '../views/Admin.vue'
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
        { path: 'recommended', name: 'Recommended', component: Recommended },
        { path: 'public-playlist', name: 'PublicPlaylist', component: PublicPlaylist },
        { path: 'search', name: 'Search', component: Search },
        { path: 'favorites', name: 'Favorites', component: Favorites },
        { path: 'playlists', name: 'Playlists', component: Playlists },
        { path: 'player', name: 'Player', component: Player },
        { path: 'profile', name: 'Profile', component: Profile },
        { path: 'admin', redirect: { name: 'AdminUsers' } },
        { path: 'admin/users', name: 'AdminUsers', component: Admin },
        { path: 'admin/music', name: 'AdminMusic', component: Admin },
      ],
    },
    { path: '/:pathMatch(.*)*', name: 'Empty', component: Empty },
  ],
})

export default router
