"""Para cada opción del menú: tamaño del código, vistas/tablas que usa y pantallas o programas a los que llama."""
import json
import re
from pathlib import Path

import sys
# Carpeta de la instalación de DimproCristalWin, como primer argumento.
ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else r'C:\DimproCristalWin')
FORMS = {p.stem.lower(): p for p in (ROOT / 'FORMS').glob('*.[sS][cC][tT]')}
PROGS = {p.stem.lower(): p for p in (ROOT / 'PROGS').glob('*.[pP][rR][gG]')}
PROGS.update({p.stem.lower(): p for p in (ROOT / 'PROGS_CUSTOM').glob('*.[pP][rR][gG]') if p.stem.lower() not in PROGS})

def strings(path):
    data = path.read_bytes()
    return [m.group().decode('cp1252') for m in re.finditer(rb'[\x09\x0a\x0d\x20-\x7e\xa0-\xff]{4,}', data)]

def scan(kind, name):
    path = (FORMS if kind == 'form' else PROGS).get(name.lower())
    if not path:
        return None
    text = '\n'.join(strings(path)) if kind == 'form' else path.read_bytes().decode('cp1252', 'replace')
    code_lines = [l for l in text.splitlines() if l.strip() and not l.strip().startswith(('*', '&&'))]
    procedures = re.findall(r'^\s*(?:PROCEDURE|FUNCTION)\s+([\w.]+)', text, re.I | re.M)
    views = sorted(set(v.lower() for v in re.findall(r'CursorSource\s*=\s*"([^"]+)"', text, re.I)))
    tables = sorted(set(t.lower() for t in re.findall(r'\bUSE\s+(?:\(?\s*ADDBS\(\w+\)\s*\+\s*)?"?(\w+)"?', text, re.I)
                        if t.lower() not in ('in', 'again', 'shared')))
    forms = sorted(set(f.lower() for f in re.findall(r'(?:DO\s+FORM\s+(?:forms\\)?|llama_formulario\w*\(\s*")(\w+)', text, re.I)))
    progs = sorted(set(p.lower() for p in re.findall(r'\bDO\s+(?!FORM)(?:progs\\)?(\w+)', text, re.I)
                       if p.lower() in PROGS))
    return {'file': path.name, 'code_lines': len(code_lines), 'procedures': len(procedures), 'views': views,
            'tables': tables, 'calls_forms': [f for f in forms if f != name.lower()], 'calls_progs': progs}

menu = json.load(open('menu_principal.json', encoding='utf-8'))
for item in menu:
    target = item.get('target', '')
    if ':' in target:
        kind, name = target.split(':', 1)
        name = name.replace('.prg', '')
        item['scan'] = scan(kind, name) if kind in ('form', 'prg') else None
json.dump(menu, open('menu_inventory.json', 'w', encoding='utf-8'), ensure_ascii=False, indent=1)

# Segundo nivel: pantallas a las que se llega desde otras, sin estar en el menú.
reached = {}
queue = []
for item in menu:
    s = item.get('scan')
    if s:
        queue += [('form', f) for f in s['calls_forms']] + [('prg', p) for p in s['calls_progs']]
menu_targets = {i.get('target', '').split(':', 1)[-1].lower() for i in menu}
while queue:
    kind, name = queue.pop()
    if name in reached or name in menu_targets:
        continue
    s = scan(kind, name)
    reached[name] = {'kind': kind, 'scan': s}
    if s:
        queue += [('form', f) for f in s['calls_forms']] + [('prg', p) for p in s['calls_progs']]
json.dump(reached, open('inner_screens.json', 'w', encoding='utf-8'), ensure_ascii=False, indent=1)

total_menu = sum(i['scan']['code_lines'] for i in menu if i.get('scan'))
total_inner = sum(v['scan']['code_lines'] for v in reached.values() if v['scan'])
missing = [i['target'] for i in menu if i.get('kind') == 'option' and ':' in i.get('target', '') and not i.get('scan')]
print('opciones con código encontrado:', sum(1 for i in menu if i.get('scan')))
print('líneas de código en pantallas del menú:', total_menu)
print('pantallas internas alcanzadas:', len(reached), 'líneas:', total_inner)
print('objetivos no encontrados:', missing)
top = sorted([i for i in menu if i.get('scan')], key=lambda i: -i['scan']['code_lines'])[:15]
for i in top:
    print(f"{i['scan']['code_lines']:7d}  {' > '.join(i['path'])}  ({i['scan']['file']})")
print('--- internas más grandes')
for name, v in sorted(reached.items(), key=lambda kv: -(kv[1]['scan'] or {'code_lines': 0})['code_lines'])[:20]:
    if v['scan']:
        print(f"{v['scan']['code_lines']:7d}  {name}")
