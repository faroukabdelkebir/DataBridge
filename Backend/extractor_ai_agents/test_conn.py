import os
import pyodbc
from dotenv import load_dotenv

load_dotenv(override=True)

BI_HOST = os.getenv("BI_HOST", "127.0.0.1")
BI_PORT = os.getenv("BI_PORT", "1433")
BI_DB = os.getenv("BI_DB", "T24_TEST")
BI_USER = os.getenv("BI_USER", "")
BI_PASS = os.getenv("BI_PASS", "")

# Test 1 : authentification SQL (UID/PWD)
try:
    conn = pyodbc.connect(
        "DRIVER={ODBC Driver 17 for SQL Server};"
        f"SERVER={BI_HOST},{BI_PORT};"
        f"DATABASE={BI_DB};"
        f"UID={BI_USER};"
        f"PWD={BI_PASS};"
        "Connection Timeout=5;"
    )
    print("✅ Test 1 OK - Auth SQL")
    conn.close()
except Exception as e:
    print(f"❌ Test 1 : {e}")

# Test 2 : authentification Windows (Trusted)
try:
    conn = pyodbc.connect(
        "DRIVER={ODBC Driver 17 for SQL Server};"
        f"SERVER={BI_HOST},{BI_PORT};"
        f"DATABASE={BI_DB};"
        "Trusted_Connection=yes;"
        "Connection Timeout=5;"
    )
    print("✅ Test 2 OK - Auth Windows")
    conn.close()
except Exception as e:
    print(f"❌ Test 2 : {e}")

# Test 3 : sans spécifier la DB
try:
    conn = pyodbc.connect(
        "DRIVER={ODBC Driver 17 for SQL Server};"
        f"SERVER={BI_HOST},{BI_PORT};"
        "Trusted_Connection=yes;"
        "Connection Timeout=5;"
    )
    print("✅ Test 3 OK - Sans DB")
    # Lister les bases disponibles
    cursor = conn.cursor()
    cursor.execute("SELECT name FROM sys.databases")
    dbs = [row[0] for row in cursor.fetchall()]
    print(f"   Bases disponibles : {dbs}")
    conn.close()
except Exception as e:
    print(f"❌ Test 3 : {e}")