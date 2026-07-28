import sqlite3, json

conn = sqlite3.connect('D:\\code\\project-demo\\youkeda_pj\\user_context.db')
rows = conn.execute('SELECT user_id, context_json, updated_at FROM user_context').fetchall()

if not rows:
    print("数据库为空，还没有用户对话记录")
else:
    for user_id, context_json, updated_at in rows:
        messages = json.loads(context_json)
        print(f"━━━ 用户: {user_id} (更新于 {updated_at}) ━━━")
        print(f"对话轮数: {len(messages)} 条")
        for msg in messages:
            role = msg.get('role', '?')
            content = str(msg.get('content', ''))[:80]
            print(f"  [{role}] {content}")
        print()

conn.close()
