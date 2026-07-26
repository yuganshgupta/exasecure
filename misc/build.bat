@echo off
setlocal
echo Creating Secure Examination System project structure...

REM Create the main source root
mkdir src

REM Create package structure
set "BASE_PATH=src\com\examsystem"
mkdir "%BASE_PATH%"
mkdir "%BASE_PATH%\db"
mkdir "%BASE_PATH%\models"
mkdir "%BASE_PATH%\dao"
mkdir "%BASE_PATH%\services"
mkdir "%BASE_PATH%\ui"

REM Create empty .java files
echo Creating .java files...
type nul > "%BASE_PATH%\Main.java"
type nul > "%BASE_PATH%\db\DatabaseConnector.java"

for %%f in (User Exam Question Option ExamAttempt StudentAnswer AttemptSummary) do (
    type nul > "%BASE_PATH%\models\%%f.java"
)

for %%f in (UserDAO ExamDAO QuestionDAO OptionDAO ExamAttemptDAO StudentAnswerDAO) do (
    type nul > "%BASE_PATH%\dao\%%f.java"
)

for %%f in (AuthService AdminService StudentService) do (
    type nul > "%BASE_PATH%\services\%%f.java"
)

for %%f in (AdminMenu StudentMenu) do (
    type nul > "%BASE_PATH%\ui\%%f.java"
)

REM Create the SQL file
type nul > database.sql

echo.
echo Project structure created successfully.
echo.
echo Next steps:
echo 1. Add your mysql-connector-j.jar to the (new) 'lib' folder.
echo 2. Fill the generated .java files with the code.
echo 3. Run the database.sql script in MySQL.
echo.
endlocal
pause