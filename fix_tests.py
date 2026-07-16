import os
import glob

for f in glob.glob('src/test/java/**/*.java', recursive=True):
    with open(f, 'r') as file:
        content = file.read()
    
    if 'jdbcTemplate.execute("DELETE FROM tenants");' in content and 'DELETE FROM users' not in content:
        content = content.replace('jdbcTemplate.execute("DELETE FROM tenants");', 'jdbcTemplate.execute("DELETE FROM users");\n        jdbcTemplate.execute("DELETE FROM tenants");')
        with open(f, 'w') as file:
            file.write(content)
        print("Updated", f)
