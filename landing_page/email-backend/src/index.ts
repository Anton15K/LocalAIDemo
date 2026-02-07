import express from "express";
import cors from "cors";
import sqlite3 from "sqlite3";
import { open } from "sqlite";
import path from "path";

const app = express();
const PORT = process.env.PORT || 8000;
const DB_PATH = path.join(__dirname, "..", "waitlist.db");

app.use(cors());
app.use(express.json());

async function initDb() {
  try {
    const db = await open({
      filename: DB_PATH,
      driver: sqlite3.Database,
    });

    await db.exec(`
      CREATE TABLE IF NOT EXISTS waitlist (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        email TEXT UNIQUE NOT NULL,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
      )
    `);

    console.log("SQLite Database initialized successfully at", DB_PATH);
    await db.close();
  } catch (error: any) {
    console.error("Error initializing database:", error);
  }
}

app.post("/waitlist", async (req, res) => {
  const { email } = req.body;

  if (!email || !email.includes("@")) {
    return res.status(400).json({ detail: "Invalid email address" });
  }

  try {
    const db = await open({
      filename: DB_PATH,
      driver: sqlite3.Database,
    });

    await db.run("INSERT INTO waitlist (email) VALUES (?)", [email]);
    await db.close();

    return res.json({ message: "Success", email });
  } catch (error: any) {
    // sqlite "UNIQUE constraint failed: waitlist.email"
    if (error?.message?.includes("UNIQUE constraint failed")) {
      return res.json({ message: "Email already in waitlist", email });
    }

    console.error("Database error:", error);
    return res.status(500).json({ detail: "Internal server error" });
  }
});

app.listen(PORT, async () => {
  await initDb();
  console.log(`Server is running on http://localhost:${PORT}`);
});
