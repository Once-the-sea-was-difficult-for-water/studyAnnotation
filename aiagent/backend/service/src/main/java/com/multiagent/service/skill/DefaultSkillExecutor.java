package com.multiagent.service.skill;

import com.multiagent.adapter.AgentAdapter;
import com.multiagent.adapter.AgentRegistry;
import com.multiagent.infrastructure.model.Branch;
import com.multiagent.infrastructure.model.SharedContext;
import com.multiagent.infrastructure.model.Skill;
import com.multiagent.infrastructure.model.SkillParam;
import com.multiagent.infrastructure.model.StepType;
import com.multiagent.infrastructure.model.UnifiedRequest;
import com.multiagent.infrastructure.model.UnifiedResponse;
import com.multiagent.infrastructure.model.WorkflowStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认技能执行引擎实现 — 按 Workflow 步骤顺序执行，支持五种步骤类型。
 * <p>
 * 步骤类型：
 * <ul>
 *   <li>TOOL_CALL — 调用 ToolRunner 执行工具</li>
 *   <li>LLM_CALL — 模板渲染 Prompt + AgentAdapter 调用</li>
 *   <li>CONDITION — 评估条件表达式，执行匹配分支</li>
 *   <li>PARALLEL — CompletableFuture 并行执行子步骤</li>
 *   <li>HUMAN_CONFIRM — 推送确认请求，暂停等待用户响应</li>
 * </ul>
 */
@Service
public class DefaultSkillExecutor implements SkillExecutor {

    private static final Logger log = LoggerFactory.getLogger(DefaultSkillExecutor.class);

    private final ToolRunner toolRunner;
    private final TemplateRenderer templateRenderer;
    private final AgentRegistry agentRegistry;

    /** 存储 HUMAN_CONFIRM 步骤的待确认 Future，key 为 stepId */
    private final ConcurrentHashMap<String, CompletableFuture<Boolean>> pendingConfirmations = new ConcurrentHashMap<>();

    public DefaultSkillExecutor(ToolRunner toolRunner,
                                TemplateRenderer templateRenderer,
                                AgentRegistry agentRegistry) {
        this.toolRunner = toolRunner;
        this.templateRenderer = templateRenderer;
        this.agentRegistry = agentRegistry;
    }

    @Override
    public SkillResult execute(Skill skill, Map<String, Object> params, SharedContext context) {
        // 1. 校验必填参数
        validateRequiredParams(skill, params);

        // 2. 构建结果 map（包含输入参数作为初始上下文）
        Map<String, Object> results = new HashMap<>(params);

        // 3. 按顺序执行 workflow steps
        List<WorkflowStep> steps = skill.getWorkflow().getSteps();
        for (WorkflowStep step : steps) {
            try {
                executeStep(step, results, skill, context);
            } catch (Exception e) {
                log.error("Skill '{}' step '{}' 执行失败: {}", skill.getId(), step.getId(), e.getMessage(), e);
                return SkillResult.failure("步骤 " + step.getId() + " 执行失败: " + e.getMessage(), results);
            }
        }

        return SkillResult.success(results);
    }

    /**
     * 校验 Skill 的必填参数是否全部提供。
     */
    void validateRequiredParams(Skill skill, Map<String, Object> params) {
        List<SkillParam> inputParams = skill.getInputParams();
        if (inputParams == null) {
            return;
        }
        for (SkillParam param : inputParams) {
            if (param.isRequired() && (params == null || !params.containsKey(param.getName()))) {
                throw new MissingParameterException(skill.getId(), param.getName());
            }
        }
    }

    /**
     * 执行单个 workflow 步骤，根据类型分派到对应处理方法。
     */
    void executeStep(WorkflowStep step, Map<String, Object> results, Skill skill, SharedContext context) {
        log.debug("执行步骤: id={}, type={}", step.getId(), step.getType());

        switch (step.getType()) {
            case TOOL_CALL -> executeToolCall(step, results);
            case LLM_CALL -> executeLlmCall(step, results, skill, context);
            case CONDITION -> executeCondition(step, results, skill, context);
            case PARALLEL -> executeParallel(step, results, skill, context);
            case HUMAN_CONFIRM -> executeHumanConfirm(step, results);
            default -> throw new IllegalArgumentException("未知步骤类型: " + step.getType());
        }
    }

    /**
     * TOOL_CALL: 调用 ToolRunner 执行工具，结果存入 outputKey。
     */
    void executeToolCall(WorkflowStep step, Map<String, Object> results) {
        log.debug("TOOL_CALL: toolName={}, outputKey={}", step.getToolName(), step.getOutputKey());
        Object toolResult = toolRunner.run(step.getToolName(), results);
        if (step.getOutputKey() != null) {
            results.put(step.getOutputKey(), toolResult);
        }
    }

    /**
     * LLM_CALL: 模板引擎渲染 Prompt，通过 AgentAdapter 调用 LLM，结果存入 outputKey。
     */
    void executeLlmCall(WorkflowStep step, Map<String, Object> results, Skill skill, SharedContext context) {
        log.debug("LLM_CALL: outputKey={}", step.getOutputKey());

        // 渲染 prompt 模板
        String renderedPrompt = templateRenderer.render(step.getPrompt(), results);

        // 获取 Skill 绑定的 Agent
        AgentAdapter agent = agentRegistry.getById(skill.getAgentId());
        if (agent == null) {
            throw new IllegalStateException("Skill '" + skill.getId() + "' 绑定的 Agent '" + skill.getAgentId() + "' 未注册");
        }

        // 构建请求并调用
        UnifiedRequest request = UnifiedRequest.builder()
                .input(renderedPrompt)
                .context(context)
                .build();
        UnifiedResponse response = agent.invoke(request);

        if (step.getOutputKey() != null) {
            results.put(step.getOutputKey(), response.getContent());
        }
    }

    /**
     * CONDITION: 评估条件表达式，执行匹配分支中的步骤。
     */
    void executeCondition(WorkflowStep step, Map<String, Object> results, Skill skill, SharedContext context) {
        log.debug("CONDITION: condition={}", step.getCondition());

        List<Branch> branches = step.getBranches();
        if (branches == null || branches.isEmpty()) {
            log.warn("CONDITION 步骤 '{}' 没有定义分支", step.getId());
            return;
        }

        // 评估条件并查找匹配分支
        String conditionResult = evaluateCondition(step.getCondition(), results);

        for (Branch branch : branches) {
            if (branch.getWhen().equalsIgnoreCase(conditionResult)) {
                log.debug("CONDITION 匹配分支: when={}", branch.getWhen());
                if (branch.getThen() != null) {
                    for (WorkflowStep subStep : branch.getThen()) {
                        executeStep(subStep, results, skill, context);
                    }
                }
                return;
            }
        }

        log.debug("CONDITION 步骤 '{}' 无匹配分支, conditionResult={}", step.getId(), conditionResult);
    }

    /**
     * 评估条件表达式。
     * <p>
     * 支持简单的比较表达式，如 "diskMetrics.maxUsagePercent > 90"。
     * 对于简单的 key 引用，直接返回其 toString 值。
     */
    String evaluateCondition(String condition, Map<String, Object> results) {
        if (condition == null || condition.isBlank()) {
            return "false";
        }

        // 支持简单比较: key > value, key < value, key == value
        String trimmed = condition.trim();

        // 尝试解析 "key.field > number" 格式
        String[] comparators = {" > ", " < ", " >= ", " <= ", " == ", " != "};
        for (String op : comparators) {
            int idx = trimmed.indexOf(op);
            if (idx > 0) {
                String leftExpr = trimmed.substring(0, idx).trim();
                String rightExpr = trimmed.substring(idx + op.length()).trim();

                Object leftVal = resolveExpression(leftExpr, results);
                Object rightVal = resolveExpression(rightExpr, results);

                boolean result = compareValues(leftVal, rightVal, op.trim());
                return String.valueOf(result);
            }
        }

        // 直接作为 key 查找
        Object val = resolveExpression(trimmed, results);
        return val != null ? val.toString() : "false";
    }

    /**
     * 解析表达式值。支持 "key.field" 点号路径和直接 key 查找。
     * 如果无法解析为上下文值，尝试解析为数字字面量。
     */
    Object resolveExpression(String expr, Map<String, Object> results) {
        // 尝试直接 key 查找
        if (results.containsKey(expr)) {
            return results.get(expr);
        }

        // 尝试点号路径: "key.field"
        int dotIdx = expr.indexOf('.');
        if (dotIdx > 0) {
            String rootKey = expr.substring(0, dotIdx);
            Object root = results.get(rootKey);
            if (root instanceof Map<?, ?> map) {
                String field = expr.substring(dotIdx + 1);
                Object val = map.get(field);
                if (val != null) {
                    return val;
                }
            }
        }

        // 尝试解析为数字字面量
        try {
            return Double.parseDouble(expr);
        } catch (NumberFormatException ignored) {
            // 返回原始字符串
        }

        return expr;
    }

    /**
     * 比较两个值。
     */
    boolean compareValues(Object left, Object right, String operator) {
        double leftNum = toDouble(left);
        double rightNum = toDouble(right);

        if (!Double.isNaN(leftNum) && !Double.isNaN(rightNum)) {
            return switch (operator) {
                case ">" -> leftNum > rightNum;
                case "<" -> leftNum < rightNum;
                case ">=" -> leftNum >= rightNum;
                case "<=" -> leftNum <= rightNum;
                case "==" -> leftNum == rightNum;
                case "!=" -> leftNum != rightNum;
                default -> false;
            };
        }

        // 字符串比较
        String leftStr = left != null ? left.toString() : "";
        String rightStr = right != null ? right.toString() : "";
        return switch (operator) {
            case "==" -> leftStr.equals(rightStr);
            case "!=" -> !leftStr.equals(rightStr);
            default -> false;
        };
    }

    private double toDouble(Object value) {
        if (value instanceof Number num) {
            return num.doubleValue();
        }
        if (value instanceof String str) {
            try {
                return Double.parseDouble(str);
            } catch (NumberFormatException e) {
                return Double.NaN;
            }
        }
        return Double.NaN;
    }

    /**
     * PARALLEL: 使用 CompletableFuture 并行执行所有子步骤，等待全部完成。
     */
    void executeParallel(WorkflowStep step, Map<String, Object> results, Skill skill, SharedContext context) {
        List<WorkflowStep> children = step.getChildren();
        if (children == null || children.isEmpty()) {
            log.warn("PARALLEL 步骤 '{}' 没有子步骤", step.getId());
            return;
        }

        log.debug("PARALLEL: 并行执行 {} 个子步骤", children.size());

        // 使用线程安全的 results map
        ConcurrentHashMap<String, Object> concurrentResults = new ConcurrentHashMap<>(results);

        CompletableFuture<?>[] futures = children.stream()
                .map(child -> CompletableFuture.supplyAsync(() -> {
                    // 每个子步骤使用共享的 concurrentResults
                    Map<String, Object> childResults = new HashMap<>(concurrentResults);
                    executeStep(child, childResults, skill, context);
                    // 将子步骤输出合并回 concurrentResults
                    if (child.getOutputKey() != null && childResults.containsKey(child.getOutputKey())) {
                        concurrentResults.put(child.getOutputKey(), childResults.get(child.getOutputKey()));
                    }
                    return null;
                }))
                .toArray(CompletableFuture[]::new);

        // 等待所有子步骤完成
        CompletableFuture.allOf(futures).join();

        // 合并并行结果回主 results
        results.putAll(concurrentResults);
    }

    /**
     * HUMAN_CONFIRM: 推送确认请求，暂停等待用户响应。
     * <p>
     * 使用 CompletableFuture 暂停执行，等待外部调用 {@link #confirmStep(String, boolean)} 恢复。
     * 实际 SSE 推送集成将在 Task 6.2 中完成。
     */
    void executeHumanConfirm(WorkflowStep step, Map<String, Object> results) {
        log.debug("HUMAN_CONFIRM: stepId={}, 等待用户确认", step.getId());

        CompletableFuture<Boolean> confirmFuture = new CompletableFuture<>();
        pendingConfirmations.put(step.getId(), confirmFuture);

        try {
            // 阻塞等待用户确认
            boolean confirmed = confirmFuture.join();
            if (step.getOutputKey() != null) {
                results.put(step.getOutputKey(), confirmed);
            }
            if (!confirmed) {
                throw new RuntimeException("用户拒绝了步骤 '" + step.getId() + "' 的确认请求");
            }
        } finally {
            pendingConfirmations.remove(step.getId());
        }
    }

    /**
     * 外部调用此方法以响应 HUMAN_CONFIRM 步骤的确认请求。
     *
     * @param stepId    步骤 ID
     * @param confirmed 用户是否确认
     */
    public void confirmStep(String stepId, boolean confirmed) {
        CompletableFuture<Boolean> future = pendingConfirmations.get(stepId);
        if (future != null) {
            future.complete(confirmed);
        } else {
            log.warn("未找到待确认的步骤: {}", stepId);
        }
    }
}
