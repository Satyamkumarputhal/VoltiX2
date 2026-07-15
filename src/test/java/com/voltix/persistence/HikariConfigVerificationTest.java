package com.voltix.persistence;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class HikariConfigVerificationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void testHikariConnectionTimeoutIs250ms() {
        assertTrue(dataSource instanceof HikariDataSource, "DataSource should be an instance of HikariDataSource");
        HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
        System.out.println("====== HIKARI CONNECTION TIMEOUT: " + hikariDataSource.getConnectionTimeout() + "ms ======");
        System.out.println("====== HIKARI MAX POOL SIZE: " + hikariDataSource.getMaximumPoolSize() + " ======");
        assertEquals(250, hikariDataSource.getConnectionTimeout(), "Hikari connection timeout should be configured to 250ms");
    }
}
