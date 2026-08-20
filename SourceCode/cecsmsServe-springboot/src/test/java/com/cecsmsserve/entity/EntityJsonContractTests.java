package com.cecsmsserve.entity;

import com.cecsmsserve.entity.vo.MyActivity;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EntityJsonContractTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void activityPreservesDirectorIdAcrossJacksonThree() throws Exception {
        Activity activity = objectMapper.readValue("{\"dId\":2,\"activityTypeId\":1}", Activity.class);

        assertEquals(2, activity.getdId());
        assertEquals(1, activity.getActivityTypeId());
        JsonNode json = objectMapper.valueToTree(activity);
        assertEquals(2, json.get("dId").asInt());
    }

    @Test
    void activeDomainAcronymIdsKeepTheirPublicJsonNames() throws Exception {
        ServiceOrder order = objectMapper.readValue(
                "{\"uId\":17,\"mId\":4,\"dId\":2,\"typeBId\":1,\"typeSId\":3}",
                ServiceOrder.class);
        UserActivity registration = objectMapper.readValue("{\"uId\":17,\"aId\":9}", UserActivity.class);
        Report report = objectMapper.readValue("{\"uId\":17,\"dId\":2}", Report.class);

        assertEquals(17, order.getuId());
        assertEquals(4, order.getmId());
        assertEquals(2, order.getdId());
        assertEquals(1, order.getTypeBId());
        assertEquals(3, order.getTypeSId());
        assertEquals(17, registration.getuId());
        assertEquals(9, registration.getaId());
        assertEquals(17, report.getuId());
        assertEquals(2, report.getdId());

        JsonNode orderJson = objectMapper.valueToTree(order);
        JsonNode registrationJson = objectMapper.valueToTree(registration);
        JsonNode reportJson = objectMapper.valueToTree(report);
        assertEquals(17, orderJson.get("uId").asInt());
        assertEquals(4, orderJson.get("mId").asInt());
        assertEquals(2, orderJson.get("dId").asInt());
        assertEquals(17, registrationJson.get("uId").asInt());
        assertEquals(9, registrationJson.get("aId").asInt());
        assertEquals(17, reportJson.get("uId").asInt());
        assertEquals(2, reportJson.get("dId").asInt());
    }

    @Test
    void registrationViewKeepsFrontendFieldNames() {
        MyActivity registration = new MyActivity();
        registration.setuId(17);
        registration.setaState("未开始");

        JsonNode json = objectMapper.valueToTree(registration);

        assertEquals(17, json.get("uId").asInt());
        assertEquals("未开始", json.get("aState").stringValue());
    }
}
