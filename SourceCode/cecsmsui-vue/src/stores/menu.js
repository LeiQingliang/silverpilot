import { defineStore } from "pinia";
import $axios from "../utils/axios";

export const useMenuStore = defineStore("menu", {
    state: () => {
        return {
            menus: [],
            menutree: {},
            menus4me: [],
            menutree4me: [],
            ActivityTypeView: ["社区活动管理", "活动类型管理"],
            ActivityMenageView: ["社区活动管理", "活动管理"],
            ServiceTypeView: ["社区服务管理", "服务分类"],
            ServiceOrderView: ["社区服务管理", "服务订单"],
            HealthMenageView: ["健康管理", "健康档案"],
            HealthOrderView: ["健康管理", "订单管理"],
            UserMenageView: ["系统管理", "用户管理"],
            WorkerMenageView: ["系统管理", "工作者管理"],
            DoctorMenageView: ["系统管理", "医护人员管理"],
            AgentOperationsView: ["智能服务运营", "小伴运行与知识协同"],
            currentpath: "ActivityMenageView",
            formPassenger: {
                roleId: -1,
            }
        }
    },
    getters: {
        getCrmbreadcrumbByPageName: (state) => state[state.currentpath],
    },
    actions: {
        async findPassenger(username) {
            try {
                const stored = sessionStorage.getItem('user')
                const user = stored ? JSON.parse(stored) : null
                this.formPassenger = user?.username === username && Number.isInteger(user?.roleId)
                    ? user
                    : { roleId: -1 }
            } catch (error) {
                this.formPassenger = { roleId: -1 }
            }
        },

        async load4MeByLists() {
            try {
                // 1. 获取sessionStorage中的用户信息
                const s = sessionStorage.getItem("user");
                if (!s) {
                    this.menus4me = [];
                    this.buildMenuTree4Me();
                    return
                }

                const p = JSON.parse(s);
                if (!p || !p.username || !Number.isInteger(p.roleId)) {
                    this.menus4me = [];
                    this.buildMenuTree4Me();
                    return
                }


                // 2. 获取用户信息
                await this.findPassenger(p.username);

                // 3. 检查roleId是否有效
                if (!this.formPassenger || this.formPassenger.roleId === undefined || this.formPassenger.roleId === -1) {
                    this.menus4me = [];
                    this.buildMenuTree4Me();
                    return
                }

                // 4. 获取用户菜单权限
                this.menus4me = [];
                const path = "/sysFunction/selectByRid/" + this.formPassenger.roleId;
                const { data: res } = await $axios.get(path);

                if (res && res.code === 200) {
                    this.menus4me = res.result || res.data || [];
                } else {
                    this.menus4me = [];
                }


                // 5. 构建菜单树
                this.buildMenuTree4Me();

            } catch (error) {
                this.menus4me = [];
                this.buildMenuTree4Me();
            }
        },

        // 获取默认菜单（应急用）
        getDefaultMenus4Me() {
            return []
        },

        buildMenuTree4Me() {
            try {
                this.menutree4me = [];

                if (!this.menus4me || this.menus4me.length === 0) {
                    this.menutree4me = [];
                    return
                }

                // 查找根节点（fId === 0）
                const root = this.menus4me.find((menu) => menu.fId === 0);

                if (!root) {
                    console.warn('未找到根菜单，使用第一个菜单作为根')
                    const firstMenu = this.menus4me[0]
                    if (firstMenu) {
                        this.menutree4me[0] = { ...firstMenu, children: [] }
                        this.fillChildren4Me(this.menutree4me[0])
                    }
                } else {
                    this.menutree4me[0] = { ...root, children: [] }
                    this.fillChildren4Me(this.menutree4me[0])
                }

            } catch (error) {
                this.menutree4me = []
            }
        },

        getChildren4me(fId) {
            if (!this.menus4me || !Array.isArray(this.menus4me)) {
                return []
            }
            return this.menus4me.filter((menu) => menu.fId === fId)
        },

        setCurrentpath(to) {
            if (to && to.name) {
                this.currentpath = to.name
            }
        },

        getChildren(fId) {
            if (!this.menus || !Array.isArray(this.menus)) {
                return []
            }
            return this.menus.filter((menu) => menu.fId === fId)
        },

        /**
         * 为当前用户菜单填充子节点
         */
        fillChildren4Me(root) {
            if (!root || !this.menus4me) return

            root.children = []
            for (let i = 0; i < this.menus4me.length; i++) {
                if (this.menus4me[i].fId === root.id) {
                    root.children.push({ ...this.menus4me[i], children: [] })
                }
            }

            for (let i = 0; root.children && i < root.children.length; i++) {
                this.fillChildren4Me(root.children[i])
            }
        },

        /**
         * 为所有菜单填充子节点
         */
        fillChildren(root) {
            if (!root || !this.menus) return

            root.children = []
            for (let i = 0; i < this.menus.length; i++) {
                if (this.menus[i].fId === root.id) {
                    root.children.push({ ...this.menus[i], children: [] })
                }
            }

            for (let i = 0; root.children && i < root.children.length; i++) {
                this.fillChildren(root.children[i])
            }
        },

        /**
         * 转换所有菜单List到Tree
         */
        buildMenuTree() {
            try {
                this.menutree = []

                if (!this.menus || this.menus.length === 0) {
                    console.warn('menus为空，无法构建菜单树')
                    return
                }

                const root = this.menus.find((menu) => menu.fId === 0)
                if (!root) {
                    console.warn('未找到根菜单')
                    return
                }

                this.menutree[0] = { ...root, children: [] }
                this.fillChildren(this.menutree[0])
            } catch (error) {
                console.error('buildMenuTree 发生错误:', error)
            }
        },

        async loadAllByLists() {
            try {
                this.menus = []
                const path = "/sysFunction/selectAll"
                const { data: res } = await $axios.get(path)

                if (res && res.code === 200) {
                    this.menus = res.data || res.result || []
                    this.buildMenuTree()
                } else {
                    console.warn('获取所有菜单失败:', res)
                }

            } catch (error) {
                console.error('loadAllByLists 发生错误:', error)
            }
        },

        /**
         * 清空状态
         */
        clear() {
            this.menus = []
            this.menutree = {}
            this.menus4me = []
            this.menutree4me = []
            this.formPassenger = { roleId: -1 }
        }
    }
})
