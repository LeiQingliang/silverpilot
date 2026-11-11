## 一分钟了解 SilverPilot

SilverPilot是面向社区养老业务的Agent原型，探索如何把自然语言连接到真实的活动、服务和助餐流程。它将模型规划、后端业务执行和用户确认分开，让查询结果可核验、写入动作可控制、执行过程可追踪。

**已具备：**公网演示、2026-11-10的真实DeepSeek文字及只读业务查询验收记录、13个Web业务/知识工具、5类待确认写入设计、运行审计、知识有效期治理和问题修复复测机制。

**待验证：**真实养老机构的需求优先级、使用效果、试点付费意愿和业务收益。公开演示部署不等同于机构生产落地。

[体验公开应用](https://silverpilot.up.railway.app/login) · [观看完整演示](https://leiqingliang.github.io/silverpilot/) · [查看源码与验证](https://github.com/LeiQingliang/silverpilot)

### 业务问题与产品决策

| 待验证的业务问题 | 已实现的产品回应 | 可检查的证据 |
| --- | --- | --- |
| 用户需要理解多个菜单、表单和业务状态 | 用自然语言查询真实业务工具，并返回执行轨迹 | [架构与工具边界](https://github.com/LeiQingliang/silverpilot/blob/main/docs/ARCHITECTURE.md) |
| 自动预约可能误操作、重复执行或越权 | 写操作先生成待确认动作，由后端校验、执行和审计 | [产品案例](https://github.com/LeiQingliang/silverpilot/blob/main/portfolio/AI_AGENT_CASE_STUDY.md) |
| AI问题不易定位，知识可能失效 | 区分运行与业务结果，记录问题流转，隔离过期知识 | [运营流程](https://github.com/LeiQingliang/silverpilot/blob/main/docs/AI_OPERATIONS_WORKFLOW.md) |

### 按岗位查看证据

- **AI产品运营：**查看指标定义、失败分类、知识维护和修复复测，重点理解为什么运行成功率不等于用户任务完成率。[运营案例](https://github.com/LeiQingliang/silverpilot/blob/main/portfolio/MEITUAN_AI_OPERATIONS_CASE.md)
- **AI产品经理：**查看场景选择、模型与业务系统的分工，以及确认、取消、超时和权限边界。[方案设计](https://github.com/LeiQingliang/silverpilot/blob/main/portfolio/AI_AGENT_CASE_STUDY.md)
- **客户经理／AI销售：**查看需求发现、方案演示、客户异议和试点验收方法；所有客户情境与ROI均为模拟。[售前演示](https://github.com/LeiQingliang/silverpilot/blob/main/portfolio/AI_SALES_DEMO_PLAYBOOK.md)

### 演示时如何理解结果

公网共享账号仅用于只读浏览、查询和受限AI体验，不能报名、预约或确认写入。完整写入链路应在授权测试环境用合成数据验证。模型和托管均有额度限制，完整录像可作为备用材料。

历史测试、截图和评测均对应具体版本与环境；16/16契约通过不代表回答准确率。多模型适配或客户端接入说明不自动代表真实账户验证，具体边界以各验证记录为准。
