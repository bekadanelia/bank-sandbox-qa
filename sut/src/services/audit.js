/** Writes one audit event inside the caller's transaction. */
export async function audit(db, { eventType, entityType, entityId, actorId = null, payload = {} }) {
  await db.query(
    `INSERT INTO audit_events (event_type, entity_type, entity_id, actor_id, payload)
     VALUES ($1, $2, $3, $4, $5)`,
    [eventType, entityType, entityId, actorId, JSON.stringify(payload)],
  );
}
