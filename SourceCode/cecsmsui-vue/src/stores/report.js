import { defineStore } from 'pinia'
import $axios from '../utils/axios'
import { ElMessage } from 'element-plus'

export const useReportStore = defineStore('report', {
    state: () => {
        return {
            addDialogVisible: false,
            addData: {},
            formdata: {
            },
            user: {},
            name: '',
            reportData: [],
            dialogVisible: false,
            dialogTitle: '',
        }
    },
    actions: {
        pre4addReport() {
            this.addDialogVisible = true
            this.addData.dId = JSON.parse(sessionStorage.getItem('user')).id
        },
        async addReport() {
            var now = new Date()
            var year = now.getFullYear()
            var month = now.getMonth() + 1 < 10 ?
                '0' + (now.getMonth() + 1) : now.getMonth() + 1
            var day = now.getDate() < 10 ? '0' + now.getDate() : now.getDate()
            var hour = now.getHours() < 10 ? '0' + now.getHours() : now.getHours()
            var minute = now.getMinutes() < 10 ? '0' + now.getMinutes() : now.getMinutes()
            var second = now.getSeconds() < 10 ? '0' + now.getSeconds() : now.getSeconds()
            var time = year + '-' + month + '-' + day + " " + hour + ":" + minute + ":" + second
            this.addData.time = time
            const path = '/report/insert'
            const { data: res } = await $axios.put(path, this.addData)
            if (res.code === 200) {
                this.addDialogVisible = false
                ElMessage({
                    message: '添加成功！',
                    type: 'success',
                })
                window.location.reload();
            }
            else {
                this.addDialogVisible = false
                ElMessage.error(res.msg)
            }
        },
        pre4Detail(row) {
            this.dialogVisible = true
            this.dialogTitle = '查看体检报告'
            this.formdata = JSON.parse(JSON.stringify(row))
            this.user = JSON.parse(JSON.stringify(row)).user
            this.name = this.user.name
        },
        async updateReport() {
            const path = '/report/update'
            const { data: res } = await $axios.post(path, this.formdata)
            if (res.code === 200) {
                ElMessage({
                    message: '修改成功！',
                    type: 'success',
                })
            }
            else {
                ElMessage.error(res.msg)
            }
        },
        async selectReport(uId) {
            const path = '/report/selectByuId/' + uId
            const { data: res } = await $axios.get(path)
            if (res.code === 200 || res.code === 100) {
                this.reportData = res.result
            }
        },
    }
})
