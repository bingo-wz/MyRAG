-- 旧版本可能留下正在扫描状态的任务，升级后重新排队并按简化流程处理。
UPDATE import_file_tasks
SET status = 'QUEUED'
WHERE status = 'SCANNING';
