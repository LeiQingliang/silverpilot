import { defineStore } from "pinia"
import $axios from '../utils/axios'
import { ElNotification } from 'element-plus'

export const useUserStore = defineStore('user', {
    state: () => {
        return {
            roleId: -1,
            searchName: '',
            formData: {},          // 用于新增/编辑的表单数据
            userData: [],
            currentPage: 1,
            pageSize: 5,
            total: 0,
            dialogVisible: false,
            isEdit: false,         // 是否为编辑模式
        }
    },
    actions: {
        async getUsers() {
            const path = '/user/selectByRidByPage/' + 4 + '/' + this.currentPage + '/' + this.pageSize
            const { data: res } = await $axios.get(path)
            if (res.code === 200) {
                this.userData = res.result
                this.total = Math.max(0, Number(res.msg) || 0)
            }
        },
        async selectByNameOrIdNum() {
            if (this.searchName === '' && this.roleId === 4) {
                this.getUsers()
            } else if (this.searchName === '' && this.roleId === 2) {
                this.getAllWorkers()
            } else if (this.searchName === '' && this.roleId === 3) {
                this.getAllDoctors()
            } else {
                const path = '/user/selectByNameOrIdNum/' + this.searchName
                const { data: res } = await $axios.get(path)
                if (res.code === 200) {
                    this.userData = res.result
                    this.total = this.userData.length
                }
            }
        },
        async getAllUser() {
            const path = '/user/selectByRid/' + 4
            const { data: res } = await $axios.get(path)
            if (res.code === 200) {
                this.userData = res.result
            }
        },
        async getAllDoctors() {
            const path = '/user/selectByRid/' + 3
            const { data: res } = await $axios.get(path)
            if (res.code === 200) {
                this.userData = res.result
            }
        },
        async getAllWorkers() {
            const path = '/user/selectByRid/' + 2
            const { data: res } = await $axios.get(path)
            if (res.code === 200) {
                this.userData = res.result
            }
        },

        // 准备新增（清空表单，关闭编辑模式）
        pre4Add() {
            this.isEdit = false
            this.formData = {}   // 清空旧数据
            this.dialogVisible = true
        },

        // 准备编辑（填充表单，打开编辑模式）
        pre4Edit(row) {
            this.isEdit = true
            this.formData = { ...row }   // 深拷贝待编辑数据
            this.dialogVisible = true
        },

        // 通用提交方法（根据 isEdit 决定新增或更新）
        async submitUser() {
            if (this.isEdit) {
                await this.updateUser()
            } else {
                await this.addAllUser()
            }
        },

        // 新增用户（原 addAllUser）
        async addAllUser() {
            const path = '/user/insert'
            const { data: res } = await $axios.put(path, this.formData)
            if (res.code === 200) {
                this.dialogVisible = false
                ElNotification({
                    title: '成功',
                    message: '已成功添加!',
                    type: 'success',
                })
                // 刷新当前角色列表
                if (this.roleId === 3) this.getAllDoctors()
                else if (this.roleId === 2) this.getAllWorkers()
                else this.getAllUser()
            } else if (res.code === 104) {
                ElNotification({
                    title: '失败',
                    message: '用户名已存在，添加失败！',
                    type: 'error',
                })
            } else if (res.code === 105) {
                ElNotification({
                    title: '失败',
                    message: '手机号已存在，添加失败！',
                    type: 'error',
                })
            } else {
                ElNotification({
                    title: '失败',
                    message: '未知原因，添加失败！',
                    type: 'error',
                })
            }
        },

        // 更新用户（新增方法）
        async updateUser() {
            const path = '/user/update'
            const { data: res } = await $axios.post(path, this.formData)
            if (res.code === 200) {
                this.dialogVisible = false
                ElNotification({
                    title: '成功',
                    message: '信息修改成功！',
                    type: 'success',
                })
                // 刷新列表
                if (this.roleId === 3) this.getAllDoctors()
                else if (this.roleId === 2) this.getAllWorkers()
                else this.getAllUser()
            } else if (res.code === 103) {
                ElNotification({
                    title: '失败',
                    message: '修改失败，请重试',
                    type: 'error',
                })
            } else {
                ElNotification({
                    title: '失败',
                    message: '未知错误，修改失败',
                    type: 'error',
                })
            }
        },

        // 注销用户（置 roleId 为 0）
        async logout(row) {
            row.roleId = 0
            this.formData = JSON.parse(JSON.stringify(row))
            const path = '/user/update'
            const { data: res } = await $axios.post(path, this.formData)
            if (res.code === 200) {
                ElNotification({
                    title: '成功',
                    message: '已将' + row.name + '账号注销',
                    type: 'success',
                })
                // 刷新列表
                if (this.roleId === 3) this.getAllDoctors()
                else if (this.roleId === 2) this.getAllWorkers()
                else this.getAllUser()
            } else if (res.code === 103) {
                ElNotification({
                    title: '失败',
                    message: '修改失败',
                    type: 'error',
                })
            }
        },
    },
})
