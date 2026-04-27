MATCH (a:Account)-[:HAS_PHONE]->(p:Phone)<-[:HAS_PHONE]-(b:Account)
WHERE a.name < b.name
RETURN a.name, a.fraud_confirmed, b.name, b.fraud_confirmed, 'phone' AS shared_via

UNION

MATCH (a:Account)-[:HAS_EMAIL]->(e:Email)<-[:HAS_EMAIL]-(b:Account)
WHERE a.name < b.name
RETURN a.name, a.fraud_confirmed, b.name, b.fraud_confirmed, 'email' AS shared_via

UNION

MATCH (a:Account)-[:HAS_DEVICE]->(d:Device)<-[:HAS_DEVICE]-(b:Account)
WHERE a.name < b.name
RETURN a.name, a.fraud_confirmed, b.name, b.fraud_confirmed, 'device' AS shared_via
