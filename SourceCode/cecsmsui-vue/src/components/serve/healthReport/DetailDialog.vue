<template>
  <el-dialog
    width="75%"
    v-model="dialogVisible"
    :title="dialogTitle"
    :before-close="handleClose"
    draggable
    center
    align-center
    append-to-body
    :z-index="3000"
    :lock-scroll="true"
    :close-on-click-modal="false"
  >
    <el-form :model="formdata" label-position="right" label-width="auto">
      <!-- 姓名（不可编辑） -->
      <el-row>
        <el-col :span="12">
          <el-form-item label="姓名">
            <el-input v-model="name" autocomplete="off" disabled />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 身高、体重、血压 -->
      <el-row class="row-bg" justify="space-around" :gutter="50">
        <el-col :span="8">
          <el-form-item label="身高">
            <el-input v-model="formdata.height" autocomplete="off" :disabled="isDefault">
              <template #append>cm</template>
            </el-input>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="体重">
            <el-input v-model="formdata.weight" autocomplete="off" :disabled="isDefault">
              <template #append>kg</template>
            </el-input>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="血压">
            <el-input v-model="formdata.bp" autocomplete="off" :disabled="isDefault">
              <template #append>mmHg</template>
            </el-input>
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 血常规 -->
      <el-row class="row-bg" justify="space-around" :gutter="50">
        <el-col :span="8">
          <el-form-item label="*白细胞计数(*WBC)">
            <el-input v-model="formdata.wbc" autocomplete="off" :disabled="isDefault">
              <template #append>10^9/L</template>
            </el-input>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="淋巴细胞百分率(LY%)">
            <el-input v-model="formdata.lym" autocomplete="off" :disabled="isDefault">
              <template #append>%</template>
            </el-input>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="中性粒细胞比率(NE%)">
            <el-input v-model="formdata.neut" autocomplete="off" :disabled="isDefault">
              <template #append>%</template>
            </el-input>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row class="row-bg" justify="space-around" :gutter="50">
        <el-col :span="8">
          <el-form-item label="*红细胞计数(*RBC)">
            <el-input v-model="formdata.rbc" autocomplete="off" :disabled="isDefault">
              <template #append>10^12/L</template>
            </el-input>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="*血红蛋白(*GHB)">
            <el-input v-model="formdata.hgb" autocomplete="off" :disabled="isDefault">
              <template #append>g/L</template>
            </el-input>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="*血小板计数(*PLT)">
            <el-input v-model="formdata.plt" autocomplete="off" :disabled="isDefault">
              <template #append>10^9/L</template>
            </el-input>
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 血脂四项 -->
      <el-row class="row-bg" justify="space-around" :gutter="50">
        <el-col :span="8">
          <el-form-item label="总胆固醇">
            <el-input v-model="formdata.tcho" autocomplete="off" :disabled="isDefault">
              <template #append>mmol/L</template>
            </el-input>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="甘油三酯">
            <el-input v-model="formdata.tg" autocomplete="off" :disabled="isDefault">
              <template #append>mmol/L</template>
            </el-input>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="*高密度脂蛋白胆固醇">
            <el-input v-model="formdata.hdlc" autocomplete="off" :disabled="isDefault">
              <template #append>mmol/L</template>
            </el-input>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row class="row-bg" justify="space-around" :gutter="50">
        <el-col :span="8">
          <el-form-item label="低密度脂蛋白胆固醇">
            <el-input v-model="formdata.ldlc" autocomplete="off" :disabled="isDefault">
              <template #append>mmol/L</template>
            </el-input>
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 肝功三项 -->
      <el-row class="row-bg" justify="space-around" :gutter="50">
        <el-col :span="8">
          <el-form-item label="谷丙转氨酶">
            <el-input v-model="formdata.alt" autocomplete="off" :disabled="isDefault">
              <template #append>U/L</template>
            </el-input>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="谷草转氨酶">
            <el-input v-model="formdata.ast" autocomplete="off" :disabled="isDefault">
              <template #append>U/L</template>
            </el-input>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="血清胆红素">
            <el-input v-model="formdata.sb" autocomplete="off" :disabled="isDefault">
              <template #append>umol/L</template>
            </el-input>
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 肾功三项 -->
      <el-row class="row-bg" justify="space-around" :gutter="50">
        <el-col :span="8">
          <el-form-item label="血尿酸">
            <el-input v-model="formdata.ua" autocomplete="off" :disabled="isDefault">
              <template #append>μmol/L</template>
            </el-input>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="血肌酐">
            <el-input v-model="formdata.scr" autocomplete="off" :disabled="isDefault">
              <template #append>μmol/L</template>
            </el-input>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="尿素氮">
            <el-input v-model="formdata.bun" autocomplete="off" :disabled="isDefault">
              <template #append>mmol/L</template>
            </el-input>
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 尿常规（全部放开） -->
      <el-row class="row-bg" justify="space-around" :gutter="50">
        <el-col :span="8">
          <el-form-item label="尿葡萄糖">
            <el-input v-model="formdata.glu" autocomplete="off" :disabled="isDefault">
              <template #append>mmol/L</template>
            </el-input>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="尿比重">
            <el-input v-model="formdata.sg" autocomplete="off" :disabled="isDefault" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="尿酸碱度">
            <el-input v-model="formdata.ph" autocomplete="off" :disabled="isDefault" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row class="row-bg" justify="space-around" :gutter="50">
        <el-col :span="8">
          <el-form-item label="尿胆原">
            <el-input v-model="formdata.uro" autocomplete="off" :disabled="isDefault" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="尿酮体">
            <el-input v-model="formdata.ket" autocomplete="off" :disabled="isDefault">
              <template #append>mg/L</template>
            </el-input>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="尿胆红素">
            <el-input v-model="formdata.bil" autocomplete="off" :disabled="isDefault">
              <template #append>umol/L</template>
            </el-input>
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 肿瘤标志物 -->
      <el-row class="row-bg" justify="space-around" :gutter="50">
        <el-col :span="8">
          <el-form-item label="甲胎蛋白">
            <el-input v-model="formdata.afp" autocomplete="off" :disabled="isDefault">
              <template #append>ng/ml</template>
            </el-input>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="癌胚抗原">
            <el-input v-model="formdata.cea" autocomplete="off" :disabled="isDefault">
              <template #append>μg/L</template>
            </el-input>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="铁蛋白">
            <el-input v-model="formdata.ferritin" autocomplete="off" :disabled="isDefault">
              <template #append>µg/L</template>
            </el-input>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <template #footer>
      <span class="dialog-footer">
        <el-button @click="canelDialog">取消</el-button>
        <el-button type="primary" @click="toEdit" :icon="Promotion" v-if="isDefault">编辑</el-button>
        <el-button type="primary" @click="toSave" :icon="Promotion" v-else>保存</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import { storeToRefs } from "pinia";
import { useReportStore } from "../../../stores/report.js";
import { Promotion } from '@element-plus/icons-vue'

const reportStore = useReportStore()
const { dialogVisible, dialogTitle, formdata, name } = storeToRefs(reportStore)

const isDefault = ref(true)

const toEdit = () => {
  isDefault.value = false
}

const toSave = () => {
  reportStore.updateReport()
  isDefault.value = true
}

const canelDialog = () => {
  reportStore.dialogVisible = false
  reportStore.formdata = {}
  isDefault.value = true
}

const handleClose = () => {
  canelDialog()
}
</script>

<style scoped>
.el-col {
  border-radius: 4px;
}
</style>