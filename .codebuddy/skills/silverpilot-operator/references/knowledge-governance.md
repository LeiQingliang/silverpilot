# Knowledge governance

Treat `knowledge-base/ima-ready` as a controlled package shared by local retrieval and ima.

Require every Markdown document to declare title, document_id, version, owner, status, reviewed_at, next_review_at, scope, and source_type. Only `approved` documents are runtime-searchable. Keep one topic per file and preserve factual boundaries.

Do not admit secrets, personal data, diagnoses, unverifiable commercial outcomes, duplicate documents, broken relative links, or expired reviews. Run `scripts/validate-knowledge-base.ps1` after every change. For ima, replace the complete validated package to avoid mixed versions and bind it natively in WorkBuddy; do not describe this as API synchronization unless a real API is configured and tested.
