"""Check canonical Obsidian notes without duplicating them in the code repository."""
import argparse
import json
import re
from pathlib import Path


def verify(root):
    expected = {'시작하기', '제품 요구사항', '사용자 흐름', '기술 타당성',
                '결정 기록', '개발 및 검증 계획', '작업 일지'}
    notes = {}
    for name in expected:
        path = root / (name + '.md')
        data = path.read_bytes()
        content = data.decode('utf-8', errors='strict')
        if '\ufeff' in content or '\ufffd' in content:
            raise ValueError(f'Encoding problem: {path.name}')
        frontmatter = re.match(r'\A---\n(.*?)\n---\n', content, re.S)
        if not frontmatter:
            raise ValueError(f'Missing frontmatter: {path.name}')
        fields = dict(re.findall(r'^(type|status|created|updated): (.+)$', frontmatter[1], re.M))
        if fields.keys() != {'type','status','created','updated'}:
            raise ValueError(f'Missing fields: {path.name}')
        for key in ('created','updated'):
            if not re.fullmatch(r'\d{4}-\d{2}-\d{2}', fields[key]):
                raise ValueError(f'Invalid date: {path.name} {key}')
        notes[name] = content
    headings = {name:set(re.findall(r'^#{1,6} (.+)$', text,re.M)) for name,text in notes.items()}
    links = 0
    for name,text in notes.items():
        for target in re.findall(r'\[\[([^\]]+)\]\]',text):
            note,separator,anchor = target.split('|',1)[0].partition('#')
            note = note or name
            if note not in notes or (separator and anchor not in headings[note]):
                raise ValueError(f'Broken link: {name} -> {target}')
            links += 1
    patterns = {
        'REQ': ('제품 요구사항',r'^\| (REQ-\d{3}) \|'),
        'DEC': ('결정 기록',r'^## (DEC-\d{3}) '),
        'TEST': ('개발 및 검증 계획',r'^\| (TEST-\d{3}) \|'),
        'SRC': ('기술 타당성',r'^### (SRC-\d{3})$'),
        'OPEN': ('기술 타당성',r'^\| (OPEN-\d{3}) \|'),
        'FACT': ('기술 타당성',r'^### (FACT-\d{3}) '),
    }
    defined = {}
    for prefix,(name,pattern) in patterns.items():
        matches = re.findall(pattern,notes[name],re.M)
        if len(matches) != len(set(matches)) or not matches:
            raise ValueError(f'Duplicate or missing definitions: {prefix}')
        defined[prefix] = set(matches)
    for name,text in notes.items():
        for prefix,num in re.findall(r'\b(REQ|DEC|TEST|SRC|OPEN|FACT)-(\d{3})\b',text):
            if f'{prefix}-{num}' not in defined[prefix]:
                raise ValueError(f'Undefined ID: {name} {prefix}-{num}')
    covered = set()
    for row in notes['개발 및 검증 계획'].splitlines():
        if re.match(r'^\| TEST-\d{3} \|',row):
            covered.update(re.findall(r'REQ-\d{3}',row))
    if covered != defined['REQ']:
        raise ValueError(f'Requirements without test mapping: {defined["REQ"]-covered}')
    return {'status':'PASS','notes':len(notes),'links':links,
            'definitions':{p:len(v) for p,v in defined.items()},'requirementsWithTests':len(covered)}


if __name__ == '__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('vault',type=Path)
    args=parser.parse_args()
    print(json.dumps(verify(args.vault),ensure_ascii=False,indent=2))
