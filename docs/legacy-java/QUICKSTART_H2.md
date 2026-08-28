# ⚡ Quick Start - H2 Database for IRE

**Get up and running in 2 minutes!**

---

## 🚀 One Command Startup

```bash
mvn clean spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=h2"
```

**That's it!** Your application will start with H2 database.

---

## ✅ What Happens Automatically

When you run the command above:

1. ✅ H2 in-memory database starts
2. ✅ Flyway runs migrations (`V1_0_0__create_ire_schema.sql`)
3. ✅ Database schema is created (5 tables)
4. ✅ Sample data is loaded
5. ✅ Application starts on `http://localhost:8080`
6. ✅ H2 Console available at `http://localhost:8080/h2-console`

---

## 📊 Console Access

### Open H2 Console:
- **URL:** http://localhost:8080/h2-console
- **JDBC URL:** `jdbc:h2:mem:ire_db`
- **User:** `sa`
- **Password:** (leave blank)
- **Driver Class:** `org.h2.Driver`

Click **Connect** and you can inspect your database!

---

## 🧪 Run Tests

```bash
# Run all tests
mvn test

# Run specific test
mvn test -Dtest=IdentityResolutionServiceWaterfallTest

# Run with coverage
mvn clean test jacoco:report
```

**Tests automatically use H2** - no setup needed!

---

## 📋 Quick Tasks

### View Sample Data
```sql
-- In H2 Console, run these queries:
SELECT * FROM identities;
SELECT * FROM identity_links;
SELECT * FROM source_credibility;
```

### Reset Database
Just restart the application (Ctrl+C, then run the command again)

### Add Test Data
Edit `V1_0_0__create_ire_schema.sql`, add your INSERT statements, then restart.

---

## 🆘 Common Issues

### Port 8080 Already in Use?
```bash
# Kill process using port 8080
lsof -i :8080
kill -9 <PID>

# Then restart
mvn clean spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=h2"
```

### Can't Access H2 Console?
- Ensure URL is `http://localhost:8080/h2-console` (not `/h2`)
- Check application is running (look for banner in console)
- Verify `application-h2.properties` exists

### Tests Failing?
```bash
# Clean rebuild
mvn clean test

# If still failing, check H2 Console for data state
# Reset database by restarting app
```

---

## 📚 More Info

- **Full Guide:** See `H2_SETUP_GUIDE.md`
- **Configuration:** See `src/main/resources/application-h2.properties`
- **Schema:** See `src/main/resources/db/migration/V1_0_0__create_ire_schema.sql`

---

## ✨ Key Features

✅ **Zero Oracle Setup** - Works completely local  
✅ **Fast Development** - In-memory database  
✅ **Auto-Migration** - Flyway handles schema  
✅ **Sample Data** - Pre-loaded for testing  
✅ **Web Console** - Inspect data anytime  
✅ **Full SQL Logging** - Debug queries easily  

---

## 🎯 You're Ready!

Everything is configured. Just run:

```bash
mvn clean spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=h2"
```

Then visit: http://localhost:8080/h2-console

**Happy coding! 🚀**
