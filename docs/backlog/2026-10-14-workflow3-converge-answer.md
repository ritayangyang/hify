# 任务卡：修复演示工作流 3 的 END 变量引用缺陷

- 日期：2026-10-14
- 模块：hify-workflow（数据修复，无需发版）
- 优先级：P1（火山方舟模型开通前必须修，否则三意图演示两条必失败）

## 背景

工作流 id=3「智能客服分类工作流」当前结构：

```
start → classify(LLM 分类) → router(CONDITION 三分支)
                                 ├─presale────→ presale(LLM)    ─┐
                                 ├─aftersale──→ aftersale(LLM)  ─┤
                                 └─techsupport→ techsupport(LLM)─┴→ end
```

END 节点配置为 `replyTemplate: "{{presale.answer}}"`，只引用售前节点输出。
引擎是**严格模板语义**（引用未产出变量直接 FAILED，用户已确认不改成静默保留占位符），因此：

- 售前意图：全链路成功；
- 售后 / 技术支持意图：分支 LLM 成功，但 END 渲染抛
  `模板引用了未产出的变量: presale.answer` → run FAILED。

## 方案（已与用户讨论的首选：分支收敛）

三个分支 LLM 收敛为**同一个 answer 节点**，提示词内带分类结果让模型按身份回答：

```
start → classify → router ──presale────┐
                        ├──aftersale───┼──→ answer(LLM, outputKey=answer) → end
                        └──techsupport─┘
```

- answer 节点 promptTemplate 示例：
  `你是客服团队成员，用户意图分类结果为：{{classify.intent}}。售前请介绍产品功能优势，售后请处理退换货保修，技术支持请帮用户排查问题。\n用户问题：{{start.userMessage}}`
- END `replyTemplate` 改为 `{{answer.answer}}`；
- 三条分支边都从 router 指向 answer（保留三个 sourceHandle），answer 唯一出线到 end；
- 图校验规则不变（合流节点多入边是允许的，需确认 WorkflowGraphValidator 不拦截，若拦截则放开「非 START 节点允许多入边，END 允许多入边路径」规则）。

## 验收

- [ ] 三条意图（保修/新品功能/蓝牙连接）试运行均 SUCCEEDED，输出口吻与意图匹配；
- [ ] 旧演示 JSON 备份（更新前 GET /workflows/3 存一份到本目录或 seed 目录）；
- [ ] 工作流详情接口回显新图，工作流列表页 JSON 编辑可正常回填；
- [ ] 对话页：Agent 4（绑定 workflow 3）三条意图 SSE 均收到 delta+done，assistant 消息落库；
- [ ] 现有 72 个 workflow 单测全绿（若放开了多入边校验，同步补 validator 用例）。

## 备注

- 前置条件：火山方舟至少一个模型开通（当前 modelConfig 5 的 doubao-seed 404）；
- 备选方案（不首选）：引擎模板加显式默认值语法（如 `{{x.y | ""}}`），改动面大，延后；
- 可直接调 PUT /api/v1/workflows/3 整体替换图，或工作流页 JSON 编辑提交。
