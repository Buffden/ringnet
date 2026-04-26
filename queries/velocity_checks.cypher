MATCH (a:Account)-[:SENT]->(t:Transaction)
WITH a, count(t) AS tx_count, min(t.timestamp) AS first_tx, max(t.timestamp) AS last_tx
WHERE tx_count > 5
RETURN a.name, a.fraud_confirmed, tx_count, first_tx, last_tx
ORDER BY tx_count DESC
