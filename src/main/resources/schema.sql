
CREATE TABLE IF NOT EXISTS chat_history (
id INTEGER PRIMARY KEY AUTOINCREMENT,
user_id TEXT NOT NULL,
role TEXT NOT NULL CHECK (role IN ('user', 'assistant', 'tool')),
content TEXT,
tool_call_id TEXT,
name TEXT,
tool_calls TEXT CHECK (tool_calls IS NULL OR JSON(tool_calls) IS NOT NULL),
created_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
    );

-- 业务查询索引
CREATE INDEX IF NOT EXISTS idx_chat_history_user_id ON chat_history(user_id);
CREATE INDEX IF NOT EXISTS idx_chat_history_created_at ON chat_history(created_at);