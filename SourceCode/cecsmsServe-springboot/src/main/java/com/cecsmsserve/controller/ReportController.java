package com.cecsmsserve.controller;

import com.cecsmsserve.entity.Report;
import com.cecsmsserve.service.DeepSeekClient;
import com.cecsmsserve.service.DeepSeekClient.DeepSeekException;
import com.cecsmsserve.service.IReportService;
import com.cecsmsserve.service.IUserService;
import com.cecsmsserve.util.JWTInterceptor;
import com.cecsmsserve.util.result.CommonResult;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/report")
public class ReportController {

    private static final Logger log = LoggerFactory.getLogger(ReportController.class);
    private static final String MEDICAL_DISCLAIMER =
            "提示：以上内容仅作健康管理参考，不能替代医生诊断；如有明显不适或异常指标，请及时就医。";

    private final IReportService reportService;
    private final IUserService userService;
    private final DeepSeekClient deepSeekClient;
    private final String model;
    private final boolean thinkingEnabled;

    public ReportController(
            IReportService reportService,
            IUserService userService,
            DeepSeekClient deepSeekClient,
            @Value("${deepseek.api.model:deepseek-v4-flash}") String model,
            @Value("${deepseek.api.thinking-enabled:false}") boolean thinkingEnabled) {
        this.reportService = reportService;
        this.userService = userService;
        this.deepSeekClient = deepSeekClient;
        this.model = model;
        this.thinkingEnabled = thinkingEnabled;
    }

    @GetMapping("/selectByuId/{uId}")
    public CommonResult<?> selectByuId(@PathVariable int uId, HttpServletRequest request) {
        Integer currentUserId = attributeAsInteger(request, JWTInterceptor.USER_ID_ATTRIBUTE);
        Integer currentRole = attributeAsInteger(request, JWTInterceptor.USER_ROLE_ATTRIBUTE);
        if (currentUserId == null
                || (!currentUserId.equals(uId) && !isHealthManager(currentRole))) {
            return CommonResult.forbidden("无权查看该用户的健康报告");
        }
        return reportService.selectByuId(uId);
    }

    @PutMapping("/insert")
    public CommonResult<?> insert(@RequestBody Report report, HttpServletRequest request) {
        Integer currentUserId = attributeAsInteger(request, JWTInterceptor.USER_ID_ATTRIBUTE);
        Integer currentRole = attributeAsInteger(request, JWTInterceptor.USER_ROLE_ATTRIBUTE);
        if (currentUserId == null || !isHealthManager(currentRole)) {
            return CommonResult.forbidden();
        }
        String validationError = validateReport(report, false);
        if (validationError != null) {
            return CommonResult.validateFailed(validationError);
        }
        if (userService.getById(report.getuId()) == null) {
            return CommonResult.notFound("用户不存在");
        }
        report.setId(null);
        report.setdId(currentUserId);
        if (report.getTime() == null) {
            report.setTime(LocalDateTime.now().withNano(0));
        }
        return reportService.insert(report);
    }

    @PostMapping("/update")
    public CommonResult<?> update(@RequestBody Report report, HttpServletRequest request) {
        Integer currentUserId = attributeAsInteger(request, JWTInterceptor.USER_ID_ATTRIBUTE);
        Integer currentRole = attributeAsInteger(request, JWTInterceptor.USER_ROLE_ATTRIBUTE);
        if (currentUserId == null || !isHealthManager(currentRole)) {
            return CommonResult.forbidden();
        }
        if (report.getId() == null || report.getId() <= 0) {
            return CommonResult.validateFailed("报告编号无效");
        }
        Report existing = reportService.getById(report.getId());
        if (existing == null) {
            return CommonResult.notFound("报告不存在");
        }
        report.setuId(existing.getuId());
        report.setdId(currentUserId);
        String validationError = validateReport(report, true);
        if (validationError != null) {
            return CommonResult.validateFailed(validationError);
        }
        return reportService.update(report);
    }

    @GetMapping("/analyze/{id}")
    public CommonResult<?> analyzeReport(@PathVariable Integer id, HttpServletRequest request) {
        Report report = reportService.getById(id);
        if (report == null) {
            return CommonResult.notFound("报告不存在");
        }

        Integer currentUserId = attributeAsInteger(request, JWTInterceptor.USER_ID_ATTRIBUTE);
        Integer currentRole = attributeAsInteger(request, JWTInterceptor.USER_ROLE_ATTRIBUTE);
        boolean privileged = Integer.valueOf(1).equals(currentRole) || Integer.valueOf(3).equals(currentRole);
        if (currentUserId == null || (!currentUserId.equals(report.getuId()) && !privileged)) {
            return CommonResult.forbidden("无权查看该健康报告");
        }

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", List.of(
                    Map.of(
                            "role", "system",
                            "content", "你是老年健康信息解释助手。只能解释指标并给出一般生活建议，不能诊断疾病、开药或替代医生。"),
                    Map.of("role", "user", "content", buildPrompt(report))));
            body.put("stream", false);
            body.put("thinking", Map.of("type", thinkingEnabled ? "enabled" : "disabled"));
            body.put("temperature", 0.2);
            body.put("max_tokens", 1800);
            body.put("user_id", "cecsms-user-" + currentUserId);

            String analysis = deepSeekClient.firstText(deepSeekClient.chatCompletion(body));
            return CommonResult.success(analysis + "\n\n" + MEDICAL_DISCLAIMER);
        } catch (DeepSeekException ex) {
            return CommonResult.error(ex.getMessage());
        } catch (Exception ex) {
            log.error("Health report AI analysis failed for report {}", id, ex);
            return CommonResult.error("AI 分析暂时不可用，请稍后重试");
        }
    }

    private String buildPrompt(Report report) {
        List<String> metrics = new ArrayList<>();
        addMetric(metrics, "身高", report.getHeight(), "cm");
        addMetric(metrics, "体重", report.getWeight(), "kg");
        addMetric(metrics, "血压", report.getBp(), "mmHg");
        addMetric(metrics, "白细胞(WBC)", report.getWbc(), "10^9/L");
        addMetric(metrics, "红细胞(RBC)", report.getRbc(), "10^12/L");
        addMetric(metrics, "血小板(PLT)", report.getPlt(), "10^9/L");
        addMetric(metrics, "血红蛋白(HGB)", report.getHgb(), "g/L");
        addMetric(metrics, "淋巴细胞(LYM)", report.getLym(), "%");
        addMetric(metrics, "中性粒细胞比率(NEUT)", report.getNeut(), "%");
        addMetric(metrics, "总胆固醇(TCHO)", report.getTcho(), "mmol/L");
        addMetric(metrics, "甘油三酯(TG)", report.getTg(), "mmol/L");
        addMetric(metrics, "高密度脂蛋白(HDLC)", report.getHdlc(), "mmol/L");
        addMetric(metrics, "低密度脂蛋白(LDLC)", report.getLdlc(), "mmol/L");
        addMetric(metrics, "谷丙转氨酶(ALT)", report.getAlt(), "U/L");
        addMetric(metrics, "谷草转氨酶(AST)", report.getAst(), "U/L");
        addMetric(metrics, "血清胆红素(SB)", report.getSb(), "umol/L");
        addMetric(metrics, "血糖(BS)", report.getBs(), "mmol/L");
        addMetric(metrics, "尿葡萄糖(GLU)", report.getGlu(), "");
        addMetric(metrics, "尿比重(SG)", report.getSg(), "");
        addMetric(metrics, "尿酸碱度(PH)", report.getPh(), "");
        addMetric(metrics, "血尿酸(UA)", report.getUa(), "μmol/L");
        addMetric(metrics, "血肌酐(Scr)", report.getScr(), "μmol/L");
        addMetric(metrics, "尿素氮(BUN)", report.getBun(), "mmol/L");
        addMetric(metrics, "甲胎蛋白(AFP)", report.getAfp(), "ng/ml");
        addMetric(metrics, "癌胚抗原(CEA)", report.getCea(), "μg/L");
        addMetric(metrics, "铁蛋白(Ferritin)", report.getFerritin(), "μg/L");

        String data = metrics.isEmpty() ? "暂无有效体检数据" : String.join("\n", metrics);
        return """
                请解释以下体检数据并提供易懂、克制的健康管理建议。
                要求：
                1. 只说明哪些数值可能需要关注，并明确参考范围可能因实验室、人群和病史而不同。
                2. 给出饮食、运动、作息和复查方面的一般建议。
                3. 不推断具体疾病，不提供药物剂量，不制造确定性结论。
                4. 使用纯文本分点表达，适合老年人阅读。

                体检数据：
                """ + data;
    }

    private void addMetric(List<String> metrics, String name, Object value, String unit) {
        if (value == null) {
            return;
        }
        String text = value.toString().trim();
        if (text.isEmpty() || "未测".equals(text)) {
            return;
        }
        metrics.add("- " + name + "：" + text + (unit.isEmpty() ? "" : " " + unit));
    }

    private Integer attributeAsInteger(HttpServletRequest request, String name) {
        Object value = request.getAttribute(name);
        return value instanceof Integer integer ? integer : null;
    }

    private boolean isHealthManager(Integer roleId) {
        return Integer.valueOf(1).equals(roleId) || Integer.valueOf(3).equals(roleId);
    }

    private String validateReport(Report report, boolean requireId) {
        if (report == null) {
            return "报告信息不能为空";
        }
        if (requireId && (report.getId() == null || report.getId() <= 0)) {
            return "报告编号无效";
        }
        if (report.getuId() == null || report.getuId() <= 0) {
            return "请选择报告所属用户";
        }
        if (report.getTime() != null && report.getTime().isAfter(LocalDateTime.now().plusMinutes(5))) {
            return "体检时间不能晚于当前时间";
        }
        if (report.getHeight() != null
                && (!Double.isFinite(report.getHeight()) || report.getHeight() < 30 || report.getHeight() > 250)) {
            return "身高必须在30到250厘米之间";
        }
        if (report.getWeight() != null
                && (!Double.isFinite(report.getWeight()) || report.getWeight() < 2 || report.getWeight() > 500)) {
            return "体重必须在2到500公斤之间";
        }
        List<String> values = List.of(
                nullToEmpty(report.getBp()), nullToEmpty(report.getWbc()), nullToEmpty(report.getRbc()),
                nullToEmpty(report.getPlt()), nullToEmpty(report.getHgb()), nullToEmpty(report.getLym()),
                nullToEmpty(report.getNeut()), nullToEmpty(report.getTcho()), nullToEmpty(report.getTg()),
                nullToEmpty(report.getHdlc()), nullToEmpty(report.getLdlc()), nullToEmpty(report.getAlt()),
                nullToEmpty(report.getAst()), nullToEmpty(report.getSb()), nullToEmpty(report.getBs()),
                nullToEmpty(report.getGlu()), nullToEmpty(report.getBil()), nullToEmpty(report.getKet()),
                nullToEmpty(report.getUro()), nullToEmpty(report.getPh()), nullToEmpty(report.getSg()),
                nullToEmpty(report.getUa()), nullToEmpty(report.getScr()), nullToEmpty(report.getBun()),
                nullToEmpty(report.getAfp()), nullToEmpty(report.getCea()), nullToEmpty(report.getFerritin()));
        if (values.stream().anyMatch(value -> value.length() > 255)) {
            return "单项体检指标不能超过255字";
        }
        return null;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
