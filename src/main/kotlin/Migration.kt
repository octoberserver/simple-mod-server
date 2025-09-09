package org.octsrv

import org.ktorm.database.Database
import org.ktorm.schema.*
import java.sql.ResultSet

object Migration {
    fun migrateTable(database: Database, table: Table<*>) {
        database.useConnection { conn ->
            val meta = conn.metaData

            // Check if table exists
            val rs: ResultSet = meta.getTables(null, null, table.tableName, null)
            if (!rs.next()) {
                // Table does not exist -> create it
                val createSql = buildString {
                    append("CREATE TABLE ${table.tableName} (")
                    table.columns.forEachIndexed { index, col ->
                        append("${col.name} ${sqlType(col)}")
                        if (table.primaryKeys.contains(col)) append(" PRIMARY KEY")
                        if (index < table.columns.size - 1) append(", ")
                    }
                    append(");")
                }
                conn.createStatement().executeUpdate(createSql)
                println("Table ${table.tableName} created.")
            } else {
                println("Table ${table.tableName} exists, checking columns...")

                // Get existing columns
                val existingCols = mutableSetOf<String>()
                val rsCols = meta.getColumns(null, null, table.tableName, null)
                while (rsCols.next()) {
                    existingCols.add(rsCols.getString("COLUMN_NAME"))
                }

                // Add missing columns
                table.columns.forEach { col ->
                    if (!existingCols.contains(col.name)) {
                        val sql = "ALTER TABLE ${table.tableName} ADD COLUMN ${col.name} ${sqlType(col)};"
                        conn.createStatement().executeUpdate(sql)
                        println("Added column ${col.name} to ${table.tableName}")
                    }
                }
            }
        }
    }

    // Map Ktorm column types to SQL
    fun sqlType(col: Column<*>): String = when (col.sqlType) {
        is IntSqlType -> "INT"
        is LongSqlType -> "BIGINT"
        is VarcharSqlType -> "VARCHAR(255)"
        is BooleanSqlType -> "BOOLEAN"
        is DateSqlType -> "DATE"
        is TimestampSqlType -> "TIMESTAMP"
        else -> throw IllegalArgumentException("Unsupported column type: ${col.sqlType}")
    }
}