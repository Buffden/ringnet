MATCH (start:Account {fraud_confirmed: true})-[:HAS_PHONE|HAS_EMAIL|HAS_DEVICE*1..6]-(connected:Account)
WHERE start <> connected
RETURN DISTINCT connected.name, connected.fraud_confirmed
