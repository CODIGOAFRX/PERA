"""Árbol del menú de DimproCristalWin a partir del .MPR generado: opción, submenús y qué abre cada una."""
import re
import json
import sys
from pathlib import Path

path = Path(sys.argv[1])
text = path.read_bytes().decode('cp1252')
# Une las líneas partidas con ';' al final.
lines = []
buffer = ''
for raw in text.splitlines():
    line = raw.rstrip()
    if line.endswith(';'):
        buffer += line[:-1] + ' '
        continue
    lines.append((buffer + line).strip())
    buffer = ''

pads, popups, actions, skip = [], {}, {}, {}
for line in lines:
    m = re.match(r'DEFINE PAD (\w+) OF _MSYSMENU PROMPT "([^"]*)"(.*)', line, re.I)
    if m:
        pads.append({'id': m.group(1), 'prompt': m.group(2)})
        continue
    m = re.match(r'DEFINE BAR (\d+) OF (\S+) PROMPT "([^"]*)"(.*)', line, re.I)
    if m:
        rest = m.group(4)
        sk = re.search(r'SKIP FOR (.*?)(MESSAGE|PICTURE|$)', rest, re.I)
        popups.setdefault(m.group(2).lower(), []).append({'bar': m.group(1), 'prompt': m.group(3),
                                                          'skip': sk.group(1).strip() if sk else ''})
        continue
    m = re.match(r'ON PAD (\w+) OF _MSYSMENU ACTIVATE POPUP (\S+)', line, re.I)
    if m:
        actions[('pad', m.group(1))] = ('popup', m.group(2).lower())
        continue
    m = re.match(r'ON SELECTION PAD (\w+) OF _MSYSMENU (.*)', line, re.I)
    if m:
        actions[('pad', m.group(1))] = ('cmd', m.group(2).strip())
        continue
    m = re.match(r'ON BAR (\d+) OF (\S+) ACTIVATE POPUP (\S+)', line, re.I)
    if m:
        actions[(m.group(2).lower(), m.group(1))] = ('popup', m.group(3).lower())
        continue
    m = re.match(r'ON SELECTION BAR (\d+) OF (\S+) (.*)', line, re.I)
    if m:
        actions[(m.group(2).lower(), m.group(1))] = ('cmd', m.group(3).strip())

def target(command):
    m = re.search(r'llama_formulario_desde_menu\("([^"]+)"', command, re.I)
    if m:
        return 'form:' + m.group(1)
    m = re.search(r'do\s+form[s]?\s*\\?\s*([\w\\]+)', command, re.I)
    if m:
        return 'form:' + m.group(1).split('\\')[-1]
    m = re.search(r'do form\s+([\w\\.]+)', command, re.I)
    if m:
        return 'form:' + m.group(1)
    m = re.search(r'do\s+([\w\\.]+)', command, re.I)
    if m:
        return 'prg:' + m.group(1).split('\\')[-1]
    return 'cmd:' + command

out = []
def walk(popup, path, depth):
    for bar in popups.get(popup, []):
        if bar['prompt'].startswith('\\-'):
            continue
        action = actions.get((popup, bar['bar']))
        item = {'path': path + [bar['prompt'].replace('\\<', '')], 'depth': depth}
        if action and action[0] == 'popup':
            out.append({**item, 'kind': 'submenu'})
            walk(action[1], item['path'], depth + 1)
        else:
            out.append({**item, 'kind': 'option', 'target': target(action[1]) if action else '', 'command': action[1] if action else '',
                        'skip': bar['skip']})

for pad in pads:
    action = actions.get(('pad', pad['id']))
    if action and action[0] == 'popup':
        out.append({'path': [pad['prompt']], 'depth': 0, 'kind': 'submenu'})
        walk(action[1], [pad['prompt']], 1)
    else:
        out.append({'path': [pad['prompt']], 'depth': 0, 'kind': 'option', 'target': target(action[1]) if action else '',
                    'command': action[1] if action else '', 'skip': ''})

json.dump(out, open(sys.argv[2], 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
for item in out:
    print('  ' * item['depth'] + item['path'][-1] + ('' if item['kind'] == 'submenu' else '  ->  ' + item['target']))
