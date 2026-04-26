MATCH (a:Account)-[:HAS_PHONE]->(p:Phone)<-[:HAS_PHONE]-(b:Account)
WHERE a.name < b.name
RETURN a.name, a.fraud_confirmed, b.name, b.fraud_confirmed, p.number
