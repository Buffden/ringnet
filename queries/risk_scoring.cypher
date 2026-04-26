MATCH (a:Account)

// Signal 1: ring proximity — is this account reachable from a confirmed fraud account?
OPTIONAL MATCH (fraud:Account {fraud_confirmed: true})-[:HAS_PHONE|HAS_EMAIL|HAS_DEVICE*1..6]-(a)
WITH a, count(DISTINCT fraud) AS fraud_neighbors

// Signal 2: shared identifiers — how many other accounts share an identifier with this one?
OPTIONAL MATCH (a)-[:HAS_PHONE|HAS_EMAIL|HAS_DEVICE]->(id)<-[:HAS_PHONE|HAS_EMAIL|HAS_DEVICE]-(other:Account)
WITH a, fraud_neighbors, count(DISTINCT other) AS shared_id_count

// Signal 3: transaction velocity — how many transactions did this account send?
OPTIONAL MATCH (a)-[:SENT]->(t:Transaction)
WITH a, fraud_neighbors, shared_id_count, count(t) AS tx_count

// Composite score: ring proximity weighted highest
WITH a,
     (fraud_neighbors * 10) + (shared_id_count * 3) + (tx_count * 1) AS risk_score,
     fraud_neighbors,
     shared_id_count,
     tx_count

WHERE risk_score > 0
RETURN a.name, a.fraud_confirmed, risk_score, fraud_neighbors, shared_id_count, tx_count
ORDER BY risk_score DESC
