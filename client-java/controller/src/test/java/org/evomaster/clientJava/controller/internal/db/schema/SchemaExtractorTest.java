package org.evomaster.clientJava.controller.internal.db.schema;

import org.evomaster.clientJava.controller.db.SqlScriptRunner;
import org.evomaster.clientJava.controllerApi.dto.database.*;
import org.evomaster.clientJava.instrumentation.InstrumentingAgent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.*;

public class SchemaExtractorTest {

    private static Connection connection;




    @BeforeAll
    public static void initClass() throws Exception {
        InstrumentingAgent.initP6Spy("org.h2.Driver");

        connection = DriverManager.getConnection("jdbc:h2:mem:db_test", "sa", "");
    }

    @BeforeEach
    public void initTest() throws Exception {

        /*
            Not supported in H2
            SqlScriptRunner.execCommand(connection, "DROP DATABASE db_test;");
            SqlScriptRunner.execCommand(connection, "CREATE DATABASE db_test;");
        */

        //custom H2 command
        SqlScriptRunner.execCommand(connection, "DROP ALL OBJECTS;");
    }

    @Test
    public void testBasic() throws Exception {

        SqlScriptRunner.execCommand(connection, "CREATE TABLE Foo(x INT)");

        DbSchemaDto schema = SchemaExtractor.extract(connection);
        assertNotNull(schema);

        assertAll(() -> assertEquals("public", schema.name.toLowerCase()),
                () -> assertEquals(DatabaseType.H2, schema.databaseType),
                () -> assertEquals(1, schema.tables.size()),
                () -> assertEquals("foo", schema.tables.get(0).name.toLowerCase()),
                () -> assertEquals(1, schema.tables.get(0).columns.size())
        );
    }

    @Test
    public void testTwoTables() throws Exception {
        SqlScriptRunner.execCommand(connection, "CREATE TABLE Foo(x INT); CREATE TABLE Bar(y INT)");

        DbSchemaDto schema = SchemaExtractor.extract(connection);
        assertNotNull(schema);

        assertEquals(2, schema.tables.size());
        assertTrue(schema.tables.stream().map(t -> t.name.toLowerCase()).anyMatch(n -> n.equals("foo")));
        assertTrue(schema.tables.stream().map(t -> t.name.toLowerCase()).anyMatch(n -> n.equals("bar")));
    }


    @Test
    public void testIdentity() throws Exception {
        SqlScriptRunner.execCommand(connection, "CREATE TABLE Foo(" +
                "  id bigint generated by default as identity " +
                ", x int" +
                ", primary key (id) " +
                ");");

        DbSchemaDto schema = SchemaExtractor.extract(connection);

        TableDto table = schema.tables.get(0);
        assertEquals(2, table.columns.size());

        ColumnDto id = table.columns.stream()
                .filter(c -> c.name.equalsIgnoreCase("id"))
                .findAny().get();
        ColumnDto x = table.columns.stream()
                .filter(c -> c.name.equalsIgnoreCase("x"))
                .findAny().get();

        assertEquals("integer", x.type.toLowerCase());
        assertEquals("bigint", id.type.toLowerCase());

        assertFalse(x.autoIncrement);
        assertTrue(id.autoIncrement);
    }


    @Test
    public void testBasicConstraints() throws Exception {

        SqlScriptRunner.execCommand(connection, "CREATE TABLE Foo(" +
                "  id bigint generated by default as identity " +
                ", name varchar(128) not null " +
                ", surname varchar(255) " +
                ", primary key (id) " +
                ");");

        DbSchemaDto schema = SchemaExtractor.extract(connection);

        TableDto table = schema.tables.get(0);
        assertEquals(3, table.columns.size());

        ColumnDto id = table.columns.stream()
                .filter(c -> c.name.equalsIgnoreCase("id"))
                .findAny().get();
        assertTrue(id.autoIncrement);

        ColumnDto name = table.columns.stream()
                .filter(c -> c.name.equalsIgnoreCase("name"))
                .findAny().get();
        assertFalse(name.autoIncrement);
        assertFalse(name.nullable);
        assertEquals(128, name.size);

        ColumnDto surname = table.columns.stream()
                .filter(c -> c.name.equalsIgnoreCase("surname"))
                .findAny().get();
        assertFalse(surname.autoIncrement);
        assertTrue(surname.nullable);
        assertEquals(255, surname.size);
    }



    @Test
    public void testBasicForeignKey() throws Exception {

        SqlScriptRunner.execCommand(connection, "CREATE TABLE Foo(" +
                "  id bigint generated by default as identity " +
                ", barId bigint not null " +
                ");" +
                " CREATE TABLE Bar(id bigint generated by default as identity);" +
                " ALTER TABLE Foo add constraint barIdKey foreign key (barId) references Bar;\n"
        );

        DbSchemaDto schema = SchemaExtractor.extract(connection);
        assertEquals(2, schema.tables.size());

        TableDto bar = schema.tables.stream().filter(t -> t.name.equalsIgnoreCase("Bar")).findAny().get();
        TableDto foo = schema.tables.stream().filter(t -> t.name.equalsIgnoreCase("Foo")).findAny().get();

        assertEquals(0, bar.foreignKeys.size());
        assertEquals(1, foo.foreignKeys.size());

        ForeignKeyDto foreignKey = foo.foreignKeys.get(0);

        assertEquals(1, foreignKey.columns.size());
        assertTrue(foreignKey.columns.stream().anyMatch(c -> c.equalsIgnoreCase("barId")));
        assertTrue(foreignKey.targetTable.equalsIgnoreCase("Bar"));
    }

    @Test
    public void testQuizGame() throws Exception {

        InputStream in = SchemaExtractorTest.class.getResourceAsStream("/db_schemas/quizgame.sql");
        SqlScriptRunner.runScript(connection, new InputStreamReader(in));
        in.close();

        DbSchemaDto schema = SchemaExtractor.extract(connection);
        assertEquals(6, schema.tables.size());

        //TODO test all of its content
    }
}