<template>
    <el-dialog
        width="75%"
        v-model="addDialogVisible"
        title="新增体检报告"
        :before-close="handleClose"
        draggable
        center
        align-center
        append-to-body
        :z-index="3000"
        :lock-scroll="true"
        :close-on-click-modal="false"
    >
        <el-form :model="addData" label-position="right" label-width="auto">
            <el-row>
                <el-col :span="12">
                    <el-form-item label="姓名">
                        <el-select v-model="addData.uId" placeholder="请选择体检人员">
                            <el-option v-for="user in userData" :key="user.id" :label="user.name" :value="user.id" />
                        </el-select>
                    </el-form-item>
                </el-col>
            </el-row>
            <el-row class="row-bg" justify="space-around" :gutter="50">
                <el-col :span="8">
                    <el-form-item label="身高">
                        <el-input v-model="addData.height" autocomplete="off">
                            <template #append>cm</template></el-input>
                    </el-form-item>
                </el-col>
                <el-col :span="8">
                    <el-form-item label="体重">
                        <el-input v-model="addData.weight" autocomplete="off">
                            <template #append>kg</template></el-input>
                    </el-form-item>
                </el-col>
                <el-col :span="8">
                    <el-form-item label="血压">
                        <el-input v-model="addData.bp" autocomplete="off">
                            <template #append>mmHg</template></el-input>
                    </el-form-item>
                </el-col>
            </el-row>
            <el-row class="row-bg" justify="space-around" :gutter="50">
                <el-col :span="8">
                    <el-form-item label="*白细胞计数(*WBC)">
                        <el-input v-model="addData.wbc" autocomplete="off" placeholder="3.5-9.5">
                            <template #append>10^9/L</template></el-input>
                    </el-form-item>
                </el-col>
                <el-col :span="8">
                    <el-form-item label="淋巴细胞百分率(LY%)">
                        <el-input v-model="addData.lym" autocomplete="off" placeholder="20-50">
                            <template #append>%</template></el-input>
                    </el-form-item>
                </el-col>
                <el-col :span="8">
                    <el-form-item label="中性粒细胞比率(NE%)">
                        <el-input v-model="addData.neut" autocomplete="off" placeholder="40.0-75.0">
                            <template #append>%</template></el-input>
                    </el-form-item>
                </el-col>
            </el-row>
            <el-row class="row-bg" justify="space-around" :gutter="50">
                <el-col :span="8">
                    <el-form-item label="*红细胞计数(*RBC)">
                        <el-input v-model="addData.rbc" autocomplete="off" placeholder="4.3-5.8">
                            <template #append>10^12/L</template></el-input>
                    </el-form-item>
                </el-col>
                <el-col :span="8">
                    <el-form-item label="*血红蛋白(*GHB)">
                        <el-input v-model="addData.hgb" autocomplete="off" placeholder="130-175">
                            <template #append>g/L</template></el-input>
                    </el-form-item>
                </el-col>
                <el-col :span="8">
                    <el-form-item label="*血小板计数(*PLT)">
                        <el-input v-model="addData.plt" autocomplete="off" placeholder="125-350">
                            <template #append>10^9/L</template></el-input>
                    </el-form-item>
                </el-col>
            </el-row>
            <!-- 血脂四项 -->
            <el-row class="row-bg" justify="space-around" :gutter="50">
                <el-col :span="8">
                    <el-form-item label="总胆固醇">
                        <el-input v-model="addData.tcho" autocomplete="off" placeholder="3.1-5.7">
                            <template #append>mmol/L</template></el-input>
                    </el-form-item>
                </el-col>
                <el-col :span="8">
                    <el-form-item label="甘油三酯">
                        <el-input v-model="addData.tg" autocomplete="off" placeholder="0.56-1.71">
                            <template #append>mmol/L</template></el-input>
                    </el-form-item>
                </el-col>
            </el-row>
            <el-row class="row-bg" justify="space-around" :gutter="50">
                <el-col :span="8">
                    <el-form-item label="*高密度脂蛋白胆固醇">
                        <el-input v-model="addData.hdlc" autocomplete="off" placeholder="0.78-2">
                            <template #append>mmol/L</template></el-input>
                    </el-form-item>
                </el-col>
                <el-col :span="8">
                    <el-form-item label="低密度脂蛋白胆固醇">
                        <el-input v-model="addData.ldlc" autocomplete="off" placeholder="0-0.37">
                            <template #append>mmol/L</template></el-input>
                    </el-form-item>
                </el-col>
            </el-row>
            <!-- 肝功三项 -->
            <el-row class="row-bg" justify="space-around" :gutter="50">
                <el-col :span="8">
                    <el-form-item label="谷丙转氨酶">
                        <el-input v-model="addData.alt" autocomplete="off" placeholder="0-40">
                            <template #append>U/L</template></el-input>
                    </el-form-item>
                </el-col>
                <el-col :span="8">
                    <el-form-item label="谷草转氨酶">
                        <el-input v-model="addData.ast" autocomplete="off" placeholder="0-40">
                            <template #append>U/L</template></el-input>
                    </el-form-item>
                </el-col>
                <el-col :span="8">
                    <el-form-item label="血清胆红素">
                        <el-input v-model="addData.sb" autocomplete="off" placeholder="0-20">
                            <template #append>umol/L</template></el-input>
                    </el-form-item>
                </el-col>
            </el-row>
            <!-- 肾功三项 -->
            <el-row class="row-bg" justify="space-around" :gutter="50">
                <el-col :span="8">
                    <el-form-item label="血尿酸">
                        <el-input v-model="addData.ua" autocomplete="off" placeholder="男性210-420，女性150-350">
                            <template #append>μmol/L</template></el-input>
                    </el-form-item>
                </el-col>
                <el-col :span="8">
                    <el-form-item label="血肌酐">
                        <el-input v-model="addData.scr" autocomplete="off" placeholder="男性62-115，女性35-97">
                            <template #append>μmol/L</template></el-input>
                    </el-form-item>
                </el-col>
                <el-col :span="8">
                    <el-form-item label="尿素氮">
                        <el-input v-model="addData.bun" autocomplete="off" placeholder="1.75-8.05">
                            <template #append>mmol/L</template></el-input>
                    </el-form-item>
                </el-col>
            </el-row>
            <!-- 尿常规 -->
            <el-row class="row-bg" justify="space-around" :gutter="50">
                <el-col :span="8">
                    <el-form-item label="尿葡萄糖">
                        <el-input v-model="addData.glu" autocomplete="off" placeholder="参考值：阴">
                            <template #append>mmol/L</template></el-input>
                    </el-form-item>
                </el-col>
                <el-col :span="8">
                    <el-form-item label="尿比重">
                        <el-input v-model="addData.sg" autocomplete="off">
                        </el-input>
                    </el-form-item>
                </el-col>
                <el-col :span="8">
                    <el-form-item label="尿酸碱度">
                        <el-input v-model="addData.ph" autocomplete="off" placeholder="参考值：5-7">
                        </el-input>
                    </el-form-item>
                </el-col>
            </el-row>
            <el-row class="row-bg" justify="space-around" :gutter="50">
                <el-col :span="8">
                    <el-form-item label="尿胆原">
                        <el-input v-model="addData.uro" autocomplete="off" placeholder="参考值：阴">
                        </el-input>
                    </el-form-item>
                </el-col>
                <el-col :span="8">
                    <el-form-item label="尿酮体">
                        <el-input v-model="addData.ket" autocomplete="off" placeholder="参考值：阴">
                            <template #append>mg/L</template></el-input>
                    </el-form-item>
                </el-col>
                <el-col :span="8">
                    <el-form-item label="尿胆红素">
                        <el-input v-model="addData.bil" autocomplete="off" placeholder="参考值：阴">
                            <template #append>umol/L</template></el-input>
                    </el-form-item>
                </el-col>
            </el-row>
            <el-row class="row-bg" justify="space-around" :gutter="50">
                <el-col :span="8">
                    <el-form-item label="甲胎蛋白">
                        <el-input v-model="addData.afp" autocomplete="off" placeholder="<7.0">
                            <template #append>ng/ml</template></el-input>
                    </el-form-item>
                </el-col>
                <el-col :span="8">
                    <el-form-item label="癌胚抗原">
                        <el-input v-model="addData.cea" autocomplete="off" placeholder="<5.0">
                            <template #append>μg/L</template></el-input>
                    </el-form-item>
                </el-col>
                <el-col :span="8">
                    <el-form-item label="铁蛋白">
                        <el-input v-model="addData.ferritin" autocomplete="off" placeholder="男性15-200，女性12-150">
                            <template #append>µg/L</template></el-input>
                    </el-form-item>
                </el-col>
            </el-row>
        </el-form>
        <template #footer>
            <span class="dialog-footer">
                <el-button @click="cancelDialog">取消</el-button>
                <el-button type="primary" @click="toSave" :icon="Promotion"> 保存 </el-button>
            </span>
        </template>
    </el-dialog>
</template>

<script setup>
import { storeToRefs } from "pinia";
import { useReportStore } from "../../../stores/report.js";
const reportStore = useReportStore()
const { addDialogVisible, addData } = storeToRefs(reportStore)

import { useUserStore } from "../../../stores/user.js";
const userStore = useUserStore()
const { userData } = storeToRefs(userStore)
userStore.getAllUser()

const toSave = () => {
    reportStore.addReport()
}

const cancelDialog = () => {
    reportStore.addDialogVisible = false
    reportStore.addData = {}
}

const handleClose = () => {
    reportStore.addDialogVisible = false
    reportStore.addData = {}
}
</script>

<style scoped>
.el-col {
    border-radius: 4px;
}
</style>