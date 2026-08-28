# 🗄️ H2 Database Setup Guide for IRE (Local Development)

## Overview

This guide helps you set up and use **H2 Database** for local development of the Identity Resolution Engine (IRE). H2 eliminates the need for Oracle setup on your local machine.

---

## ✅ Prerequisites

- **Java 17+** (already in your project)
- **Maven 3.6+**
- **Spring Boot 2.7.18** (already configured)
- H2 dependency (already added to `pom.xml`)

---

## 🚀 Quick Start (3 Steps)

### **Step 1: Run with H2 Profile**

```bash
# Clone the repository
git clone https://github.com/isharryfung/ire-poc.git
cd ire-poc

# Run with H2 profile activated
mvn clean spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=h2"
```

### **Step 2: Verify Database Initialization**

Watch for this log message:
```
╔════════════════════════════════════════════╗
║  H2 Database Configuration Activated       ║
║  ──────────��────────────────────────────  ║
║  Database: In-Memory H2                    ║
║  Mode: Oracle Compatibility                ║
║  Console: http://localhost:8080/h2-console║
║  User: sa (no password)                    ║
╚════════════════════════════════════════════╝
```

### **Step 3: Access H2 Console (Optional)**

1. Open browser: **http://localhost:8080/h2-console**
2. Connection details:
   - **JDBC URL:** `jdbc:h2:mem:ire_db`
   - **User:** `sa`
   - **Password:** (leave blank)
   - **Driver Class:** `org.h2.Driver`
3. Click **Connect**

---

## 🔧 Configuration Files

### **1. application-h2.properties**
Location: `src/main/resources/application-h2.properties`

Key settings:
```properties
# H2 In-Memory Database (MODE=Oracle for Oracle SQL compatibility)
spring.datasource.url=jdbc:h2:mem:ire_db;MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false

# Auto-create schema using Flyway migrations
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration

# H2 Console enabled for inspection
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console

# Debug logging enabled
logging.level.org.hkust.ire=DEBUG
logging.level.org.hibernate.SQL=DEBUG
```

### **2. Flyway Migration Scripts**
Location: `src/main/resources/db/migration/`

All SQL files are automatically executed in order:
- `V1_0_0__create_ire_schema.sql` - Creates all tables, indexes, and loads seed data

---

## 📊 Database Schema

### **Tables Created:**

| Table | Purpose |
|-------|---------|
| `identities` | Golden identity records |
| `identity_links` | Links between source systems and golden records |
| `source_credibility` | Credibility scores for each source system |
| `manual_reviews` | Records requiring manual review |
| `audit_logs` | Audit trail of all operations |

### **Sample Data Loaded:**

After startup, you have:
- 1 sample identity: `GID-001` (John Doe)
- 2 identity links: ADMS and CRM sources
- 7 source credibility records (CRM=1.0, ADMS=0.9, THIRD_PARTY=0.8, etc.)

---

## 🧪 Running Tests with H2

All tests automatically use H2 when you run:

```bash
# Run all tests
mvn clean test

# Run specific test class
mvn test -Dtest=IdentityResolutionServiceWaterfallTest

# Run with coverage
mvn clean test jacoco:report
```

Tests use: `src/main/resources/application-test.properties`
- Database: `jdbc:h2:mem:testdb`
- Fresh schema for each test run
- Automatic cleanup after tests

---

## 🔍 Common Tasks with H2

### **View Data in H2 Console**

1. Go to: http://localhost:8080/h2-console
2. Execute query:
   ```sql
   SELECT * FROM identities;
   SELECT * FROM identity_links;
   SELECT * FROM audit_logs;
   ```

### **Reset Database**

Simply restart the application:
```bash
# Stop current process (Ctrl+C)
# Then restart
mvn clean spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=h2"
```

### **Load Custom Data**

Edit `src/main/resources/db/migration/V1_0_0__create_ire_schema.sql`:
```sql
-- Add more INSERT statements for test data
INSERT INTO identities (golden_id, hkid, email, first_name, last_name, status) VALUES
('GID-002', 'B123456(8)', 'jane.smith@ust.hk', 'Jane', 'Smith', 'ACTIVE'),
('GID-003', 'C123456(9)', 'bob.johnson@ust.hk', 'Bob', 'Johnson', 'ACTIVE');
```

Then restart the application (migrations re-run).

### **Inspect SQL Statements**

Enable SQL logging in `application-h2.properties`:
```properties
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE
```

Then run with:
```bash
mvn clean spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=h2" | grep -E "SQL|Binder"
```

---

## 🌐 Switching Between Databases

### **Local Development (H2)**
```bash
mvn clean spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=h2"
```

### **Development (if Oracle is available)**
```bash
export ORACLE_URL=jdbc:oracle:thin:@localhost:1521:ire
export ORACLE_USER=ire_user
export ORACLE_PASSWORD=your_password

mvn clean spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"
```

### **Testing (H2 - Automatic)**
```bash
mvn test
```

### **Production (Oracle)**
```bash
export ORACLE_URL=jdbc:oracle:thin:@prod-host:1521:ire
export ORACLE_USER=ire_user_prod
export ORACLE_PASSWORD=prod_password

java -jar target/ire-1.0.0-SNAPSHOT.war --spring.profiles.active=prod
```

---

## ⚡ Performance Considerations

| Aspect | H2 | Oracle |
|--------|--|----|
| **Startup Time** | ~2 seconds | ~5-10 seconds |
| **Query Performance** | Instant (in-memory) | Depends on network |
| **Data Persistence** | Lost on restart | Persisted |
| **Development** | ✅ Ideal | ❌ Overkill |
| **Production** | ❌ Not suitable | ✅ Recommended |

---

## 📝 Troubleshooting

### **Issue: "No Spring Boot application found in current directory"**

**Solution:**
```bash
# Ensure you're in the project root
ls pom.xml  # Should exist

# If not, navigate to project root
cd path/to/ire-poc
```

### **Issue: "ERROR - Failed to start application"**

Check logs for:
```bash
# Too many processes using port 8080?
lsof -i :8080

# Kill process using port 8080
kill -9 <PID>

# Then restart
mvn clean spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=h2"
```

### **Issue: "H2 Console at /h2-console returns 404"**

**Solution:** Ensure you're accessing: `http://localhost:8080/h2-console` (not /h2)

### **Issue: "Cannot open database URL 'jdbc:h2:mem:ire_db'"**

**Solution:** Ensure `MODE=Oracle` is in the URL (it's in the config file, so restart should help)

### **Issue: "Flyway migration failed"**

**Solution:** Check migration file syntax:
```bash
# Verify migration file exists
ls src/main/resources/db/migration/

# Check file naming follows Flyway convention: V<version>__<description>.sql
```

---

## 🧠 H2 Oracle Mode Features

The H2 database uses `MODE=Oracle` which provides compatibility with:
- ✅ Oracle's SQL syntax
- ✅ Built-in functions
- ✅ Date/timestamp handling
- ✅ String functions
- ✅ Aggregate functions

**Note:** Not 100% compatible, but sufficient for most applications.

---

## 🔗 Useful Resources

- [H2 Database Documentation](https://www.h2database.com/)
- [H2 Mode: Oracle](https://www.h2database.com/html/grammar.html#set_mode)
- [Flyway Documentation](https://flywaydb.org/)
- [Spring Boot H2 Support](https://spring.io/guides/gs/accessing-data-with-rest/)

---

## ✅ Verification Checklist

After setup, verify everything works:

- [ ] Application starts with H2 profile
- [ ] H2 Console accessible at `http://localhost:8080/h2-console`
- [ ] Can see tables in H2 Console
- [ ] Tests pass: `mvn test`
- [ ] Sample data loaded (GID-001 exists)
- [ ] Audit logs recording operations
- [ ] No errors in application logs

---

## 💡 Tips & Tricks

### **1. Persist H2 Data Between Restarts (Optional)**

Change the URL to use file-based storage:
```properties
spring.datasource.url=jdbc:h2:~/ire_db;MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false
```

### **2. Increase H2 Console Max Connections**

In `application-h2.properties`:
```properties
spring.h2.console.settings.web-allow-others=true
spring.h2.console.settings.trace=false
```

### **3. Add More Test Data**

Run SQL directly in H2 Console:
```sql
INSERT INTO identities (golden_id, hkid, email, first_name, last_name, status)
VALUES ('GID-999', 'Z123456(0)', 'test@ust.hk', 'Test', 'User', 'ACTIVE');
```

### **4. Disable H2 Console in Production**

Already done in `application-prod.properties`:
```properties
spring.h2.console.enabled=false
```

---

## 🎯 Next Steps

1. ✅ **Run the application:** `mvn clean spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=h2"`
2. ✅ **Access H2 Console:** http://localhost:8080/h2-console
3. ✅ **Run tests:** `mvn test`
4. ✅ **Check logs:** Look for ire package logs (DEBUG level)
5. ✅ **Test API endpoints:** Use Postman or curl

---

## 📞 Support

For issues:
1. Check the **Troubleshooting** section above
2. Review application logs in console
3. Check H2 Console for data state
4. Verify `application-h2.properties` configuration
5. Check Flyway migration files in `src/main/resources/db/migration/`

---

**Happy local development with H2! 🚀**
