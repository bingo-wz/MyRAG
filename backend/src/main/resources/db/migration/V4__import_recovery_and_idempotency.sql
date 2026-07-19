ALTER TABLE knowledge_documents
    ADD COLUMN import_task_id BIGINT;

CREATE UNIQUE INDEX uk_knowledge_documents_import_task
    ON knowledge_documents(import_task_id)
    WHERE import_task_id IS NOT NULL;
