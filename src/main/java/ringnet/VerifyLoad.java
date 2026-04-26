package ringnet;

import io.github.cdimascio.dotenv.Dotenv;
import org.neo4j.driver.*;

import java.util.*;

public class VerifyLoad {

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.load();
        String uri      = dotenv.get("NEO4J_URI",  "bolt://localhost:7687");
        String user     = dotenv.get("NEO4J_USER", "neo4j");
        String password = dotenv.get("NEO4J_PASSWORD");

        try (Driver driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password));
             Session session = driver.session()) {

            printNodeCounts(session);
            List<List<String>> rings = detectFraudRings(session);
            printRingSummary(rings);
        }
    }

    static void printNodeCounts(Session session) {
        System.out.println("--- Node counts ---");
        String[] labels = {"Account", "Phone", "Email", "Device", "Address", "Transaction"};
        for (String label : labels) {
            long count = session.run("MATCH (n:" + label + ") RETURN count(n) AS c")
                    .single().get("c").asLong();
            System.out.printf("  %-15s %d%n", label + ":", count);
        }
    }

    static List<List<String>> detectFraudRings(Session session) {
        List<String> fraudAccounts = session
                .run("MATCH (a:Account {fraud_confirmed: true}) RETURN a.id AS id")
                .list(r -> r.get("id").asString());

        Set<String> visited = new HashSet<>();
        List<List<String>> rings = new ArrayList<>();

        for (String seed : fraudAccounts) {
            if (visited.contains(seed)) continue;

            List<String> component = new ArrayList<>();
            Queue<String> queue = new LinkedList<>();
            queue.add(seed);
            visited.add(seed);

            while (!queue.isEmpty()) {
                String current = queue.poll();
                component.add(current);

                List<String> neighbors = session.run(
                        "MATCH (a:Account {id: $id})" +
                        "-[:HAS_PHONE|HAS_EMAIL|HAS_DEVICE]->(shared)" +
                        "<-[:HAS_PHONE|HAS_EMAIL|HAS_DEVICE]-" +
                        "(b:Account {fraud_confirmed: true}) " +
                        "WHERE b.id <> $id RETURN DISTINCT b.id AS neighborId",
                        Map.of("id", current)
                ).list(r -> r.get("neighborId").asString());

                for (String neighbor : neighbors) {
                    if (!visited.contains(neighbor)) {
                        visited.add(neighbor);
                        queue.add(neighbor);
                    }
                }
            }

            rings.add(component);
        }

        return rings;
    }

    static void printRingSummary(List<List<String>> rings) {
        int largest = rings.stream().mapToInt(List::size).max().orElse(0);

        System.out.println("\n--- Fraud ring summary ---");
        System.out.printf("Fraud rings detected: %d%n", rings.size());
        System.out.printf("Largest ring size:    %d%n", largest);

        System.out.println("\n--- Ring breakdown ---");
        for (int i = 0; i < rings.size(); i++) {
            List<String> ring = rings.get(i);
            System.out.printf("  Ring %d: %d accounts — %s%n", i + 1, ring.size(), ring);
        }
    }
}
