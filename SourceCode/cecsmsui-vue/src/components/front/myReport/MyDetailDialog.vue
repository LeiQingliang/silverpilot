<template>
    <el-dialog width="75%" v-model="dialogVisible" :title="dialogTitle" :before-close="handleClose" draggable overflow
        center align-center>
        <el-form :model="formdata" label-position="right" label-width="auto">
            <el-row>
                <el-col :span="12">
                    <el-form-item label="姓名">
                        <el-input v-model="name" autocomplete="off" disabled />
                    </el-form-item>
                </el-col>
            </el-row>
            <el-row class="row-bg" justify="space-around" :gutter="50">
                <el-col :span="8">
                    <el-form-item label="身高">
                        <el-input v-model="formdata.height" autocomplete="off" readonly>
                            <template #append>cm</template></el-input>
                    </el-form-item>
                </el-col>
                <el-col :span="8">
                    <el-form-item label="体重">
                        <el-input v-model="formdata.weight" autocomplete="off" readonly>
                            <template #append>kg</template></el-input>
                    </el-form-item>
                </el-col>
                <el-col :span="8">
                    <el-form-item label="血压">
                        <el-input v-model="formdata.bp" autocomplete="off" readonly>
                            <template #append>mmHg</template></el-input>
                    </el-form-item>
                </el-col>
            </el-row>
            <el-row class="row-bg" justify="space-around" :gutter="50">
                <el-col :span="8">
                    <el-form-item label="*白细胞计数(*WBC)">
                        <el-input v-model="formdata.wbc" autocomplete="off" readonly>
                            <template #append>10^9/L</template></el-input>
                    </el-form-item>
                </el-col>
                <el-col :span="8">
                    <el-form-item label="淋巴细胞百分率(LY%)">
                        <el-input v-model="formdata.lym" autocomplete="off" readonly>
                            <template #append>%</template></el-input>
                    </el-form-item>
                </el-col>
                <el-col :span="8">
                    <el-form-item label="中性粒细胞比率(NE%)">
                        <el-input v-model="formdata.neut" autocomplete="off" readonly>
                            <template #append>%</template></el-input>
                    </el-form-item>
                </el-col>
            </el-row>
            <el-row class="row-bg" justify="space-around" :gutter="50">
                <el-col :span="8">
                    <el-form-item label="*红细胞计数(*RBC)">
                        <el-input v-model="formdata.rbc" autocomplete="off" readonly>
                            <template #append>10^12/L</template></el-input>
                    </el-form-item>
                </el-col>
                <el-col :span="8">
                    <el-form-item label="*血红蛋白(*GHB)">
                        <el-input v-model="formdata.hgb" autocomplete="off" readonly>
                            <template #append>g/L</template></el-input>
                    </el-form-item>
                </el-col>
                <el-col :span="8">
                    <el-form-item label="*血小板计数(*PLT)">
                        <el-input v-model="formdata.plt" autocomplete="off" readonly>
                            <template #append>10^9/L</template></el-input>
                    </el-form-item>
                </el-col>
            </el-row>
            <!-- 血脂四项 -->
            <el-row class="row-bg" justify="space-around" :gutter="50">
                <el-col :span="8">
                    <el-form-item label="总胆固醇">
                        <el-input v-model="formdata.tcho" autocomplete="off" readonly>
                            <template #append>mmol/L</template></el-input>
                    </el-form-item>
                </el-col>
                <el-col :span="8">
                    <el-form-item label="甘油三酯">
                        <el-input v-model="formdata.tg" autocomplete="off" readonly>
                            <template #append>mmol/L</template></el-input>
                    </el-form-item>
                </el-col>
            </el-row>
            <el-row class="row-bg" justify="space-around" :gutter="50">
                <el-col :span="8">
                    <el-form-item label="*高密度脂蛋白胆固醇">
                        <el-input v-model="formdata.hdlc" autocomplete="off" readonly>
                            <template #append>mmol/L</template></el-input>
                    </el-form-item>
                </el-col>
                <el-col :span="8">
                    <el-form-item label="低密度脂蛋白胆固醇">
                        <el-input v-model="formdata.ldlc" autocomplete="off" readonly>
                            <template #append>mmol/L</template></el-input>
                    </el-form-item>
                </el-col>
            </el-row>
            <!-- 肝功三项 -->
            <el-row class="row-bg" justify="space-around" :gutter="50">
                <el-col :span="8">
                    <el-form-item label="谷丙转氨酶">
                        <el-input v-model="formdata.alt" autocomplete="off" readonly>
                            <template #append>U/L</template></el-input>
                    </el-form-item>
                </el-col>
                <el-col :span="8">
                    <el-form-item label="谷草转氨酶">
                        <el-input v-model="formdata.ast" autocomplete="off" readonly>
                            <template #append>U/L</template></el-input>
                    </el-form-item>
                </el-col>
                <el-col :span="8">
                    <el-form-item label="血清胆红素">
                        <el-input v-model="formdata.sb" autocomplete="off" readonly>
                            <template #append>umol/L</template></el-input>
                    </el-form-item>
                </el-col>
            </el-row>
            <!-- 肾功三项 -->
            <el-row class="row-bg" justify="space-around" :gutter="50">
                <el-col :span="8">
                    <el-form-item label="血尿酸">
                        <el-input v-model="formdata.ua" autocomplete="off" readonly>
                            <template #append>μmol/L</template></el-input>
                    </el-form-item>
                </el-col>
                <el-col :span="8">
                    <el-form-item label="血肌酐">
                        <el-input v-model="formdata.scr" autocomplete="off" readonly>
                            <template #append>μmol/L</template></el-input>
                    </el-form-item>
                </el-col>
                <el-col :span="8">
                    <el-form-item label="尿素氮">
                        <el-input v-model="formdata.bun" autocomplete="off" readonly>
                            <template #append>mmol/L</template></el-input>
                    </el-form-item>
                </el-col>
            </el-row>
            <!-- 尿常规 -->
            <el-row class="row-bg" justify="space-around" :gutter="50">
                <el-col :span="8">
                    <el-form-item label="尿葡萄糖">
                        <el-input v-model="formdata.glu" autocomplete="off" readonly>
                            <template #append>mmol/L</template></el-input>
                    </el-form-item>
                </el-col>
                <el-col :span="8">
                    <el-form-item label="尿比重">
                        <el-input v-model="formdata.sg" autocomplete="off" readonly />
                    </el-form-item>
                </el-col>
                <el-col :span="8">
                    <el-form-item label="尿酸碱度">
                        <el-input v-model="formdata.ph" autocomplete="off" readonly />
                    </el-form-item>
                </el-col>
            </el-row>
            <el-row class="row-bg" justify="space-around" :gutter="50">
                <el-col :span="8">
                    <el-form-item label="尿胆原">
                        <el-input v-model="formdata.uro" autocomplete="off" readonly>
                        </el-input>
                    </el-form-item>
                </el-col>
                <el-col :span="8">
                    <el-form-item label="尿酮体">
                        <el-input v-model="formdata.ket" autocomplete="off" readonly>
                            <template #append>mg/L</template></el-input>
                    </el-form-item>
                </el-col>
                <el-col :span="8">
                    <el-form-item label="尿胆红素">
                        <el-input v-model="formdata.bil" autocomplete="off" readonly>
                            <template #append>umol/L</template></el-input>
                    </el-form-item>
                </el-col>
            </el-row>
            <el-row class="row-bg" justify="space-around" :gutter="50">
                <el-col :span="8">
                    <el-form-item label="甲胎蛋白">
                        <el-input v-model="formdata.afp" autocomplete="off" readonly>
                            <template #append>ng/ml</template></el-input>
                    </el-form-item>
                </el-col>
                <el-col :span="8">
                    <el-form-item label="癌胚抗原">
                        <el-input v-model="formdata.cea" autocomplete="off" readonly>
                            <template #append>μg/L</template></el-input>
                    </el-form-item>
                </el-col>
                <el-col :span="8">
                    <el-form-item label="铁蛋白">
                        <el-input v-model="formdata.ferritin" autocomplete="off" readonly>
                            <template #append>µg/L</template></el-input>
                    </el-form-item>
                </el-col>
            </el-row>
        </el-form>
    </el-dialog>
</template>

<script setup>
import { storeToRefs } from "pinia";
import { useReportStore } from "../../../stores/report.js";
const reportStore = useReportStore()
const { dialogVisible, dialogTitle, formdata, name } = storeToRefs(reportStore)

const handleClose = () => {
    reportStore.dialogVisible = false
    reportStore.formdata = {}
}

</script>

<style scoped>
.el-col {
    border-radius: 4px;
}
</style>
