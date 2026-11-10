package com.vku.pulseoj.backend.db;

import com.vku.pulseoj.common.dto.ContestantDTO;
import com.vku.pulseoj.common.dto.ProblemDTO;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class NeonConnectionTest {

    @Test
    public void testNeonPostgreSqlLiveConnection() throws Exception {
        NeonDataSourceConfig.init();
        assertFalse(NeonDataSourceConfig.isMockMode(), "Should not be in mock mode when valid NEON_DB_URL is provided");

        try (Connection conn = NeonDataSourceConfig.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT current_database(), current_user, version()")) {
            assertTrue(rs.next());
            System.out.println(">>> Connected Database: " + rs.getString(1));
            System.out.println(">>> Current User: " + rs.getString(2));
            System.out.println(">>> PostgreSQL Version: " + rs.getString(3));
        }

        // Test ContestantAccountDAO
        ContestantAccountDAO contestantDAO = new ContestantAccountDAO();
        List<ContestantDTO> contestants = contestantDAO.getAllContestants();
        assertNotNull(contestants);
        System.out.println(">>> Contestants count in Neon DB: " + contestants.size());
        for (ContestantDTO c : contestants) {
            System.out.println("    - " + c.getUsername() + " : " + c.getFullName());
        }

        // Test ProblemDAO
        ProblemDAO problemDAO = new ProblemDAO();
        List<ProblemDTO> problems = problemDAO.getAllProblems();
        assertNotNull(problems);
        System.out.println(">>> Problems count in Neon DB: " + problems.size());
        for (ProblemDTO p : problems) {
            System.out.println("    - [" + p.getCode() + "] " + p.getTitle());
        }

        NeonDataSourceConfig.close();
    }
}
