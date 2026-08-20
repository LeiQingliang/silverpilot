package com.cecsmsserve.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

@TableName("agent_run")
public class AgentRun implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("run_id")
    private String runId;
    @TableField("user_id")
    private Integer userId;
    private String provider;
    private String model;
    @TableField("input_modality")
    private String inputModality;
    private String status;
    @TableField("latency_ms")
    private Long latencyMs;
    @TableField("llm_calls")
    private Integer llmCalls;
    @TableField("tool_calls")
    private Integer toolCalls;
    @TableField("successful_tools")
    private Integer successfulTools;
    @TableField("failed_tools")
    private Integer failedTools;
    @TableField("confirmation_required")
    private Boolean confirmationRequired;
    @TableField("prompt_version")
    private String promptVersion;
    @TableField("prompt_tokens")
    private Integer promptTokens;
    @TableField("completion_tokens")
    private Integer completionTokens;
    @TableField("total_tokens")
    private Integer totalTokens;
    @TableField("error_type")
    private String errorType;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("completed_at")
    private LocalDateTime completedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getInputModality() { return inputModality; }
    public void setInputModality(String inputModality) { this.inputModality = inputModality; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }
    public Integer getLlmCalls() { return llmCalls; }
    public void setLlmCalls(Integer llmCalls) { this.llmCalls = llmCalls; }
    public Integer getToolCalls() { return toolCalls; }
    public void setToolCalls(Integer toolCalls) { this.toolCalls = toolCalls; }
    public Integer getSuccessfulTools() { return successfulTools; }
    public void setSuccessfulTools(Integer successfulTools) { this.successfulTools = successfulTools; }
    public Integer getFailedTools() { return failedTools; }
    public void setFailedTools(Integer failedTools) { this.failedTools = failedTools; }
    public Boolean getConfirmationRequired() { return confirmationRequired; }
    public void setConfirmationRequired(Boolean confirmationRequired) { this.confirmationRequired = confirmationRequired; }
    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
    public Integer getPromptTokens() { return promptTokens; }
    public void setPromptTokens(Integer promptTokens) { this.promptTokens = promptTokens; }
    public Integer getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(Integer completionTokens) { this.completionTokens = completionTokens; }
    public Integer getTotalTokens() { return totalTokens; }
    public void setTotalTokens(Integer totalTokens) { this.totalTokens = totalTokens; }
    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
