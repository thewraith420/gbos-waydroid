package com.gbos.dbexec;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * Tiny SQLite CLI for the device (no sqlite3 binary there), run with app_process as root. It opens the database through Android's own SQLite
 * (WAL-safe while the owning app keeps it open), so no file swapping is needed.
 *   dbexec <db> query  "<select ...>"            print rows (tab separated, header first)
 *   dbexec <db> exec   "<stmt>" ["<stmt>" ...]   run the statements in ONE transaction, foreign keys ON, then integrity + FK check
 *   dbexec <db> file   <script.sql>              same as exec, statements read from a file ("--" comment lines ignored)
 *   dbexec <db> backup <dest.db>                 consistent copy via VACUUM INTO
 * Used by host/provision/gb-userfixes.sh (the Play services flag overrides).
 */
public class Main {
    public static void main(String[] a) throws Exception {
        if (a.length < 3) { System.err.println("usage: dbexec <db> query|exec|backup ..."); System.exit(2); }
        SQLiteDatabase db = SQLiteDatabase.openDatabase(a[0], null, a[1].equals("query") ? SQLiteDatabase.OPEN_READONLY : SQLiteDatabase.OPEN_READWRITE);
        try {
            if (!a[1].equals("query")) db.execSQL("PRAGMA foreign_keys=ON");
            if (a[1].equals("query")) {
                print(db.rawQuery(a[2], null));
            } else if (a[1].equals("backup")) {
                db.execSQL("VACUUM INTO '" + a[2].replace("'", "''") + "'");
                System.out.println("backup written: " + a[2]);
            } else if (a[1].equals("exec") || a[1].equals("file")) {
                java.util.List<String> stmts = new java.util.ArrayList<String>();
                if (a[1].equals("file")) {          // dbexec <db> file <script.sql>: statements separated by ";" at line end, "--" comment lines ignored
                    StringBuilder sb = new StringBuilder();
                    for (String line : new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(a[2])), "UTF-8").split("\n"))
                        if (!line.trim().startsWith("--")) sb.append(line).append('\n');
                    for (String st : sb.toString().split(";\\s*\\n")) { st = st.trim(); if (st.endsWith(";")) st = st.substring(0, st.length() - 1).trim(); if (!st.isEmpty()) stmts.add(st); }
                } else for (int i = 2; i < a.length; i++) stmts.add(a[i]);
                db.beginTransaction();
                try {
                    for (String st : stmts) { db.execSQL(st); System.out.println("ok: " + st); }
                    Cursor c = db.rawQuery("PRAGMA foreign_key_check", null);
                    boolean bad = c.getCount() > 0;
                    if (bad) { print(c); System.out.println("FOREIGN KEY VIOLATIONS: rolling back"); } else c.close();
                    if (!bad) { db.setTransactionSuccessful(); System.out.println("committed"); }
                } finally { db.endTransaction(); }
                print(db.rawQuery("PRAGMA integrity_check", null));
            }
        } finally { db.close(); }
    }

    static void print(Cursor c) {
        String[] n = c.getColumnNames();
        System.out.println(String.join("\t", n));
        while (c.moveToNext()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < n.length; i++) { if (i > 0) sb.append('\t'); sb.append(c.getType(i) == Cursor.FIELD_TYPE_BLOB ? "<blob>" : c.getString(i)); }
            System.out.println(sb);
        }
        c.close();
    }
}
