import { defineStore } from 'pinia'
import $axios from '../utils/axios'
import { ElMessage } from 'element-plus'

export const useServiceorderStore = defineStore('serviceorder', {
    state: () => {
        return {
            index: -1,
            currentPage: 1,
            pageSize: 8,
            total: 0,
            background: true,
            dialogFormVisible: false,
            tableData: [],
            formData: {},
            updateFrom: {
            },
            myOrder: [],
            noAcceptData: [],
            noFinishData: [],
            noReviewData: [],
        }
    },
    actions: {
        async init() {//ServiceOrder
            const path = '/serviceOrder/selectServiceByPage/' + this.currentPage + '/' + this.pageSize
            const { data: res } = await $axios.get(path)
            if (res.code === 200) {
                this.tableData = res.result
                this.total = Math.max(0, Number(res.msg) || 0)
            }
        },
        async initHealth() {//HealthOrder
            const path = '/serviceOrder/selectHealthByPage/' + this.currentPage + '/' + this.pageSize
            const { data: res } = await $axios.get(path)
            if (res.code === 200) {
                this.tableData = res.result
                this.total = Math.max(0, Number(res.msg) || 0)
            }
        },
        preInfo4ServiceEdit(index, row) {
            this.index = index
            this.updateFrom.id = row.id
            const mId = JSON.parse(sessionStorage.getItem("user")).id
            this.updateFrom.mId = mId
            this.dialogFormVisible = true
        },
        preInfo4HealthEdit(index, row) {
            this.index = index
            this.updateFrom.id = row.id
            const dId = JSON.parse(sessionStorage.getItem("user")).id
            this.updateFrom.dId = dId
            this.dialogFormVisible = true
        },
        async updateOrder() {
            var now = new Date()
            var year = now.getFullYear()
            var month = now.getMonth() + 1 < 10 ?
                '0' + (now.getMonth() + 1) : now.getMonth() + 1
            var day = now.getDate() < 10 ? '0' + now.getDate() : now.getDate()
            var hour = now.getHours() < 10 ? '0' + now.getHours() : now.getHours()
            var minute = now.getMinutes() < 10 ? '0' + now.getMinutes() : now.getMinutes()
            var second = now.getSeconds() < 10 ? '0' + now.getSeconds() : now.getSeconds()
            var date = year + '-' + month + '-' + day
            var time = hour + ":" + minute + ":" + second
            this.updateFrom.acceptDate = date
            this.updateFrom.acceptTime = time
            this.updateFrom.orderState = 2
            const isHealthOrder = Boolean(this.updateFrom.dId)
            const path = '/serviceOrder/update'
            const { data: res } = await $axios.post(path, this.updateFrom)
            if (res.code === 200) {
                this.dialogFormVisible = false
                this.updateFrom = {}
                if (isHealthOrder) {
                    await this.initHealth()
                } else {
                    await this.init()
                }
                ElMessage({
                    message: '已受理!',
                    type: 'success',
                })
            } else {
                ElMessage.error(res.msg || '订单受理失败，请稍后重试')
            }
        },
        async loadByUid() {//我的全部订单
            const user = JSON.parse(sessionStorage.getItem("user"))
            const path = '/serviceOrder/selectByUId/' + user.id
            const { data: res } = await $axios.get(path)
            if (res.code === 200) {
                this.myOrder = res.result
            }
        },
        async getNoAccept() {//我的待受理订单
            const user = JSON.parse(sessionStorage.getItem("user"))
            const path = '/serviceOrder/selectByuIdByState/' + user.id + '/' + 0
            const { data: res } = await $axios.get(path)
            if (res.code === 200) {
                this.noAcceptData = res.result
            }
        },
        async getNoFinish() {//我的未完成订单
            const user = JSON.parse(sessionStorage.getItem("user"))
            const path = '/serviceOrder/selectByuIdByState/' + user.id + '/' + 2
            const { data: res } = await $axios.get(path)
            if (res.code === 200) {
                this.noFinishData = res.result
            }
        },
        async getNoReview() {//我的待评价订单
            const user = JSON.parse(sessionStorage.getItem("user"))
            const path = '/serviceOrder/selectByuIdByState/' + user.id + '/' + 3
            const { data: res } = await $axios.get(path)
            if (res.code === 200) {
                this.noReviewData = res.result
            }
        },
        updateDate() {
            var now = new Date()
            var year = now.getFullYear()
            var month = now.getMonth() + 1 < 10 ?
                '0' + (now.getMonth() + 1) : now.getMonth() + 1
            var day = now.getDate() < 10 ? '0' + now.getDate() : now.getDate()
            var date = year + '-' + month + '-' + day
            return date;
        },
        updateTime() {
            var now = new Date()
            var hour = now.getHours() < 10 ? '0' + now.getHours() : now.getHours()
            var minute = now.getMinutes() < 10 ? '0' + now.getMinutes() : now.getMinutes()
            var second = now.getSeconds() < 10 ? '0' + now.getSeconds() : now.getSeconds()
            var time = hour + ":" + minute + ":" + second
            return time;
        },
        async updateState(row) {
            this.updateFrom.id = row.id
            this.updateFrom.orderState = row.orderState + 1
            const path = '/serviceOrder/update'
            const { data: res } = await $axios.post(path, this.updateFrom)
            if (res.code === 200) {
                this.loadByUid()
                this.getNoAccept()
                this.getNoFinish()
                this.getNoReview()
            } else {
                ElMessage.error(res.msg || '订单状态更新失败，请稍后重试')
            }

        }
    }
})
