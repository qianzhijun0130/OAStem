package com.kellen.oa_system.workflow.bootstrap;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kellen.oa_system.workflow.dto.FlowDefinitionImportRequest;
import com.kellen.oa_system.workflow.entity.FlowDefinition;
import com.kellen.oa_system.workflow.mapper.FlowDefinitionMapper;
import com.kellen.oa_system.workflow.service.FlowDefinitionService;
import com.kellen.oa_system.workflow.util.BpmnXmlParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;

/**
 * 内置流程播种器：应用启动时扫描 {@value #LOCATION_PATTERN}，
 * 把尚未登记的内置 BPMN 注册成流程模板（入库 + 部署到 Flowable）。
 *
 * <p>为什么不直接用 Flowable 的资源自动部署：Flowable 的自动部署只把流程写进自己的
 * {@code ACT_RE_PROCDEF}，不会写业务表 {@code oa_flow_definition}，前端模板列表、动态表单、
 * 节点审批人都依赖业务表，因此这里统一由本 Runner 完成「注册业务元数据 + 部署引擎」。
 * 相应地，application.properties 里关闭了 {@code flowable.check-process-definitions}，
 * 避免同一份文件被部署两次。</p>
 *
 * <p>播种策略：按 flowCode 幂等——已存在则跳过，绝不覆盖用户在设计器里的改动；
 * 单个文件失败只告警，不影响应用启动。注意：内置流程若被删除，重启后会重新播种。</p>
 */
@Slf4j
@Component
@Order(FlowBootstrapRunner.ORDER)
@RequiredArgsConstructor
public class FlowBootstrapRunner implements ApplicationRunner {

    /** 固定顺序：必须先于 {@link FlowKeyConsistencyChecker}，否则本次播种的模板逃过体检 */
    public static final int ORDER = 100;

    private static final String LOCATION_PATTERN = "classpath*:/processes/**/*.bpmn20.xml";
    private static final String BUILTIN_CATEGORY = "内置流程";

    private final FlowDefinitionMapper flowDefinitionMapper;
    private final FlowDefinitionService flowDefinitionService;
    private final ResourcePatternResolver resourcePatternResolver;

    @Override
    public void run(ApplicationArguments args) {
        Resource[] resources;
        try {
            resources = resourcePatternResolver.getResources(LOCATION_PATTERN);
        } catch (IOException e) {
            log.warn("扫描内置流程文件失败：{}", e.getMessage());
            return;
        }
        if (resources.length == 0) {
            log.info("未发现内置流程文件，跳过播种");
            return;
        }
        // 固定顺序，保证播种日志可复现
        Arrays.sort(resources, Comparator.comparing(Resource::getFilename,
                Comparator.nullsLast(Comparator.naturalOrder())));

        int seeded = 0;
        for (Resource resource : resources) {
            if (seed(resource)) {
                seeded++;
            }
        }
        log.info("内置流程播种完成：文件 {} 个，本次新增模板 {} 个", resources.length, seeded);
    }

    /** @return 是否新注册了模板 */
    private boolean seed(Resource resource) {
        String filename = resource.getFilename();
        try {
            String xml = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            BpmnXmlParser.ParsedBpmn parsed = BpmnXmlParser.parse(xml);

            Long exist = flowDefinitionMapper.selectCount(new LambdaQueryWrapper<FlowDefinition>()
                    .eq(FlowDefinition::getIsDeleted, 0)
                    .eq(FlowDefinition::getFlowCode, parsed.key()));
            if (exist != null && exist > 0) {
                log.info("内置流程 {} 已存在，跳过播种", parsed.key());
                return false;
            }

            FlowDefinitionImportRequest req = new FlowDefinitionImportRequest();
            req.setBpmnXml(xml);
            req.setFlowCode(parsed.key());
            req.setFlowName(parsed.name());
            req.setCategory(BUILTIN_CATEGORY);

            Long id = flowDefinitionService.importDefinition(req);
            log.info("已注册内置流程模板 {}（{}，id={}）", parsed.name(), parsed.key(), id);
            return true;
        } catch (Exception e) {
            log.warn("内置流程文件 {} 注册失败：{}", filename, e.getMessage());
            return false;
        }
    }
}
