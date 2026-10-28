<script setup lang="ts">
import { onMounted, ref } from 'vue'
import CourseTab from './tabs/CourseTab.vue'
import SessionTab from './tabs/SessionTab.vue'
import EnrollTab from './tabs/EnrollTab.vue'
import ScoreTab from './tabs/ScoreTab.vue'
import StatTab from './tabs/StatTab.vue'
import { useTrainOptions } from './composables/useTrainOptions'
import { useTrainEnroll } from './composables/useTrainEnroll'
import './training.css'

/**
 * 培训管理：五个标签页的容器。
 *
 * <p>FE-OPT-06 拆分完成后的形态——本文件只做三件事：页签切换、承载三份共享下拉
 * （员工/课程/期次，四个弹窗与两个页签都要用，只取一次）、以及创建报名看板。</p>
 *
 * <p><b>报名看板为什么在页面创建</b>：「报名管理」和「成绩管理」是同一份期次花名册，
 * 拆成两个独立状态会导致「在报名页选了期次，切到成绩页又要重选」。所以
 * `useTrainEnroll()` 在这里实例化一次，`state` 以 props 传给两个 tab。</p>
 */
const activeTab = ref('course')

const { userCandidates, courseOptions, sessionOptions, load: loadOptions } = useTrainOptions()

/** 报名/成绩共用看板：单实例，两个页签共享选中期次与花名册 */
const enroll = useTrainEnroll()

onMounted(loadOptions)
</script>

<template>
  <div class="training">
    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="培训课程" name="course" lazy>
          <CourseTab :user-candidates="userCandidates" />
        </el-tab-pane>
        <el-tab-pane label="培训期次" name="session" lazy>
          <SessionTab :course-options="courseOptions" />
        </el-tab-pane>
        <el-tab-pane label="报名管理" name="enroll" lazy>
          <EnrollTab :enroll="enroll" :session-options="sessionOptions" :user-candidates="userCandidates" />
        </el-tab-pane>
        <el-tab-pane label="成绩管理" name="score" lazy>
          <ScoreTab :enroll="enroll" :session-options="sessionOptions" />
        </el-tab-pane>
        <el-tab-pane label="培训统计" name="stat" lazy>
          <StatTab />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>
