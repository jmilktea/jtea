import java.sql.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class SqlIndexChecker4j {
    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("Usage: java -cp <mysql-jar>;. ExplainRunner <url> <user> <pass> <sqlFile>");
            System.exit(1);
        }

        String url = args[0];
        String user = args[1];
        String pass = args[2];
        String sqlFile = args[3];

        Class.forName("com.mysql.cj.jdbc.Driver");

        String content = new String(Files.readAllBytes(Paths.get(sqlFile)), StandardCharsets.UTF_8);
        String[] sqls = content.split("\\r?\\n---SQL_SEPARATOR---\\r?\\n");

        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            conn.setReadOnly(true);
            conn.setAutoCommit(false);

            try (Statement initStmt = conn.createStatement()) {
                initStmt.execute("SET SESSION TRANSACTION READ ONLY");
            }

            for (String sql : sqls) {
                sql = sql.trim();
                if (sql.isEmpty()) continue;

                System.out.println("===EXPLAIN_START===");
                System.out.println("SQL:" + sql.replaceAll("[\\r\\n]+", " ").replaceAll("\\s+", " ").trim());

                String explainSql = "EXPLAIN " + sql;

                String check = explainSql.replaceAll("[\\s]+", " ").trim().toUpperCase();
                if (!check.startsWith("EXPLAIN ")) {
                    System.out.println("STATUS:ERROR:Blocked - statement does not start with EXPLAIN");
                    System.out.println("===EXPLAIN_END===");
                    continue;
                }
                if (check.contains(";")) {
                    System.out.println("STATUS:ERROR:Blocked - semicolons are not allowed (prevents injection)");
                    System.out.println("===EXPLAIN_END===");
                    continue;
                }

                try (Statement stmt = conn.createStatement()) {
                    ResultSet rs = stmt.executeQuery(explainSql);
                    ResultSetMetaData meta = rs.getMetaData();
                    int colCount = meta.getColumnCount();

                    StringBuilder header = new StringBuilder("HEADER:");
                    for (int i = 1; i <= colCount; i++) {
                        if (i > 1) header.append("\t");
                        header.append(meta.getColumnLabel(i));
                    }
                    System.out.println(header.toString());

                    while (rs.next()) {
                        StringBuilder row = new StringBuilder("ROW:");
                        for (int i = 1; i <= colCount; i++) {
                            if (i > 1) row.append("\t");
                            String val = rs.getString(i);
                            row.append(val != null ? val : "NULL");
                        }
                        System.out.println(row.toString());
                    }

                    System.out.println("STATUS:OK");
                    rs.close();
                } catch (SQLException e) {
                    System.out.println("STATUS:ERROR:" + e.getMessage().replaceAll("[\\r\\n]+", " "));
                }

                System.out.println("===EXPLAIN_END===");
            }
        }
    }
}
