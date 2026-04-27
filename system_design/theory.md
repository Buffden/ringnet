# Theory — Fraud Ring Detection with Graph Databases

---

## Fraud

Financial fraud means deceiving a system or person to gain money illegally. The most damaging forms are not individual bad actors but organized rings. **Account fraud** involves creating fake accounts using fabricated or stolen identities. **Synthetic identity fraud** goes further — combining a real identifier like a social security number with a fake name and address to create a person who does not exist but passes identity checks. Both are usually the setup phase for a **fraud ring**: a coordinated group of fake or compromised accounts all controlled by the same actor, operating as a network rather than as isolated accounts.

What fraud rings do with money follows a predictable three-stage pattern called **money laundering**:

- **Placement** — dirty money enters the financial system
- **Layering** — money moves repeatedly between accounts to obscure where it came from
- **Integration** — the money is withdrawn in a form that appears legitimate

The heavy intra-ring transactions in this project simulate the layering stage. The ring-to-legit transactions simulate a **bust-out**: accounts that build up apparent legitimacy over time, then transfer out large amounts before disappearing.

---

## The Graph Argument

The reason fraud rings are hard to detect in relational databases is not a query language problem — it is a data model problem. In a relational database, connections between entities are represented as foreign keys. They do not exist as data; they are derived at query time through joins. To find all accounts connected through shared phone numbers, you join accounts to phones, then self-join accounts where the phone matches, then repeat for email and device, then recurse to find second and third-degree connections. At 3–4 hops, this requires recursive CTEs. Query complexity grows exponentially with hop depth. At scale, it becomes impractical.

In a property graph, connections are first-class data. They are stored as edges, not derived. Traversing four hops costs the same as traversing one hop because each node stores direct pointers to its neighbors. This property is called **index-free adjacency** — the cost of following a relationship is constant and does not depend on total database size. The same fraud ring query that grinds a relational database is a single Cypher statement in Neo4j, regardless of ring size.

---

## Graph Fundamentals

A **graph** is a structure of nodes connected by edges. A **node** (also called a vertex) represents an entity — in this project: Account, Phone, Email, Device, Address, Transaction. An **edge** (also called a relationship) represents a connection between two nodes. Edges have direction and a type. The edge `(Account)-[:HAS_PHONE]->(Phone)` means an account registered that phone number.

Both nodes and edges carry **properties** — key-value data attached to them. An Account node has `id`, `name`, `created_at`, and `fraud_confirmed`. A `TRANSFERRED_TO` edge has `amount`, `timestamp`, and `transaction_id`. This combination of labels and properties on both nodes and edges is the **Labeled Property Graph (LPG)** model, which is what Neo4j implements.

A **path** is a sequence of nodes connected by edges. `Account A → Phone → Account B` is a path of length 2. A **hop** is one traversal of a single edge. The deeper the hop, the more distant the relationship. The number of edges connected to a node is its **degree** — a phone number shared by 12 fraud accounts has degree 12, which is a red flag.

A set of nodes where every node is reachable from every other node is a **connected component**. Each fraud ring in this project is a connected component in the subgraph of shared identifiers. The Account ↔ identifier structure — where accounts connect to phones/emails/devices, which connect back to other accounts, but accounts never directly connect to each other through these paths — is a **bipartite graph**: two distinct node sets where edges only cross between sets, never within the same set.

---

## Neo4j and Cypher

Neo4j's query language is **Cypher**. It is designed so the query visually resembles the graph pattern you are looking for. The pattern `(a:Account)-[:HAS_PHONE]->(p:Phone)` means: find an Account node connected to a Phone node via a HAS_PHONE relationship. Variable-length traversal is written with `*`: `(a)-[:HAS_PHONE|HAS_EMAIL|HAS_DEVICE*1..4]-(b)` finds any path of 1 to 4 hops between `a` and `b` using any of those relationship types.

`MATCH` finds patterns, `WHERE` filters them, `RETURN` outputs results. `MERGE` is an upsert — it creates a node or relationship only if it does not already exist. This is critical for loading shared identifiers: when two accounts share the same phone number, `MERGE (p:Phone {number: $n})` ensures only one Phone node is created, and both accounts get edges pointing to the same node. `CREATE` always creates a new node — addresses use `CREATE` in this project because each account's address is its own node with no deduplication intent.

**Uniqueness constraints** guarantee that a property is unique across all nodes with a given label and automatically create an index on that property. Without a constraint, `MERGE` scans all nodes of that label to check for existence. With a constraint, it uses the index — fast lookup instead of a full scan. Constraints are created before any data is loaded in `LoadData.java` for exactly this reason.

**LOAD CSV** is a built-in Cypher clause that reads a CSV file row by row and executes a Cypher statement per row. The file must sit in Neo4j's import directory. In this project, `data/raw/` is mounted to `/var/lib/neo4j/import` inside the Docker container, making files reachable via `file:///filename.csv`.

---

## Detection Techniques

Think of a shared phone number as a secret connection between two accounts. The fraudster reused the same phone number across multiple fake accounts — probably out of laziness. That reuse is the mistake to exploit.

If the phone number is stored as a text field on each account, it stays invisible — just a string sitting in a column. But pulling it out and making it its own node in the graph changes something: both accounts now have an edge pointing to that same phone node. The connection becomes physical. You can follow it. That is the difference between a property and a node — one is data you can read, the other is a connection you can traverse. The same logic applies to email and device.

**Transaction velocity** is about how fast an account moves money. Twenty transactions in one hour, or $50,000 transferred out within a day of account creation — these are behavioral red flags. This is a **local signal**: it tells you something is off about one specific account, in isolation.

Ring membership is a **structural signal**: it tells you an account is connected to a network of other suspicious accounts, regardless of its own transaction history. An account could have very few transactions but still be deeply embedded in a fraud ring.

The risk score in `risk_scoring.cypher` combines both signals into one number per account — how close it is to a confirmed fraud node, how many shared identifiers it has with flagged accounts, how fast it moves money, and whether it belongs to a ring. That combined score is what you hand to an analyst. Instead of reviewing thousands of accounts blindly, they start from the top of the list.

---

## Algorithms

**Breadth-first search (BFS)** explores all neighbors at the current hop depth before going deeper. It guarantees finding the shortest path between two nodes. In `VerifyLoad.java`, BFS finds connected components among fraud accounts: start at an unvisited fraud account, follow shared Phone/Email/Device edges to find all reachable fraud accounts, record them as one ring, then repeat for the next unvisited account. Each iteration identifies one ring. BFS is chosen because ring detection must be exhaustive — every reachable node must be found before the component is marked complete.

