package com.kellen.oa_system.workflow.bootstrap;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.workflow.entity.FlowDefinition;
import com.kellen.oa_system.workflow.mapper.FlowDefinitionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 流程标识一致性自检：启动时扫描 {@code oa_flow_definition}，把「标识与编码不一致」的模板暴露到日志。
 *
 * <p>为什么需要它：部署时的归一化（{@code BpmnXmlNormalizer}）只作用于<b>新保存</b>的模板，
 * 归一化上线前创建的历史模板仍是旧的 process id（例如 {@code process_1}）。这些数据不会自己变好，
 * 而它们恰恰是「发起 A 却执行 B」的高危来源，因此放在启动阶段主动体检。</p>
 *
 * <p>只读不改：发现异常仅告警并给出处置建议（在设计器里重新保存一次即自动对齐），
 * 不擅自改动业务数据——批量改写流程标识会牵动 Flowable 部署，属于需要人工确认的操作。</p>
 *
 * <p>执行顺序排在 {@link FlowBootstrapRunner} 之后（{@link #ORDER} &gt; 播种器的顺序），
 * 否则本次启动新播种的模板不会被纳入体检。</p>
 */
@Slf4j
@Component
@Order(FlowKeyConsistencyChecker.ORDER)
@RequiredArgsConstructor
public class FlowKeyConsistencyChecker implements ApplicationRunner {

    /** 必须晚于 FlowBootstrapRunner（其顺序为 100） */
    public static final int ORDER = 200;

    private final FlowDefinitionMapper flowDefinitionMapper;

    @Override
    public void run(ApplicationArguments args) {
        List<FlowDefinition> definitions = flowDefinitionMapper.selectList(
                new LambdaQueryWrapper<FlowDefinition>()
                        .eq(FlowDefinition::getIsDeleted, 0)
                        .orderByAsc(FlowDefinition::getId));
        if (definitions.isEmpty()) {
            log.info("流程标识一致性检查：暂无流程模板，跳过");
            return;
        }

        List<String> duplicated = findDuplicatedKeys(definitions);
        List<String> mismatched = findMismatchedKeys(definitions);

        if (!duplicated.isEmpty()) {
            log.warn("检测到 {} 个流程标识被多个模板共用，发起流程可能执行到错误版本：{}",
                    duplicated.size(), duplicated);
            log.warn("处置建议：在设计器中重新保存涉及到的模板，系统会自动把流程标识对齐为流程编码");
        }
        if (!mismatched.isEmpty()) {
            log.warn("以下模板的流程标识与流程编码不一致，重新保存一次即可自动对齐：{}", mismatched);
        }
        if (duplicated.isEmpty() && mismatched.isEmpty()) {
            log.info("流程标识一致性检查通过：{} 个模板，标识与编码一一对应", definitions.size());
        }
    }

    /** 同一个 bpmn_key 被多个未删除模板使用（「选 A 跑 B」的直接成因） */
    private List<String> findDuplicatedKeys(List<FlowDefinition> definitions) {
        Map<String, List<FlowDefinition>> grouped = definitions.stream()
                .filter(fd -> StringUtils.hasText(fd.getBpmnKey()))
                .collect(Collectors.groupingBy(FlowDefinition::getBpmnKey));
        return grouped.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> entry.getKey() + " ← " + entry.getValue().stream()
                        .map(FlowDefinition::getFlowCode)
                        .collect(Collectors.joining(", ")))
                .toList();
    }

    /** bpmn_key 与 flow_code 不一致（归一化上线前的历史数据） */
    private List<String> findMismatchedKeys(List<FlowDefinition> definitions) {
        return definitions.stream()
                .filter(fd -> StringUtils.hasText(fd.getBpmnKey()) && !fd.getBpmnKey().equals(fd.getFlowCode()))
                .map(fd -> fd.getFlowCode() + "（标识=" + fd.getBpmnKey() + "）")
                .toList();
    }
}
