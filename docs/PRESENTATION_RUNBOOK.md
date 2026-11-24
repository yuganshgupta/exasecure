# ExaSecure V2 — Presentation Runbook

## Prerequisites

1. **MySQL 8.0+** running on `localhost:3306`.
2. **A provisioned database** (e.g. `secure_exam_db`) with a MySQL user that
   has full privileges on that database.
3. **Windows x64** with the ExaSecure Student and Operator installers
   already installed, **or** the development checkout with JDK 25.

## Option A: Installed (Recommended for Presentation)

### 1. Configure the Operator

Launch **ExaSecure Operator** from the Start Menu → ExaSecure group.

In the Control Center window, click **Configure Installed Server** and enter:

| Field | Value |
| --- | --- |
| Database URL | `jdbc:mysql://localhost:3306/secure_exam_db` |
| Database User | *(your MySQL user)* |
| Database Password | *(your MySQL password)* |
| Server Port | `8089` (or your preferred port) |

Click **Save**. This writes to `%LOCALAPPDATA%\ExaSecure\Operator\server\db.properties`.

### 2. Start the Server

Click **Start Server** in Control Center. Wait for the status to show
**Server: Running (Port 8089)**. The first start applies database migrations
automatically.

### 3. Create the First Admin User

A fresh database has **no** default ExaSecure application users. Use the
development bootstrap utility once to seed an initial admin:

```powershell
cd d:\exasecure
.\mvnw.cmd exec:java -pl exasecure-server -Dexec.mainClass=com.examsystem.server.DevBootstrap
```

This creates a demo admin (`admin`/`admin123`) and demo student
(`student1`/`pass123`) if they do not already exist, plus a sample exam.
It exits immediately after seeding.

> **Important**: DevBootstrap is a development utility. For installed
> deployments, it uses the same `db.properties` as the development Server.
> Point it at the correct database.

### 4. Open Admin

Click **Start Admin** in Control Center, or launch separately.
Log in with `admin` / `admin123` (or your created credentials).

### 5. Prepare a Demonstration Exam

In Admin → **Exams** tab:
- If DevBootstrap created a "Sample Exam", it is ready to use.
- Otherwise: click **New Exam**, fill in title, duration (e.g. 10 minutes),
  add 3–5 questions with SINGLE or MULTI choice options, mark correct answers,
  and save.

Ensure the exam is **Active** (not deactivated).

### 6. Prepare a Demonstration Student

In Admin → **Users** tab:
- If DevBootstrap created `student1`, it is ready.
- Otherwise: click **New User**, set username, full name, password, role
  = STUDENT, and save.

### 7. Launch the Student Application

Launch **ExaSecure Student** from Start Menu → ExaSecure.
On first run, enter the Server URL: `http://localhost:8089` (or the
LAN IP if on a different machine) and save.

### 8. Demonstrate the Exam Flow

1. **Log in** as `student1` / `pass123`.
2. The dashboard shows available exams. Click **Start Exam** on the demo exam.
3. **Answer questions**: click options, navigate with the question palette.
4. **Demonstrate autosave**: change an answer; the status bar shows save state.
5. **Demonstrate review flags**: click the flag icon on a question.
6. **Submit**: click Submit, confirm in the dialog, see the result summary.
7. Return to the dashboard to see attempt history.

### 9. Demonstrate Grading and Results

Switch to Admin:
1. Go to **Attempts & Results** tab.
2. Select the demo exam from the dropdown.
3. The student's attempt appears with score and status.
4. Click on the attempt row for detailed answers.

### 10. Demonstrate Proctoring

1. Go to **Proctoring Review** tab in Admin.
2. Select the demo exam.
3. If the student lost focus during the exam, incidents appear with timestamps.
4. Click an incident to view the captured screenshot evidence.
5. Use zoom controls and the evidence inspector.

### 11. Shut Down

1. In Control Center, click **Stop Server** (or close Control Center,
   which stops its owned Server and Admin).
2. Close Student.

---

## Option B: Development Mode

If the installed package is not available:

```powershell
# Terminal 1: Server
.\mvnw.cmd exec:java -pl exasecure-server

# Terminal 2: Student
.\mvnw.cmd exec:java -pl exasecure-student-client

# Terminal 3: Admin
.\mvnw.cmd exec:java -pl exasecure-admin-client

# Terminal 4: Control Center (optional)
.\mvnw.cmd exec:java -pl exasecure-launcher
```

Ensure `db.properties` exists in the repository root with valid credentials.
Run `.\mvnw.cmd install -DskipTests` first if modules have changed.

---

## Troubleshooting

### Server fails to start

| Symptom | Fix |
| --- | --- |
| "Connection refused" or MySQL error | Verify MySQL is running: `Get-Service MySQL80` |
| "Access denied" | Check `db.properties` credentials |
| Port already in use | Change `server.port` in config, or stop the other process |
| Schema migration error | Use a fresh database or check `docs/database/r3-migrations.md` |

### Student cannot connect

| Symptom | Fix |
| --- | --- |
| "Connection refused" | Verify Server is running: `http://localhost:8089/health` |
| "Server not reachable" | Check the configured Server URL in Student settings |
| Wrong URL | In Student, go to settings and correct the Server URL |

### Database issues

- **Fresh database**: Run DevBootstrap to seed initial data.
- **Readiness check**: `http://localhost:8089/ready` verifies Server→MySQL path.
- **Reset**: Drop and recreate the database; migrations reapply on next start.

### Admin login rejected

- Ensure the user has role `ADMIN`.
- DevBootstrap creates `admin` / `admin123` with ADMIN role.
- Students cannot log in to Admin.

### Proctoring screenshots missing

- Screenshots require desktop access; headless environments return no evidence.
- On macOS, grant Screen Recording permission.
- Screenshots are supporting evidence; incidents still record without them.
