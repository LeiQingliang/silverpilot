import { defineStore } from "pinia"
import $axios from '../utils/axios'
import { ElMessage } from 'element-plus'
import { useActivityStore } from "./activity"

export const useActivitytypeStore = defineStore('activitytype', {
    state: () => {
        return {
            type: '',
            addDialogVisible: false,
            tableData: [],
            state1Type: [],
            categorizedActivities: {}, // 分类的活动，键是活动类型ID，值是活动列表
            formData: {},
            form: {},
        }
    },
    actions: {
        async init() {
            const path = '/activityType/selectAll'
            const { data: res } = await $axios.get(path)
            if (res.code === 200) {
                this.tableData = res.result
            }
        },
        async loadByState() {
            try {
                const path = '/activityType/selectByState1'
                const { data: res } = await $axios.get(path)
                if (res.code === 200) {
                    this.state1Type = res.result
                    await this.fetchCategorizedActivities()
                }
                else {
                    // 处理错误情况，比如抛出一个错误或设置一个错误状态
                    console.error('Failed to load state1Type:', res);
                }
            } catch (error) {
                // 处理网络错误或其他异常
                console.error('An error occurred:', error);
            }
        },
        async fetchCategorizedActivities() {
            const activityStore = useActivityStore()

            // 遍历活动类型并获取每个类型的活动
            const fetchPromises = this.state1Type.map(async (type) => {
                try {
                    const activities = await activityStore.selectByType(type.id)
                    this.categorizedActivities[type.id] = activities
                } catch (error) {
                    console.error('Failed to fetch activities for type:', type.id, error);
                }
            })

            // 等待所有异步操作完成
            await Promise.all(fetchPromises)
        },
        async loadByName() {
            this.tableData.length = 0
            if (this.type === '') {
                this.init()
            } else {
                const path = '/activityType/selectByName/' + this.type
                const { data: res } = await $axios.get(path)
                if (res.code === 200) {
                    this.tableData = res.result
                }
            }
        },
        async updateState() {
            const path = '/activityType/update'
            const { data: res } = await $axios.post(path, this.formData)
            if (res.code !== 200) {
                ElMessage.error(res.msg || '活动分类状态更新失败')
                this.init()
            }
        },
        preInfo4Add() {
            this.addDialogVisible = true
        },
        async addType() {
            const path = '/activityType/insert'
            const { data: res } = await $axios.put(path, this.form)
            if (res.code === 200) {
                this.addDialogVisible = false
                this.init()
            }
        }
    },
})
