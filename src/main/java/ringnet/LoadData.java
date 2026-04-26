package ringnet;

import io.github.cdimascio.dotenv.Dotenv;
import org.neo4j.driver.*;

public class LoadData {

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.load();
        String uri      = dotenv.get("NEO4J_URI",  "bolt://localhost:7687");
        String user     = dotenv.get("NEO4J_USER", "neo4j");
        String password = dotenv.get("NEO4J_PASSWORD");

        try (Driver driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password));
            Session session = driver.session()) {

            step("Creating constraints and indexes", () -> {
                session.run("CREATE CONSTRAINT account_id IF NOT EXISTS FOR (a:Account) REQUIRE a.id IS UNIQUE");
                session.run("CREATE CONSTRAINT phone_number IF NOT EXISTS FOR (p:Phone) REQUIRE p.number IS UNIQUE");
                session.run("CREATE CONSTRAINT email_address IF NOT EXISTS FOR (e:Email) REQUIRE e.address IS UNIQUE");
                session.run("CREATE CONSTRAINT device_id IF NOT EXISTS FOR (d:Device) REQUIRE d.device_id IS UNIQUE");
                session.run("CREATE CONSTRAINT transaction_id IF NOT EXISTS FOR (t:Transaction) REQUIRE t.id IS UNIQUE");
            });

            step("Loading accounts", () ->
                session.run("""
                    LOAD CSV WITH HEADERS FROM 'file:///accounts.csv' AS row
                    MERGE (a:Account {id: row.id})
                    SET a.name           = row.name,
                        a.created_at     = row.created_at,
                        a.fraud_confirmed = (row.fraud_confirmed = 'true')
                    """).consume());

            step("Loading phones → HAS_PHONE", () ->
                session.run("""
                    LOAD CSV WITH HEADERS FROM 'file:///phones.csv' AS row
                    MATCH (a:Account {id: row.account_id})
                    MERGE (p:Phone {number: row.number})
                    MERGE (a)-[r:HAS_PHONE]->(p)
                    SET r.created_at = row.created_at
                    """).consume());

            step("Loading emails → HAS_EMAIL", () ->
                session.run("""
                    LOAD CSV WITH HEADERS FROM 'file:///emails.csv' AS row
                    MATCH (a:Account {id: row.account_id})
                    MERGE (e:Email {address: row.address})
                    MERGE (a)-[r:HAS_EMAIL]->(e)
                    SET r.created_at = row.created_at
                    """).consume());

            step("Loading devices → USED_DEVICE", () ->
                session.run("""
                    LOAD CSV WITH HEADERS FROM 'file:///devices.csv' AS row
                    MATCH (a:Account {id: row.account_id})
                    MERGE (d:Device {device_id: row.device_id})
                    SET d.device_type = row.device_type
                    MERGE (a)-[r:USED_DEVICE]->(d)
                    SET r.last_seen = row.last_seen
                    """).consume());

            step("Loading addresses → HAS_ADDRESS", () ->
                session.run("""
                    LOAD CSV WITH HEADERS FROM 'file:///addresses.csv' AS row
                    MATCH (a:Account {id: row.account_id})
                    CREATE (addr:Address {
                        street: row.street,
                        city:   row.city,
                        zip:    row.zip,
                        type:   row.type
                    })
                    CREATE (a)-[:HAS_ADDRESS]->(addr)
                    """).consume());

            step("Loading transactions → SENT", () ->
                session.run("""
                    LOAD CSV WITH HEADERS FROM 'file:///transactions.csv' AS row
                    MATCH (from:Account {id: row.from_account_id})
                    MATCH (to:Account   {id: row.to_account_id})
                    MERGE (t:Transaction {id: row.id})
                    SET t.amount    = toFloat(row.amount),
                        t.timestamp = row.timestamp,
                        t.status    = row.status
                    MERGE (from)-[:SENT]->(t)
                    MERGE (t)-[:TO]->(to)
                    """).consume());
        }
    }

    @FunctionalInterface
    interface Step { void run() throws Exception; }

    static void step(String label, Step s) {
        System.out.print(label + "... ");
        System.out.flush();
        try {
            s.run();
            System.out.println("done.");
        } catch (Exception e) {
            System.out.println("FAILED: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
