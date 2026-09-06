package com.cecsmsserve;

import com.cecsmsserve.entity.Comment;
import com.cecsmsserve.entity.vo.CommentVo;
import com.cecsmsserve.mapper.CommentMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CommentOrderingTests {
    @Test
    void actualMapperQueriesUseStableChronologyForRootsRepliesAndPersonalRecords() throws Exception {
        PooledDataSource dataSource = new PooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:comments_" + UUID.randomUUID() + ";MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1", "sa", "");
        try {
            try (Connection connection = dataSource.getConnection(); Statement sql = connection.createStatement()) {
                sql.execute("CREATE TABLE user (id INT PRIMARY KEY, name VARCHAR(40), telephone VARCHAR(20))");
                sql.execute("INSERT INTO user VALUES (17, 'Test user', '00000000000')");
                sql.execute("""
                        CREATE TABLE comment (
                            id INT PRIMARY KEY, content VARCHAR(200), user_id INT, user_name VARCHAR(40),
                            user_telephone VARCHAR(20), parent_id INT, reply_to INT, reply_to_name VARCHAR(40),
                            create_time TIMESTAMP, update_time TIMESTAMP, is_deleted INT)
                        """);
                // Equal timestamps are deliberately inserted out of ID order.
                for (int id : new int[]{7, 3, 11}) {
                    sql.execute("INSERT INTO comment (id,user_id,create_time,is_deleted) VALUES (" + id + ",17,'2027-04-20 20:41:55',0)");
                }
                sql.execute("INSERT INTO comment (id,user_id,create_time,is_deleted) VALUES (99,17,'2026-04-20 20:41:55',0)");
                for (int id : new int[]{22, 20}) {
                    sql.execute("INSERT INTO comment (id,user_id,parent_id,create_time,is_deleted) VALUES (" + id + ",17,11,'2027-04-20 21:00:00',0)");
                }
                sql.execute("INSERT INTO comment (id,user_id,parent_id,create_time,is_deleted) VALUES (21,17,11,'2027-04-20 21:00:00',1)");
            }
            Configuration configuration = new Configuration(new Environment("comment-order-test", new JdbcTransactionFactory(), dataSource));
            try (InputStream input = getClass().getResourceAsStream("/mapper/CommentMapper.xml")) {
                assertNotNull(input);
                new XMLMapperBuilder(input, configuration, "mapper/CommentMapper.xml", configuration.getSqlFragments()).parse();
            }
            try (SqlSession session = new SqlSessionFactoryBuilder().build(configuration).openSession()) {
                CommentMapper mapper = session.getMapper(CommentMapper.class);
                assertEquals(List.of(11, 7, 3, 99), mapper.selectCommentListWithUser().stream().map(CommentVo::getId).toList());
                assertEquals(List.of(20, 22), mapper.selectRepliesByParentId(11).stream().map(CommentVo::getId).toList());
                assertEquals(List.of(20, 21, 22), mapper.selectRepliesIncludingDeleted(11).stream().map(CommentVo::getId).toList());
                assertEquals(List.of(22, 21, 20, 11, 7, 3, 99), mapper.selectManagementListWithUser().stream().map(CommentVo::getId).toList());
                assertEquals(List.of(22, 20, 11, 7, 3, 99), mapper.selectByUserId(17).stream().map(Comment::getId).toList());
            }
        } finally {
            dataSource.forceCloseAll();
        }
    }
}
