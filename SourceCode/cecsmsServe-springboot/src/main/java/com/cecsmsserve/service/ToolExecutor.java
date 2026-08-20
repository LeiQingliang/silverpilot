package com.cecsmsserve.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cecsmsserve.entity.Activity;
import com.cecsmsserve.entity.Recipe;
import com.cecsmsserve.entity.RecipeOrder;
import com.cecsmsserve.entity.Report;
import com.cecsmsserve.entity.ServiceOrder;
import com.cecsmsserve.entity.ServiceType;
import com.cecsmsserve.entity.UserActivity;
import com.cecsmsserve.util.result.CommonResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter MEAL_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> MUTATING_TOOLS = Set.of(
            "join_activity", "book_service", "cancel_service_order", "book_recipe", "cancel_recipe_order");

    private final IActivityService activityService;
    private final IUserActivityService userActivityService;
    private final IServiceTypeService serviceTypeService;
    private final IServiceOrderService serviceOrderService;
    private final IReportService reportService;
    private final IRecipeService recipeService;
    private final IRecipeOrderService recipeOrderService;
    private final KnowledgeBaseService knowledgeBaseService;

    @Autowired
    public ToolExecutor(
            IActivityService activityService,
            IUserActivityService userActivityService,
            IServiceTypeService serviceTypeService,
            IServiceOrderService serviceOrderService,
            IReportService reportService,
            IRecipeService recipeService,
            IRecipeOrderService recipeOrderService,
            KnowledgeBaseService knowledgeBaseService) {
        this.activityService = activityService;
        this.userActivityService = userActivityService;
        this.serviceTypeService = serviceTypeService;
        this.serviceOrderService = serviceOrderService;
        this.reportService = reportService;
        this.recipeService = recipeService;
        this.recipeOrderService = recipeOrderService;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    /** Kept for focused unit tests and embedded consumers. */
    public ToolExecutor(
            IActivityService activityService,
            IUserActivityService userActivityService,
            IServiceTypeService serviceTypeService,
            IServiceOrderService serviceOrderService,
            IReportService reportService,
            IRecipeService recipeService,
            IRecipeOrderService recipeOrderService) {
        this(activityService, userActivityService, serviceTypeService, serviceOrderService,
                reportService, recipeService, recipeOrderService,
                new KnowledgeBaseService("../../knowledge-base/ima-ready"));
    }

    public ToolPlan prepare(String toolName, Map<String, Object> rawArguments, Integer userId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户身份无效");
        }
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("工具名称无效");
        }
        Map<String, Object> args = rawArguments == null ? Map.of() : rawArguments;
        return switch (toolName) {
            case "list_available_activities" -> readPlan(toolName, "查询可报名活动");
            case "list_services" -> readPlan(toolName, "查询可预约服务");
            case "my_service_orders" -> readPlan(toolName, "查询我的服务订单");
            case "my_health_reports" -> readPlan(toolName, "查询我的健康报告");
            case "get_health_report_detail" -> prepareReportDetail(args, userId);
            case "list_recipes" -> prepareRecipeList(args);
            case "my_recipe_orders" -> readPlan(toolName, "查询我的助餐预订");
            case "search_knowledge_base" -> prepareKnowledgeSearch(args);
            case "join_activity" -> prepareJoinActivity(args, userId);
            case "book_service" -> prepareBookService(args);
            case "cancel_service_order" -> prepareCancelService(args, userId);
            case "book_recipe" -> prepareBookRecipe(args);
            case "cancel_recipe_order" -> prepareCancelRecipe(args, userId);
            default -> throw new IllegalArgumentException("不支持的工具：" + toolName);
        };
    }

    public ToolResult execute(ToolPlan plan, Integer userId) {
        try {
            String content = switch (plan.name()) {
                case "list_available_activities" -> listAvailableActivities();
                case "list_services" -> listServices();
                case "my_service_orders" -> myServiceOrders(userId);
                case "my_health_reports" -> myHealthReports(userId);
                case "get_health_report_detail" -> getHealthReportDetail(
                        userId, requiredInteger(plan.arguments(), "reportId"));
                case "list_recipes" -> listRecipes(optionalString(plan.arguments(), "keyword", 50));
                case "my_recipe_orders" -> myRecipeOrders(userId);
                case "search_knowledge_base" -> knowledgeBaseService.searchText(
                        requiredString(plan.arguments(), "query", 200), 3);
                case "join_activity" -> joinActivity(userId, requiredInteger(plan.arguments(), "activityId"));
                case "book_service" -> bookService(
                        userId,
                        requiredInteger(plan.arguments(), "typeSId"),
                        requiredString(plan.arguments(), "reserveDate", 10),
                        requiredString(plan.arguments(), "serviceAddress", 200));
                case "cancel_service_order" -> cancelServiceOrder(
                        userId, requiredInteger(plan.arguments(), "orderId"));
                case "book_recipe" -> bookRecipe(
                        userId,
                        requiredInteger(plan.arguments(), "recipeId"),
                        requiredString(plan.arguments(), "orderTime", 19),
                        requiredInteger(plan.arguments(), "peopleCount"),
                        optionalString(plan.arguments(), "remark", 500));
                case "cancel_recipe_order" -> cancelRecipeOrder(
                        userId, requiredInteger(plan.arguments(), "orderId"));
                default -> throw new IllegalArgumentException("不支持的工具：" + plan.name());
            };
            return new ToolResult(true, content);
        } catch (IllegalArgumentException ex) {
            return new ToolResult(false, "执行失败：" + ex.getMessage());
        } catch (Exception ex) {
            log.error("Agent tool execution failed: tool={}, userId={}", plan.name(), userId, ex);
            return new ToolResult(false, "执行失败：系统暂时无法完成该操作，请稍后重试。");
        }
    }

    public boolean isMutating(String toolName) {
        return MUTATING_TOOLS.contains(toolName);
    }

    private ToolPlan prepareJoinActivity(Map<String, Object> args, Integer userId) {
        Integer activityId = requiredInteger(args, "activityId");
        Activity activity = requireJoinableActivity(activityId, userId);
        return writePlan("join_activity", Map.of("activityId", activityId),
                "报名活动“" + activity.getActivityName() + "”（" + activity.getActivityDate() + "）");
    }

    private ToolPlan prepareBookService(Map<String, Object> args) {
        Integer typeSId = requiredInteger(args, "typeSId");
        String reserveDateValue = requiredString(args, "reserveDate", 10);
        String address = requiredString(args, "serviceAddress", 200);
        ServiceType serviceType = requireActiveChildService(typeSId);
        LocalDate reserveDate = parseServiceDate(reserveDateValue);
        if (address.length() < 2) {
            throw new IllegalArgumentException("请提供完整的服务地址");
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("typeSId", typeSId);
        normalized.put("reserveDate", reserveDate.toString());
        normalized.put("serviceAddress", address);
        return writePlan("book_service", normalized,
                "预约“" + serviceType.getServiceName() + "”，日期 " + reserveDate + "，地址 " + maskAddress(address));
    }

    private ToolPlan prepareCancelService(Map<String, Object> args, Integer userId) {
        Integer orderId = requiredInteger(args, "orderId");
        ServiceOrder order = serviceOrderService.getById(orderId);
        if (order == null || !userId.equals(order.getuId())) {
            throw new IllegalArgumentException("未找到您的该服务订单");
        }
        if (!Integer.valueOf(0).equals(order.getOrderState())) {
            throw new IllegalArgumentException("只有待受理订单可以取消");
        }
        return writePlan("cancel_service_order", Map.of("orderId", orderId),
                "取消服务订单 #" + orderId + "（预约日期 " + order.getReserveDate() + "）");
    }

    private ToolPlan prepareReportDetail(Map<String, Object> args, Integer userId) {
        Integer reportId = requiredInteger(args, "reportId");
        Report report = reportService.getById(reportId);
        if (report == null || !userId.equals(report.getuId())) {
            throw new IllegalArgumentException("未找到您的该健康报告");
        }
        return new ToolPlan("get_health_report_detail", Map.of("reportId", reportId),
                "查询健康报告 #" + reportId, false);
    }

    private ToolPlan prepareRecipeList(Map<String, Object> args) {
        String keyword = optionalString(args, "keyword", 50);
        return new ToolPlan("list_recipes",
                keyword == null ? Map.of() : Map.of("keyword", keyword),
                keyword == null ? "查询可预订菜谱" : "搜索菜谱“" + keyword + "”",
                false);
    }

    private ToolPlan prepareKnowledgeSearch(Map<String, Object> args) {
        String query = requiredString(args, "query", 200);
        return new ToolPlan("search_knowledge_base", Map.of("query", query),
                "检索专业知识“" + query + "”", false);
    }

    private ToolPlan prepareBookRecipe(Map<String, Object> args) {
        Integer recipeId = requiredInteger(args, "recipeId");
        String orderTimeValue = requiredString(args, "orderTime", 19);
        Integer peopleCount = requiredInteger(args, "peopleCount");
        String remark = optionalString(args, "remark", 500);
        Recipe recipe = requireActiveRecipe(recipeId);
        LocalDateTime orderTime = parseMealTime(orderTimeValue);
        if (peopleCount > 20) {
            throw new IllegalArgumentException("用餐人数需为1-20人");
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("recipeId", recipeId);
        normalized.put("orderTime", orderTime.format(MEAL_TIME_FORMAT));
        normalized.put("peopleCount", peopleCount);
        if (remark != null) {
            normalized.put("remark", remark);
        }
        return writePlan("book_recipe", normalized,
                "预订菜谱“" + recipe.getName() + "”，时间 " + orderTime.format(MEAL_TIME_FORMAT)
                        + "，人数 " + peopleCount);
    }

    private ToolPlan prepareCancelRecipe(Map<String, Object> args, Integer userId) {
        Integer orderId = requiredInteger(args, "orderId");
        RecipeOrder order = recipeOrderService.getById(orderId);
        if (order == null || !userId.equals(order.getUserId())) {
            throw new IllegalArgumentException("未找到您的该助餐预订");
        }
        if (!Integer.valueOf(0).equals(order.getStatus())) {
            throw new IllegalArgumentException("只有待处理助餐预订可以取消");
        }
        return writePlan("cancel_recipe_order", Map.of("orderId", orderId),
                "取消助餐预订 #" + orderId + "（" + formatDate(order.getOrderTime()) + "）");
    }

    private ToolPlan readPlan(String name, String summary) {
        return new ToolPlan(name, Map.of(), summary, false);
    }

    private ToolPlan writePlan(String name, Map<String, Object> arguments, String summary) {
        return new ToolPlan(name, arguments, summary, true);
    }

    @SuppressWarnings("unchecked")
    private String listAvailableActivities() {
        CommonResult<?> response = activityService.selectNotBegin();
        if (response.getCode() != 200) {
            throw new IllegalArgumentException(valueOrFallback(response.getMsg(), "活动数据暂时无法读取"));
        }
        if (!(response.getResult() instanceof List<?> rawList)) {
            throw new IllegalArgumentException("活动数据格式异常");
        }
        List<Activity> activities = (List<Activity>) rawList;
        if (activities.isEmpty()) {
            return "当前没有可报名的活动。";
        }
        StringBuilder text = new StringBuilder("可报名活动：\n");
        int visibleCount = Math.min(activities.size(), 5);
        for (int i = 0; i < visibleCount; i++) {
            Activity activity = activities.get(i);
            int remaining = activity.getLimitNum() == null || activity.getLimitNum() <= 0
                    ? -1 : Math.max(0, activity.getLimitNum() - valueOrZero(activity.getSignNum()));
            text.append("- ID ").append(activity.getId()).append("：").append(activity.getActivityName())
                    .append("，").append(activity.getActivityDate()).append(" ").append(activity.getStartTime())
                    .append("，地点：").append(valueOrFallback(activity.getActivityAddress(), "待通知"));
            if (remaining >= 0) {
                text.append("，剩余 ").append(remaining).append(" 个名额");
            }
            text.append('\n');
        }
        return text.toString().trim();
    }

    private String joinActivity(Integer userId, Integer activityId) {
        requireJoinableActivity(activityId, userId);
        UserActivity registration = new UserActivity();
        registration.setuId(userId);
        registration.setaId(activityId);
        registration.setEnterDate(LocalDate.now());
        registration.setEnterTime(LocalTime.now().withNano(0));
        registration.setState("报名成功");
        CommonResult<?> response = userActivityService.insert(registration);
        if (response.getCode() != 200) {
            throw new IllegalArgumentException(valueOrFallback(response.getMsg(), "报名失败，可能已无剩余名额"));
        }
        return "活动报名成功";
    }

    @SuppressWarnings("unchecked")
    private String listServices() {
        CommonResult<?> response = serviceTypeService.selectAllChildren();
        if (response.getCode() != 200) {
            throw new IllegalArgumentException(valueOrFallback(response.getMsg(), "服务数据暂时无法读取"));
        }
        if (!(response.getResult() instanceof List<?> rawList)) {
            throw new IllegalArgumentException("服务数据格式异常");
        }
        List<ServiceType> services = ((List<ServiceType>) rawList).stream()
                .filter(service -> Integer.valueOf(1).equals(service.getState()))
                .toList();
        if (services.isEmpty()) {
            return "当前没有可预约的服务。";
        }
        StringBuilder text = new StringBuilder("可预约服务：\n");
        int visibleCount = Math.min(services.size(), 8);
        for (int i = 0; i < visibleCount; i++) {
            ServiceType service = services.get(i);
            text.append("- ID ").append(service.getId()).append("：")
                    .append(service.getServiceName()).append('\n');
        }
        if (services.size() > visibleCount) {
            text.append("另有 ").append(services.size() - visibleCount).append(" 项服务。\n");
        }
        return text.toString().trim();
    }

    private String bookService(Integer userId, Integer typeSId, String reserveDateValue, String serviceAddress) {
        ServiceType serviceType = requireActiveChildService(typeSId);
        LocalDate reserveDate = parseServiceDate(reserveDateValue);
        if (serviceAddress.length() < 2) {
            throw new IllegalArgumentException("请提供完整的服务地址");
        }
        ServiceOrder order = new ServiceOrder();
        order.setuId(userId);
        order.setTypeSId(typeSId);
        order.setTypeBId(serviceType.getLeaderId());
        order.setReserveDate(reserveDate);
        order.setServiceAddress(serviceAddress);
        CommonResult<?> response = serviceOrderService.insert(order);
        if (response.getCode() != 200) {
            throw new IllegalArgumentException(valueOrFallback(response.getMsg(), "预约失败，请稍后重试"));
        }
        Object created = response.getResult();
        String suffix = created instanceof ServiceOrder createdOrder && createdOrder.getId() != null
                ? "，订单号 #" + createdOrder.getId() : "";
        return "服务预约成功，订单待受理" + suffix;
    }

    @SuppressWarnings("unchecked")
    private String myServiceOrders(Integer userId) {
        CommonResult<?> response = serviceOrderService.selectByUId(userId);
        if (response.getCode() != 200) {
            throw new IllegalArgumentException(valueOrFallback(response.getMsg(), "服务订单暂时无法读取"));
        }
        if (!(response.getResult() instanceof List<?> rawList)) {
            throw new IllegalArgumentException("服务订单数据格式异常");
        }
        List<ServiceOrder> orders = (List<ServiceOrder>) rawList;
        if (orders.isEmpty()) {
            return "您还没有服务订单。";
        }
        StringBuilder text = new StringBuilder("最近的服务订单：\n");
        int count = Math.min(orders.size(), 5);
        for (int i = 0; i < count; i++) {
            ServiceOrder order = orders.get(i);
            String serviceName = order.getTypeS() == null
                    ? "服务ID " + order.getTypeSId() : order.getTypeS().getServiceName();
            text.append("- 订单 #").append(order.getId()).append("：").append(serviceName)
                    .append("，预约日期 ").append(order.getReserveDate())
                    .append("，状态：").append(serviceOrderStatus(order.getOrderState())).append('\n');
        }
        return text.toString().trim();
    }

    private String cancelServiceOrder(Integer userId, Integer orderId) {
        CommonResult<Void> response = serviceOrderService.cancelByUser(orderId, userId);
        if (response.getCode() != 200) {
            throw new IllegalArgumentException(valueOrFallback(response.getMsg(), "取消服务预约失败"));
        }
        return "服务订单 #" + orderId + " 已取消";
    }

    @SuppressWarnings("unchecked")
    private String myHealthReports(Integer userId) {
        CommonResult<?> response = reportService.selectByuId(userId);
        if (response.getCode() != 200) {
            throw new IllegalArgumentException(valueOrFallback(response.getMsg(), "健康报告暂时无法读取"));
        }
        if (!(response.getResult() instanceof List<?> rawList)) {
            throw new IllegalArgumentException("健康报告数据格式异常");
        }
        List<Report> reports = (List<Report>) rawList;
        if (reports.isEmpty()) {
            return "您还没有体检报告。";
        }
        StringBuilder text = new StringBuilder("您的体检报告：\n");
        int visibleCount = Math.min(reports.size(), 5);
        for (int i = 0; i < visibleCount; i++) {
            Report report = reports.get(i);
            text.append("- 报告 #").append(report.getId()).append("，日期：").append(report.getTime()).append('\n');
        }
        return text.toString().trim();
    }

    private String getHealthReportDetail(Integer userId, Integer reportId) {
        Report report = reportService.getById(reportId);
        if (report == null || !userId.equals(report.getuId())) {
            throw new IllegalArgumentException("未找到您的该健康报告");
        }
        return "报告 #" + reportId
                + "：身高 " + valueOrUnmeasured(report.getHeight(), "cm")
                + "；体重 " + valueOrUnmeasured(report.getWeight(), "kg")
                + "；血压 " + valueOrUnmeasured(report.getBp(), "")
                + "；血糖 " + valueOrUnmeasured(report.getBs(), "mmol/L")
                + "。数据仅供查看，不替代医生诊断。";
    }

    private String listRecipes(String keyword) {
        CommonResult<?> response = recipeService.getRecipeList(1, 8, keyword, null);
        if (response.getCode() != 200) {
            throw new IllegalArgumentException(valueOrFallback(response.getMsg(), "菜谱数据暂时无法读取"));
        }
        if (!(response.getResult() instanceof IPage<?> page)) {
            throw new IllegalArgumentException("菜谱数据格式异常");
        }
        if (page.getRecords().isEmpty()) {
            return keyword == null ? "当前没有可预订菜谱。" : "没有找到与“" + keyword + "”相关的菜谱。";
        }
        StringBuilder text = new StringBuilder("可预订菜谱：\n");
        for (Object value : page.getRecords()) {
            if (value instanceof Recipe recipe) {
                text.append("- ID ").append(recipe.getId()).append("：").append(recipe.getName());
                if (recipe.getSuitableCrowd() != null && !recipe.getSuitableCrowd().isBlank()) {
                    text.append("，适合：").append(recipe.getSuitableCrowd());
                }
                text.append('\n');
            }
        }
        return text.toString().trim();
    }

    private String bookRecipe(
            Integer userId, Integer recipeId, String orderTimeValue, Integer peopleCount, String remark) {
        Recipe recipe = requireActiveRecipe(recipeId);
        LocalDateTime orderTime = parseMealTime(orderTimeValue);
        if (peopleCount < 1 || peopleCount > 20) {
            throw new IllegalArgumentException("用餐人数需为1-20人");
        }
        RecipeOrder order = new RecipeOrder();
        order.setUserId(userId);
        order.setRecipeId(recipeId);
        order.setOrderTime(Date.from(orderTime.atZone(BUSINESS_ZONE).toInstant()));
        order.setPeopleCount(peopleCount);
        order.setRemark(remark);
        CommonResult<RecipeOrder> response = recipeOrderService.placeOrder(order);
        if (response.getCode() != 200) {
            throw new IllegalArgumentException(valueOrFallback(response.getMsg(), "助餐预订失败"));
        }
        Integer orderId = response.getResult() == null ? null : response.getResult().getId();
        return "菜谱“" + recipe.getName() + "”预订成功"
                + (orderId == null ? "" : "，订单号 #" + orderId);
    }

    private String myRecipeOrders(Integer userId) {
        CommonResult<IPage<RecipeOrder>> response = recipeOrderService.getMyOrders(userId, 1, 5, null, null);
        if (response.getCode() != 200) {
            throw new IllegalArgumentException(valueOrFallback(response.getMsg(), "助餐订单暂时无法读取"));
        }
        IPage<RecipeOrder> page = response.getResult();
        if (page == null) {
            throw new IllegalArgumentException("助餐订单数据格式异常");
        }
        if (page.getRecords().isEmpty()) {
            return "您还没有助餐预订。";
        }
        StringBuilder text = new StringBuilder("最近的助餐预订：\n");
        for (RecipeOrder order : page.getRecords()) {
            text.append("- 订单 #").append(order.getId()).append("：")
                    .append(valueOrFallback(order.getRecipeName(), "菜谱ID " + order.getRecipeId()))
                    .append("，时间 ").append(formatDate(order.getOrderTime()))
                    .append("，人数 ").append(order.getPeopleCount())
                    .append("，状态：").append(recipeOrderStatus(order.getStatus())).append('\n');
        }
        return text.toString().trim();
    }

    private String cancelRecipeOrder(Integer userId, Integer orderId) {
        CommonResult<Void> response = recipeOrderService.cancelOrder(orderId, userId);
        if (response.getCode() != 200) {
            throw new IllegalArgumentException(valueOrFallback(response.getMsg(), "取消助餐预订失败"));
        }
        return "助餐订单 #" + orderId + " 已取消";
    }

    private Activity requireJoinableActivity(Integer activityId, Integer userId) {
        Activity activity = activityService.getById(activityId);
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        if (activity == null) {
            throw new IllegalArgumentException("活动不存在");
        }
        if (!Integer.valueOf(1).equals(activity.getState()) || activity.getActivityDate() == null
                || activity.getActivityDate().isBefore(today)
                || (activity.getActivityDate().equals(today)
                    && (activity.getStartTime() == null || !activity.getStartTime().isAfter(now)))) {
            throw new IllegalArgumentException("活动已截止报名");
        }
        int signNum = valueOrZero(activity.getSignNum());
        int limitNum = valueOrZero(activity.getLimitNum());
        if (limitNum > 0 && signNum >= limitNum) {
            throw new IllegalArgumentException("活动名额已满");
        }
        CommonResult<?> existing = userActivityService.selectByuIdByaId(userId, activityId);
        if (existing.getCode() == 200 && existing.getResult() != null) {
            throw new IllegalArgumentException("您已经报名过该活动");
        }
        return activity;
    }

    private ServiceType requireActiveChildService(Integer typeSId) {
        ServiceType serviceType = serviceTypeService.getById(typeSId);
        if (serviceType == null || serviceType.getLeaderId() == null
                || !Integer.valueOf(1).equals(serviceType.getState())) {
            throw new IllegalArgumentException("服务类型不存在或已停用");
        }
        ServiceType parentType = serviceTypeService.getById(serviceType.getLeaderId());
        if (parentType == null || !Integer.valueOf(1).equals(parentType.getState())) {
            throw new IllegalArgumentException("服务大类不存在或已停用");
        }
        return serviceType;
    }

    private Recipe requireActiveRecipe(Integer recipeId) {
        Recipe recipe = recipeService.getById(recipeId);
        if (recipe == null || !Integer.valueOf(1).equals(recipe.getStatus())) {
            throw new IllegalArgumentException("菜谱不存在或已停用");
        }
        return recipe;
    }

    private LocalDate parseServiceDate(String value) {
        try {
            LocalDate date = LocalDate.parse(value);
            if (date.isBefore(LocalDate.now())) {
                throw new IllegalArgumentException("预约日期不能早于今天");
            }
            return date;
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("预约日期必须使用 yyyy-MM-dd 格式");
        }
    }

    private LocalDateTime parseMealTime(String value) {
        try {
            LocalDateTime time = LocalDateTime.parse(value, MEAL_TIME_FORMAT);
            if (!time.atZone(BUSINESS_ZONE).toInstant().isAfter(java.time.Instant.now())) {
                throw new IllegalArgumentException("预订时间必须晚于当前时间");
            }
            return time;
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("预订时间必须使用 yyyy-MM-dd HH:mm:ss 格式");
        }
    }

    private Integer requiredInteger(Map<String, Object> args, String name) {
        Object value = args.get(name);
        int parsed;
        try {
            parsed = value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(name + " 必须是整数");
        }
        if (parsed <= 0) {
            throw new IllegalArgumentException(name + " 必须大于0");
        }
        return parsed;
    }

    private String requiredString(Map<String, Object> args, String name, int maxLength) {
        String parsed = optionalString(args, name, maxLength);
        if (parsed == null) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return parsed;
    }

    private String optionalString(Map<String, Object> args, String name, int maxLength) {
        Object value = args.get(name);
        String parsed = value == null ? "" : value.toString().trim();
        if (parsed.isEmpty()) {
            return null;
        }
        if (parsed.length() > maxLength) {
            throw new IllegalArgumentException(name + " 内容过长");
        }
        return parsed;
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private String valueOrUnmeasured(Object value, String unit) {
        return value == null || value.toString().isBlank() ? "未测" : value + unit;
    }

    private String valueOrFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String maskAddress(String address) {
        if (address.length() <= 4) {
            return address;
        }
        return address.substring(0, 3) + "***" + address.substring(address.length() - 1);
    }

    private String formatDate(Date date) {
        return date == null ? "未设置" : MEAL_TIME_FORMAT.format(date.toInstant().atZone(BUSINESS_ZONE));
    }

    private String serviceOrderStatus(Integer status) {
        return switch (status == null ? -1 : status) {
            case 0 -> "待受理";
            case 1 -> "已取消";
            case 2 -> "待完成";
            case 3 -> "待评价";
            case 4 -> "已结束";
            default -> "未知";
        };
    }

    private String recipeOrderStatus(Integer status) {
        return switch (status == null ? -1 : status) {
            case 0 -> "已预订";
            case 1 -> "已完成";
            case 2 -> "已取消";
            default -> "未知";
        };
    }

    public record ToolPlan(String name, Map<String, Object> arguments, String summary, boolean requiresConfirmation) {
        public ToolPlan {
            arguments = Map.copyOf(arguments);
        }
    }

    public record ToolResult(boolean success, String content) { }
}
