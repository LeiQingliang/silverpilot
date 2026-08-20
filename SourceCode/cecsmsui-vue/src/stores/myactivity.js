import { defineStore } from "pinia";
import $axios from '../utils/axios';
import { ElMessage } from 'element-plus';
import { saveBlobResponse } from '../utils/download.js';

export const useMyactivityStore = defineStore("myactivity", {
    state: () => {
        return {
            allData: [],
            canceledData: [],
            futureData: [],
            updateForm: {},
            UserList: [],
            dialogVisible: false,
            aId: -1,
            activityName: '',
            dialogTitle: '',
        }
    },
    actions: {
        async init() {
            const user = JSON.parse(sessionStorage.getItem("user"))
            const path = '/userActivity/selectAllByuId/' + user.id
            const { data: res } = await $axios.get(path)
            if (res.code === 200) {
                this.allData = res.result
            }
        },
        async getFutureData() {
            const user = JSON.parse(sessionStorage.getItem("user"))
            const path = '/userActivity/selectByUIdByState/' + user.id + '/' + '未开始'
            const { data: res } = await $axios.get(path)
            if (res.code === 200) {
                this.futureData = res.result
            }
        },
        async getCanceledData() {
            const user = JSON.parse(sessionStorage.getItem("user"))
            const path = '/userActivity/selectByUIdBymyState/' + user.id + '/' + '已取消报名'
            const { data: res } = await $axios.get(path)
            if (res.code === 200) {
                this.canceledData = res.result
            }
        },
        async update(row) {
            this.updateForm.id = row.id
            this.updateForm.state = '已取消报名'
            const path = '/userActivity/update'
            const { data: res } = await $axios.post(path, this.updateForm)
            if (res.code === 200) {
                this.init()
                this.getFutureData()
                this.getCanceledData()
            } else {
                ElMessage.error(res.msg || '取消报名失败，请稍后重试')
            }
        },
        pre4UserList(row) {
            this.dialogVisible = true
            this.activityName = row.activityDate + "\"" + row.activityName + "\""
            this.dialogTitle = this.activityName + "报名人员统计表"
            this.aId = row.id
            this.getUserList(row.id)
        },
        async getUserList(aId) {
            const path = '/userActivity/selectUserByaId/' + aId
            const { data: res } = await $axios.get(path)
            if (res.code === 200) {
                this.UserList = res.result
            }
            else {
                this.UserList = []
                ElMessage.error(res.msg || '报名人员列表加载失败')
            }
        },
        async download() {
            try {
                const path = '/download/excel/' + this.aId
                const response = await $axios.post(path, null, { responseType: 'blob' })

                if (response.status === 200) {
                    await saveBlobResponse(response, this.dialogTitle + '.xlsx')
                } else {
                    console.error("下载失败", response);
                    ElMessage.error('报名名单下载失败')
                }
            } catch (error) {
                console.error("下载过程中发生错误：", error);
                ElMessage.error(error.response?.data?.msg || '报名名单下载失败，请稍后重试')
            }
        }
    }
})
