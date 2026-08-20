package com.cecsmsserve.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Tools {

    private static final List<Map<String, Object>> DEFINITIONS = List.of(
            tool("list_available_activities",
                    "查询当前可报名且仍有名额的活动，先用它获得活动ID", parameters(Map.of(), List.of())),
            tool("join_activity",
                    "报名指定活动；这是写操作，系统会先向用户展示确认卡片",
                    parameters(Map.of("activityId", property("integer", "活动ID")), List.of("activityId"))),
            tool("list_services",
                    "查询当前可预约的服务小类及其ID", parameters(Map.of(), List.of())),
            tool("book_service",
                    "预约养老服务；这是写操作，信息齐全后调用并等待用户确认",
                    parameters(orderedProperties(
                            "typeSId", property("integer", "服务小类ID"),
                            "reserveDate", property("string", "预约日期，严格格式 yyyy-MM-dd"),
                            "serviceAddress", property("string", "服务详细地址，最长200字")),
                            List.of("typeSId", "reserveDate", "serviceAddress"))),
            tool("my_service_orders",
                    "查询当前用户最近的服务订单、订单号和状态", parameters(Map.of(), List.of())),
            tool("cancel_service_order",
                    "取消当前用户自己的待受理服务订单；这是写操作，需要用户确认",
                    parameters(Map.of("orderId", property("integer", "服务订单ID")), List.of("orderId"))),
            tool("my_health_reports",
                    "查询当前用户的体检报告列表和报告ID", parameters(Map.of(), List.of())),
            tool("get_health_report_detail",
                    "查询当前用户指定体检报告的指标详情",
                    parameters(Map.of("reportId", property("integer", "体检报告ID")), List.of("reportId"))),
            tool("list_recipes",
                    "查询或按关键词搜索可预订菜谱及菜谱ID",
                    parameters(Map.of("keyword", property("string", "可选的菜谱关键词，最长50字")), List.of())),
            tool("book_recipe",
                    "预订助餐菜谱；这是写操作，信息齐全后调用并等待用户确认",
                    parameters(orderedProperties(
                            "recipeId", property("integer", "菜谱ID"),
                            "orderTime", property("string", "预订时间，严格格式 yyyy-MM-dd HH:mm:ss"),
                            "peopleCount", property("integer", "用餐人数，1到20"),
                            "remark", property("string", "可选备注，最长500字")),
                            List.of("recipeId", "orderTime", "peopleCount"))),
            tool("my_recipe_orders",
                    "查询当前用户最近的助餐预订、订单号和状态", parameters(Map.of(), List.of())),
            tool("cancel_recipe_order",
                    "取消当前用户自己的待处理助餐预订；这是写操作，需要用户确认",
                    parameters(Map.of("orderId", property("integer", "助餐订单ID")), List.of("orderId"))),
            tool("search_knowledge_base",
                    "检索已审核的养老业务、服务SOP、健康安全边界和产品知识；回答专业问题前优先调用",
                    parameters(Map.of("query", property("string", "具体检索问题，最长200字")), List.of("query")))
    );

    private Tools() { }

    public static List<Map<String, Object>> getTools() {
        return DEFINITIONS;
    }

    private static Map<String, Object> tool(
            String name, String description, Map<String, Object> parameters) {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", name,
                        "description", description,
                        "parameters", parameters));
    }

    private static Map<String, Object> parameters(
            Map<String, Object> properties, List<String> required) {
        return Map.of(
                "type", "object",
                "properties", properties,
                "required", required,
                "additionalProperties", false);
    }

    private static Map<String, Object> property(String type, String description) {
        return Map.of("type", type, "description", description);
    }

    private static Map<String, Object> orderedProperties(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            result.put((String) entries[i], entries[i + 1]);
        }
        return result;
    }
}
