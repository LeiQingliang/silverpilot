import { defineStore } from 'pinia'
import $axios from '../utils/axios'
import { ElMessage } from 'element-plus'

export const useActivityStore = defineStore('activity', {
    state: () => {
        return {
            index: -1,
            dialogFormVisible: false,
            dialogFormTitle: '',
            saving: false,
            saveError: '',
            aName: '',
            tableData: [],
            directorsData: [],//所有可选的活动负责人
            typesData: [],//可选活动类型
            activityTypeId: -1,
            formdata: {
                id: -1
            },
            formdateCopy: {
                activityType: {},
                director: {}
            },
            activityDetail: {
                id: -1,
                pic: ''
            },
            currentPage: 1,
            pageSize: 3,
            total: 0,
        }
    },
    actions: {
        async init() {
            const path = '/activity/findAll'
            const { data: res } = await $axios.get(path)
            if (res.code === 200) {
                this.tableData = res.result
            }
        },
        async selectAllByPage() {//分页初始化
            const path = '/activity/selectAllByPage/' + this.currentPage + '/' + this.pageSize
            const { data: res } = await $axios.get(path)
            if (res.code === 200) {
                this.tableData = res.result
                this.total = Math.max(0, Number(res.msg) || 0)
            }
        },
        async loadbyname() {//分页查询
            if (this.aName === '') {
                await this.selectAllByPage()
            } else {
                const path = '/activity/selectByNameByPage/' + this.aName + '/' + this.currentPage + '/' + this.pageSize
                const { data: res } = await $axios.get(path)
                if (res.code === 200) {
                    this.tableData = res.result
                    this.total = Math.max(0, Number(res.msg) || 0)
                }
            }
        },
        async selectByType(activityTypeId) {
            const path = '/activity/selectByType/' + activityTypeId
            const { data: res } = await $axios.get(path)
            if (res.code === 200) {
                return res.result
            }
        },
        async loadDirector() {//加载所有可选的活动负责人
            const path = '/user/selectByRid/' + 2
            const { data: res } = await $axios.get(path)
            if (res.code === 200) {
                this.directorsData = res.result
            }
        },
        async loadType() {//加载活动类型
            const path = '/activityType/selectByState1'
            const { data: res } = await $axios.get(path)
            if (res.code === 200) {
                this.typesData = res.result
            }
        },
        preInfo4Add() {
            if (this.saving) return
            this.resetEditor()
            this.dialogFormTitle = '发布最新活动'
            this.dialogFormVisible = true
            this.loadDirector()
            this.loadType()
        },
        async addActivity(payload) {//发布活动
            const path = '/activity/insert'
            const { data: res } = await $axios.put(path, payload, { suppressErrorToast: true })
            return res
        },
        async deleteAction(row) {
            const path = '/activity/del/' + row.id
            const { data: res } = await $axios.post(path)
            if (res.code === 200) {
                ElMessage({
                    message: '成功！',
                    type: 'success',
                })
                this.selectAllByPage()
            }
            else {
                ElMessage.error(res.msg)
            }
        },
        preInfo4Edit(index, row) {
            if (this.saving) return
            this.index = index
            this.dialogFormTitle = '编辑活动内容'
            this.formdata = JSON.parse(JSON.stringify(row))
            this.formdateCopy = JSON.parse(JSON.stringify(row))
            this.saveError = ''
            this.dialogFormVisible = true
            this.loadDirector()
            this.loadType()
        },
        async editActivity(payload) {
            const path = '/activity/update'
            const { data: res } = await $axios.post(path, payload, { suppressErrorToast: true })
            return res
        },
        resetEditor() {
            this.formdata = { id: -1 }
            this.formdateCopy = { activityType: {}, director: {} }
            this.saveError = ''
            this.index = -1
        },
        async save() {
            if (this.saving) return false
            this.saving = true
            this.saveError = ''
            try {
                const payload = JSON.parse(JSON.stringify(this.formdata))
                const editing = Number(payload.id) > 0
                const res = await (editing ? this.editActivity(payload) : this.addActivity(payload))
                if (res.code !== 200) {
                    this.saveError = res.msg || '保存失败，请检查活动信息后重试'
                    return false
                }
                this.dialogFormVisible = false
                this.resetEditor()
                ElMessage({ message: editing ? '修改成功！' : '发布成功！', type: 'success' })
                try {
                    await this.loadbyname()
                } catch {
                    ElMessage.warning('活动已保存，列表刷新失败，请刷新页面查看')
                }
                return true
            } catch (error) {
                this.saveError = error.response?.data?.msg || error.message || '保存失败，请稍后重试'
                return false
            } finally {
                this.saving = false
            }
        },
        async initNotBegin() {
            const path = '/activity/selectNotBegin'
            const { data: res } = await $axios.get(path)
            if (res.code === 200) {
                this.tableData = res.result
            }
        },
    }
})
