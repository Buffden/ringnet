// Graph visualization — paste into Neo4j browser, switch to graph view.
// Returns fraud accounts and their shared identifier nodes (Phone/Email/Device).
// The browser renders which identifiers are shared across accounts, revealing the rings.
MATCH (a:Account {fraud_confirmed: true})-[r:HAS_PHONE|HAS_EMAIL|HAS_DEVICE]->(identifier)
RETURN a, r, identifier

// Tabular form — returns one row per connected account, useful for analysis.
// MATCH (start:Account {fraud_confirmed: true})-[:HAS_PHONE|HAS_EMAIL|HAS_DEVICE*1..6]-(connected:Account)
// WHERE start <> connected
// RETURN DISTINCT connected.name, connected.fraud_confirmed
