import { defineStore } from 'pinia'
import $axios from '../utils/axios'
import { ElMessage } from 'element-plus'

export const useActivityStore = defineStore('activity', {
    state: () => {
        return {
            index: -1,
            dialogFormVisible: false,
            dialogFormTitle: '',
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
            this.tableData.length = 0
            if (this.aName === '') {
                this.selectAllByPage()
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
            this.dialogFormTitle = '发布最新活动'
            this.dialogFormVisible = true
            this.loadDirector()
            this.loadType()
        },
        async addActivity() {//发布活动
            const path = '/activity/insert'
            const { data: res } = await $axios.put(path, this.formdata)
            if (res.code === 200) {
                this.dialogFormVisible = false
                ElMessage({
                    message: '成功！',
                    type: 'success',
                })
                this.selectAllByPage()
            }
            else {
                this.dialogFormVisible = false
                ElMessage.error(res.msg)
            }
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
            this.index = index
            this.dialogFormTitle = '编辑活动内容'
            this.dialogFormVisible = true
            this.formdata = JSON.parse(JSON.stringify(row))
            this.formdateCopy = JSON.parse(JSON.stringify(row))
            this.loadDirector()
            this.loadType()
        },
        async editActivity() {
            const path = '/activity/update'
            const { data: res } = await $axios.post(path, this.formdata)
            if (res.code === 200) {
                this.dialogFormVisible = false
                ElMessage({
                    message: '修改成功！',
                    type: 'success',
                })
                this.selectAllByPage()
            } else {
                this.dialogFormVisible = false
                ElMessage.error(res.msg)
            }
        },
        save() {
            if (this.formdata.id == -1) {
                this.addActivity()
            }
            else {
                this.editActivity()
                this.formdata = {}
                this.formdata.id = -1
                this.formdateCopy.activityType = {}
                this.formdateCopy.director = {}
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
