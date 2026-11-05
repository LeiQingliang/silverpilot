package com.cecsmsserve.service;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgentFeedbackServiceTests {
    private static final String RUN = "0123456789abcdef0123456789abcdef";
    private JdbcTemplate jdbc;
    private AgentFeedbackService service;
    private JdbcDataSource dataSource;

    @BeforeEach
    void setUp() {
        JdbcDataSource source = new JdbcDataSource();
        dataSource = source;
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        ResourceDatabasePopulator schema = new ResourceDatabasePopulator(new ClassPathResource("schema.sql"));
        schema.execute(source);
        schema.execute(source);
        jdbc = new JdbcTemplate(source);
        service = new AgentFeedbackService(jdbc);
        jdbc.update("""
                INSERT INTO agent_run(run_id,user_id,provider,model,input_modality,status)
                VALUES (?,7,'deepseek','model','TEXT','COMPLETED')
                """, RUN);
    }

    @Test
    void requiresExistingRunAndKnownCategories() {
        assertThrows(IllegalArgumentException.class, () -> service.create("' OR 1=1", "TOOL_ERROR", "P1", 1));
        assertThrows(IllegalArgumentException.class, () -> service.create("a".repeat(32), "TOOL_ERROR", "P1", 1));
        assertThrows(IllegalArgumentException.class, () -> service.create(RUN, "raw health content", "P1", 1));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM agent_feedback", Integer.class));
    }

    @Test
    void requiresOrderedStateTransitionsAndEvidenceBeforeVerifiedClosure() {
        var created = service.create(RUN, "CONFIRMATION_UX", "P2", 1);
        assertThrows(IllegalArgumentException.class,
                () -> service.transition(created.id(), 0, "VERIFIED", "commit:abcdef0", "eval:case1", true, 1));
        var triaged = service.transition(created.id(), 0, "TRIAGED", null, null, false, 2);
        assertEquals(2, triaged.ownerId());
        assertThrows(IllegalArgumentException.class,
                () -> service.transition(created.id(), 1, "FIXED", null, null, false, 2));
        var fixed = service.transition(created.id(), 1, "FIXED", "commit:abcdef0", null, false, 2);
        assertEquals("FIXED", fixed.status());
        assertThrows(IllegalArgumentException.class,
                () -> service.transition(created.id(), 2, "VERIFIED", null, null, true, 2));
        assertThrows(IllegalArgumentException.class,
                () -> service.transition(created.id(), 2, "VERIFIED", null, "eval:case1", false, 2));
        var verified = service.transition(created.id(), 2, "VERIFIED", null, "eval:case1", true, 2);
        assertEquals("VERIFIED", verified.status());
        assertEquals("commit:abcdef0", verified.changeRef());
        assertEquals("eval:case1", verified.evidenceRef());
        assertEquals(100.0, service.summary(7).get("verifiedClosureRate"));
        var reopened = service.transition(created.id(), 3, "TRIAGED", null, null, false, 1);
        assertNull(reopened.changeRef());
        assertNull(reopened.evidenceRef());
        assertEquals(0.0, service.summary(7).get("verifiedClosureRate"));
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM agent_feedback_event", Integer.class));
    }

    @Test
    void rejectsStaleVersionWithoutOverwritingAnotherOperator() {
        var created = service.create(RUN, "TOOL_ERROR", "P1", 1);
        service.transition(created.id(), 0, "TRIAGED", null, null, false, 1);
        assertThrows(AgentFeedbackService.Conflict.class,
                () -> service.transition(created.id(), 0, "TRIAGED", null, null, false, 2));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM agent_feedback_event", Integer.class));
    }

    @Test
    void separatesWriteOutcomesAndDerivesExpiredPendingWithoutMutatingHistory() {
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("""
                INSERT INTO agent_action(request_key,user_id,tool_name,summary,requires_confirmation,status,expires_at)
                VALUES ('a',7,'book_service','fixture',1,'PENDING',?)
                """, now.minusMinutes(1));
        jdbc.update("""
                INSERT INTO agent_action(request_key,user_id,tool_name,summary,requires_confirmation,status)
                VALUES ('b',7,'book_service','fixture',1,'SUCCEEDED'),
                       ('c',7,'list_services','fixture',0,'SUCCEEDED')
                """);
        Map<String, Object> outcomes = service.actionOutcomes(7);
        assertEquals(2L, outcomes.get("total"));
        assertEquals(Map.of("EXPIRED", 1L, "SUCCEEDED", 1L), outcomes.get("statuses"));
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM agent_action WHERE request_key = 'a'", String.class));
        assertNull(service.summary(7).get("verifiedClosureRate"));
    }

    @Test
    void listLimitDoesNotLimitAggregateDenominator() {
        for (int index = 0; index < 101; index++) service.create(RUN, "KNOWLEDGE_GAP", "P3", 1);
        assertTrue(service.page(7).truncated());
        assertEquals(100, service.page(7).items().size());
        assertEquals(101L, service.summary(7).get("total"));
    }

    @Test
    void rollsBackStateAndCreationWhenAuditEventCannotBePersisted() {
        TransactionInterceptor transaction = new TransactionInterceptor();
        transaction.setTransactionManager(new DataSourceTransactionManager(dataSource));
        transaction.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory factory = new ProxyFactory(service);
        factory.setProxyTargetClass(true);
        factory.addAdvice(transaction);
        AgentFeedbackService transactional = (AgentFeedbackService) factory.getProxy();
        var created = transactional.create(RUN, "TOOL_ERROR", "P1", 1);
        jdbc.execute("DROP TABLE agent_feedback_event");
        assertThrows(DataAccessException.class,
                () -> transactional.transition(created.id(), 0, "TRIAGED", null, null, false, 1));
        assertEquals("OPEN", jdbc.queryForObject("SELECT status FROM agent_feedback WHERE feedback_id = ?", String.class, created.id()));
        assertEquals(0, jdbc.queryForObject("SELECT version FROM agent_feedback WHERE feedback_id = ?", Integer.class, created.id()));
        assertThrows(DataAccessException.class, () -> transactional.create(RUN, "TOOL_ERROR", "P2", 1));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM agent_feedback", Integer.class));
    }
}
