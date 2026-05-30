path = r"e:\Vaultix\app\src\main\java\com\vaultix\app\ui\screens\SettingsScreen.kt"
with open(path, 'r', encoding='utf-8') as f:
    s = f.read()
print('OPEN', s.count('{'), 'CLOSE', s.count('}'))
