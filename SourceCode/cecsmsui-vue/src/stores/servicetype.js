import { defineStore } from "pinia";
import $axios from '../utils/axios'
import { ElMessage } from 'element-plus'

export const useServicetypeStore = defineStore('servicetype', {
    state: () => {
        return {
            index: -1,
            dialogVisible: false,
            dialogTitle: '',
            treeData: [],
            fatherType: [],//服务大类
            allType: [],//所有小类,
            children: [],
            categorizedServices: {},// 分类的服务，键是服务类型ID，值是服务列表
            type: '',
            formdata: {
                id: -1,
            },
            typeB: {},
            typeS: {},
            cascaderOptions: [],// 用于存储级联选择器的选项
            selectedOptions: [],// 选中的服务类别ID数组
        }
    },
    actions: {
        //frontService:
        async selectFather() {//查找所有大类
            try {
                const path = '/serviceType/selectFather1'
                const { data: res } = await $axios.get(path)
                if (res.code === 200) {
                    this.fatherType = res.result
                    await this.fetchCategorizedServices()
                } else {
                    // 处理错误情况，比如抛出一个错误或设置一个错误状态
                    console.error('Failed to load fatherType:', res);
                }
            } catch (error) {
                // 处理网络错误或其他异常
                console.error('An error occurred:', error);
            }
        },
        async selectByFather(leaderId) {
            const path = '/serviceType/selectChildren1ByFather/' + leaderId
            const { data: res } = await $axios.get(path)
            if (res.code === 200) {
                this.children = res.result
                    return res.result
            }
        },
        async fetchCategorizedServices() {
            const fetchPromises = this.fatherType.map(async (type) => {
                try {
                    const allChildren = await this.selectByFather(type.id)
                    this.categorizedServices[type.id] = allChildren//将获取到的服务列表存储到categorizedServices中
                } catch (error) {
                    console.error('Failed to fetch activities for type:', type.id, error);
                }
            });
            // 等待所有异步操作完成
            await Promise.all(fetchPromises);
        },
        //ServiceType:
        async selectAllFather() {//查找所有大类
            try {
                const path = '/serviceType/selectAllFather'
                const { data: res } = await $axios.get(path)
                if (res.code === 200) {
                    this.fatherType = res.result
                } else {
                    // 处理错误情况，比如抛出一个错误或设置一个错误状态
                    console.error('Failed to load fatherType:', res);
                }
            } catch (error) {
                // 处理网络错误或其他异常
                console.error('An error occurred:', error);
            }
        },
        async selectAllByFather(leaderId) {
            const path = '/serviceType/selectAllChildrenByFather/' + leaderId
            const { data: res } = await $axios.get(path)
            if (res.code === 200) {
                this.children = res.result
                return res.result
            }
        },
        async fetchChildrenForParents() {
            // 假设 rawFatherTypes 已经被填充
            if (this.fatherType.length === 0) {
                return; // 如果没有父类数据，则直接返回
            }
            // 使用 Promise.all 并行处理多个请求
            const fetchChildrenPromises = this.fatherType.map(async (father) => {
                try {
                    const children = await this.selectAllByFather(father.id);
                    // 将子类别数组赋值给当前父类对象的 children 属性
                    father.children = children;
                    // 根据需要设置 hasChildren 属性
                    father.hasChildren = children.length > 0;
                } catch (error) {
                    console.error(`Failed to fetch children for father with ID ${father.id}:`, error);
                    // 如果需要，可以设置 father.children 为空数组或其他默认值
                    father.children = [];
                }
            });
            // 等待所有子类别请求完成
            await Promise.all(fetchChildrenPromises);
            // 当所有子类别都被获取后，构建完整的树形结构
            this.treeData = this.fatherType;
        },
        async selectAllChildren() {//所有小类
            const path = '/serviceType/selectAllChildren'
            const { data: res } = await $axios.get(path)
            if (res == 200) {
                this.allType = res.result
            }
        },
        pre4Order(typeBId, typeSId) {
            this.dialogVisible = true
            this.typeB = JSON.parse(JSON.stringify(typeBId))
            this.typeS = JSON.parse(JSON.stringify(typeSId))
            this.selectedOptions[0] = this.typeB
            this.selectedOptions[1] = this.typeS
        },
        // 加载所有父类别（大类）
        async loadFatherCategories() {
            try {
                const { data: res } = await $axios.get('/serviceType/selectFather1');
                this.cascaderOptions = res.result;
                // 如果需要，可以递归加载子类（这里假设不需要预先加载）
            } catch (error) {
                console.error('Error loading father categories:', error);
            }
        },
        // 根据父类别ID加载子类别（小类）
        async loadChildrenCategories(leaderId) {
            try {
                const { data: res } = await $axios.get('/serviceType/selectChildren1ByFather/' + leaderId);
                return res.result
            } catch (error) {
                console.error('Error loading children categories:', error);
                return [];// 加载失败时返回空数组
            }
        },
        async toOrder() {
            try {
                const path = '/serviceOrder/insert'
                const { data: res } = await $axios.put(path, this.formdata)
                if (res.code === 200) {
                    this.dialogVisible = false
                    ElMessage({
                        message: '已成功提交预约，等待处理',
                        type: 'success',
                    })
                    this.formdata = {}
                    this.formdata.id = -1
                } else {
                    ElMessage.error(res.msg || '预约失败，请稍后重试')
                }
            }
            catch (error) {
                ElMessage.error('预约失败，请检查网络连接')
            }
        },
        pre4EditType(index, row) {
            this.index = index
            this.dialogVisible = true
            this.dialogTitle = '编辑服务父类别'
            this.formdata = JSON.parse(JSON.stringify(row))
        },
        pre4EditChildren(index, row) {
            this.index = index
            this.dialogVisible = true
            this.dialogTitle = '编辑服务子类别'
            this.formdata = JSON.parse(JSON.stringify(row))
        },
        async editType() {
            const path = '/serviceType/update'
            const { data: res } = await $axios.post(path, this.formdata)
            if (res.code === 200) {
                this.dialogVisible = false
                ElMessage({
                    message: '修改成功！',
                    type: 'success',
                })
                window.location.reload();

            } else {
                this.dialogVisible = false
                ElMessage.error(res.msg)
            }
        },
        pre4Add() {
            this.index = -1
            this.dialogVisible = true
            this.dialogTitle = '新增服务父类别'
            this.formdata = { id: -1 }
        },
        pre4AddChildren(index, row) {
            this.index = index
            this.dialogVisible = true
            this.dialogTitle = '新增服务子类别'
            this.formdata.leaderId = row.id
        },
        async addType() {
            const path = '/serviceType/insert'
            const { data: res } = await $axios.put(path, this.formdata)
            if (res.code === 200) {
                this.dialogVisible = false
                ElMessage({
                    message: '添加成功！',
                    type: 'success',
                })
                window.location.reload();

            } else {
                this.dialogVisible = false
                ElMessage.error(res.msg)
            }
        },
        save() {
            if (this.formdata.id == -1) {
                this.addType()
            } else {
                this.editType()
            }
        }
    }
})
