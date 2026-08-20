import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import FrontView from '../components/front/home/FrontView.vue'
import MyView from '../components/front/myView/MyView.vue'
import { useMenuStore } from "../stores/menu";
import ForumManageView from '../components/serve/forum/ForumManageView.vue'
import ForumHomeView from '../components/front/forum/ForumHomeView.vue'

const ROLE_ADMIN = 1
const ROLE_WORKER = 2
const ROLE_DOCTOR = 3
const ROLE_USER = 4

const PAGE_TITLES = {
  login: '登录', register: '创建账号', IndexView: '运营总览', CountRate: '人员数据',
  ActivityMenageView: '活动管理', ActivityTypeView: '活动分类', ServiceTypeView: '服务分类',
  ServiceOrderView: '服务订单', HealthOrderView: '健康服务订单', HealthMenageView: '健康档案',
  UserMenageView: '用户管理', WorkerMenageView: '工作者管理', DoctorMenageView: '医护人员管理',
  ForumManageView: '留言运营', RecipeManageView: '菜谱管理', RecipeOrderManageView: '菜谱订单',
  RecipeEditView: '菜谱编辑', FrontHomeView: '智慧养老首页', FrontActivityView: '养老活动',
  ActivityDetailView: '活动详情', FrontServiceView: '养老服务', RecipeListView: '健康菜谱',
  RecipeDetailView: '菜谱详情', MyRecipeOrders: '我的菜谱预订', ForumHomeView: '社区留言',
  PersonalCenter: '个人中心', MyActivityView: '我的活动', MyServiceView: '我的服务', AgentOperationsView: '智能服务运营',
  MyCommentView: '我的留言', MyReportView: '我的体检报告', AiLogin: '小伴生活助理', AiChat: '小伴生活助理'
}

const landingRouteByRole = {
  [ROLE_ADMIN]: 'IndexView',
  [ROLE_WORKER]: 'ActivityMenageView',
  [ROLE_DOCTOR]: 'HealthOrderView',
  [ROLE_USER]: 'FrontHomeView'
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  scrollBehavior: () => ({ top: 0, left: 0 }),
  routes: [
    {
      path: '/',
      redirect: '/login'
    },
    {
      path: '/front/ai/AiLogin',
      name: 'AiLogin',
      component: () => import('../components/front/ai/AiLogin.vue'),
      meta: { roles: [ROLE_USER] }
    },
    {
      path: '/front/ai/AiChat',
      name: 'AiChat',
      component: () => import('../components/front/ai/AiChat.vue'),
      meta: { roles: [ROLE_USER] }
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/loginView.vue')
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('../views/registerView.vue')
    },
    {
      path: '/home',
      name: 'home',
      component: HomeView,
      meta: { roles: [ROLE_ADMIN, ROLE_WORKER, ROLE_DOCTOR] },
      children: [
        {
          path: '/CountRate',
          name: 'CountRate',
          component: () => import('../components/serve/echarts/CountRate.vue'),
          meta: { roles: [ROLE_ADMIN] }
        },
        {
          path: '/ActivityMenageView',
          name: 'ActivityMenageView',
          component: () => import('../components/serve/activity/ActivityMenageView.vue'),
          meta: { roles: [ROLE_ADMIN, ROLE_WORKER] }
        },
        {
          path: '/ActivityTypeView',
          name: 'ActivityTypeView',
          component: () => import('../components/serve/activityType/ActivityTypeView.vue'),
          meta: { roles: [ROLE_ADMIN, ROLE_WORKER] }
        },
        {
          path: '/ServiceTypeView',
          name: 'ServiceTypeView',
          component: () => import('../components/serve/serviceType/ServiceTypeView.vue'),
          meta: { roles: [ROLE_ADMIN, ROLE_WORKER] }
        },
        {
          path: '/ServiceOrderView',
          name: 'ServiceOrderView',
          component: () => import('../components/serve/serviceOrder/ServiceOrderView.vue'),
          meta: { roles: [ROLE_ADMIN, ROLE_WORKER] }
        },
        {
          path: '/HealthOrderView',
          name: 'HealthOrderView',
          component: () => import('../components/serve/healthOrder/HealthOrderView.vue'),
          meta: { roles: [ROLE_ADMIN, ROLE_DOCTOR] }
        },
        {
          path: '/HealthMenageView',
          name: 'HealthMenageView',
          component: () => import('../components/serve/healthReport/HealthMenageView.vue'),
          meta: { roles: [ROLE_ADMIN, ROLE_DOCTOR] }
        },
        {
          path: '/UserMenageView',
          name: 'UserMenageView',
          component: () => import('../components/serve/user/UserMenageView.vue'),
          meta: { roles: [ROLE_ADMIN] }
        },
        {
          path: '/WorkerMenageView',
          name: 'WorkerMenageView',
          component: () => import('../components/serve/worker/WorkerMenageView.vue'),
          meta: { roles: [ROLE_ADMIN] }
        },
        {
          path: '/DoctorMenageView',
          name: 'DoctorMenageView',
          component: () => import('../components/serve/doctor/DoctorMenageView.vue'),
          meta: { roles: [ROLE_ADMIN] }
        },
        {
          path: '/ForumManageView',
          name: 'ForumManageView',
          component: ForumManageView,
          meta: { roles: [ROLE_ADMIN, ROLE_DOCTOR] }
        },
        {
          path: '/RecipeManageView',
          name: 'RecipeManageView',
          component: () => import('../components/serve/recipe/RecipeManageView.vue'),
          meta: { roles: [ROLE_ADMIN, ROLE_DOCTOR] }
        },
        {
          path: '/RecipeOrderManageView',
          name: 'RecipeOrderManageView',
          component: () => import('../components/serve/recipe/RecipeOrderManageView.vue'),
          meta: { roles: [ROLE_ADMIN, ROLE_DOCTOR] }
        },
        {
          path: '/RecipeEditView/:id?',
          name: 'RecipeEditView',
          component: () => import('../components/serve/recipe/BackendRecipeEdit.vue'),
          meta: { roles: [ROLE_ADMIN, ROLE_DOCTOR] }
        },
        {
          path: '/AgentOperationsView',
          name: 'AgentOperationsView',
          component: () => import('../components/serve/agent/AgentOperationsView.vue'),
          meta: { roles: [ROLE_ADMIN], title: '智能服务运营' }
        },
        {
          path: '/IndexView',
          name: 'IndexView',
          component: () => import('../components/serve/echarts/IndexView.vue'),
          meta: { roles: [ROLE_ADMIN] }
        },
      ]
    },
    {
      path: '/front',
      name: 'front',
      component: FrontView,
      meta: { roles: [ROLE_USER] },
      children: [
        {
          path: '/front/home/FrontHomeView',
          name: 'FrontHomeView',
          component: () => import('../components/front/home/FrontHomeView.vue')
        },
        {
          path: '/front/activity/FrontActivityView',
          name: 'FrontActivityView',
          component: () => import('../components/front/activity/FrontActivityView.vue')
        },
        {
          path: '/front/activity/ActivityDetailView',
          name: 'ActivityDetailView',
          component: () => import('../components/front/activity/ActivityDetailView.vue')
        },
        {
          path: '/front/service/FrontServiceView',
          name: 'FrontServiceView',
          component: () => import('../components/front/service/FrontServiceView.vue')
        },
        {
          path: '/front/recipe/RecipeListView',
          name: 'RecipeListView',
          component: () => import('../components/front/recipe/RecipeListView.vue')
        },
        {
          path: '/front/recipe/RecipeDetailView/:id',
          name: 'RecipeDetailView',
          component: () => import('../components/front/recipe/RecipeDetailView.vue')
        },
        {
          path: '/front/recipe/MyRecipeOrders',
          name: 'MyRecipeOrders',
          component: () => import('../components/front/recipe/MyRecipeOrders.vue')
        },
        {
          path: '/front/forum/ForumHomeView',
          name: 'ForumHomeView',
          component: ForumHomeView
        },
        {
          path: '/front/personal/PersonalCenter',
          name: 'PersonalCenter',
          component: () => import('../components/front/personal/PersonalCenter.vue')
        },
      ]
    },
    {
      path: '/MyView',
      name: 'MyView',
      component: MyView,
      meta: { roles: [ROLE_USER] },
      children: [
        {
          path: '/front/myActivity/MyActivityView',
          name: 'MyActivityView',
          component: () => import('../components/front/myActivity/MyActivityView.vue')
        },
        {
          path: '/front/myService/MyServiceView',
          name: 'MyServiceView',
          component: () => import('../components/front/myService/MyServiceView.vue')
        },
        {
          path: '/front/myComment/MyCommentView',
          name: 'MyCommentView',
          component: () => import('../components/front/myComment/MyCommentView.vue')
        },
        {
          path: '/front/myReport/MyReportView',
          name: 'MyReportView',
          component: () => import('../components/front/myReport/MyReportView.vue')
        },
        {
          path: '/front/myRecipe/MyRecipeOrders',
          redirect: '/front/recipe/MyRecipeOrders'
        },
      ]
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/login'
    },
  ]
})

const publicRoutes = new Set(['login', 'register'])

const getStoredRoleId = () => {
  const directRoleId = Number(sessionStorage.getItem('roleId') || localStorage.getItem('roleId'))
  if (Object.hasOwn(landingRouteByRole, directRoleId)) return directRoleId

  try {
    const storedUser = sessionStorage.getItem('user') || localStorage.getItem('user')
    const roleId = Number(JSON.parse(storedUser)?.roleId)
    return Object.hasOwn(landingRouteByRole, roleId) ? roleId : null
  } catch {
    return null
  }
}

router.beforeEach((to) => {
  const token = sessionStorage.getItem('token') || localStorage.getItem('token')
  if (!publicRoutes.has(to.name) && !token) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  if (!publicRoutes.has(to.name)) {
    const roleId = getStoredRoleId()
    if (!roleId) {
      sessionStorage.clear()
      for (const authKey of ['token', 'user', 'id', 'roleId']) {
        localStorage.removeItem(authKey)
      }
      return { name: 'login' }
    }

    const allowedRoles = to.meta.roles
    if (Array.isArray(allowedRoles) && !allowedRoles.includes(roleId)) {
      return { name: landingRouteByRole[roleId] }
    }
  }

  const menuStore = useMenuStore()
  menuStore.setCurrentpath(to)
  return true
})

router.afterEach((to) => {
  const pageTitle = PAGE_TITLES[to.name] || '智慧养老服务'
  document.title = `${pageTitle} · SilverPilot`
  window.requestAnimationFrame(() => {
    document.getElementById('main-content')?.focus({ preventScroll: true })
  })
})

export default router
