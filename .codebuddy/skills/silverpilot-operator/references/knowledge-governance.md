# Knowledge governance

Treat `knowledge-base/ima-ready` as a controlled package shared by local retrieval and ima.

Require every Markdown document to declare title, document_id, version, owner, status, reviewed_at, next_review_at, scope, and source_type. Only `approved` documents with valid review dates satisfying `reviewed_at <= today <= next_review_at` in Asia/Shanghai are runtime-searchable. Keep one topic per file and preserve factual boundaries.

Do not admit secrets, personal data, diagnoses, unverifiable commercial outcomes, duplicate documents, broken relative links, or expired reviews into approved knowledge. Expired documents must be quarantined as `draft`, preserving the actual historical dates until a real review occurs. The validator warns for expired drafts but still rejects expired approved/governance documents and malformed or missing dates in any status. Run `scripts/validate-knowledge-base.ps1` after every change. For ima, import only currently eligible approved files and remove quarantined old copies, verifying removal in the external application. Do not describe this as API synchronization unless a real API is configured and tested.
