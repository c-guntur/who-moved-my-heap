import re, json, sys, os, glob
from collections import defaultdict
NUM = r'(?:≈\s*0|≈\s*10[⁻\-]?[⁰¹²³⁴⁵⁶⁷⁸⁹0-9]+|-?[\d.]+(?:E-?\d+)?|NaN)'
ROW = re.compile(r'^(\w+)\.(\w+)(?::([\w.]+))?\s+(\d+)\s+avgt\s+(\d+)\s+(' + NUM + r')\s*(?:±\s*(' + NUM + r'))?\s+(\S+)\s*$')
def num(s):
    if s is None: return None
    s = s.strip()
    if s.startswith('≈'): return 0.0
    return float(s)
def parse_table(path):
    """-> {(method,test): {metric: (score, err)}}  metric '' = primary ns/op"""
    out = defaultdict(dict)
    for line in open(path, encoding='utf-8'):
        m = ROW.match(line.strip())
        if not m: continue
        cls, meth, metric, test, cnt, score, err, unit = m.groups()
        out[(meth, int(test))][metric or 'ns'] = (num(score), num(err), score.strip())
    return out
def parse_histo(path):
    """-> {(bench_class, method, test): {'clean_iters':n,'iters':n,'classes':{cls:(obj,bytes)}}} averaged over clean iters"""
    blocks = []; cur = None
    for line in open(path, encoding='utf-8'):
        if not line.startswith('HISTO'): continue
        m = re.match(r'HISTO (\S+) test=(\d+) clean=(\w+)', line)
        if m:
            b = m.group(1).split('.'); cur = {'cls': b[-2], 'meth': b[-1], 'test': int(m.group(2)), 'clean': m.group(3)=='true', 'classes': {}}
            blocks.append(cur); continue
        m = re.match(r'HISTO\s+([\d.]+) obj\s+([\d.]+) B\s+(.+)$', line.rstrip())
        if m and cur is not None:
            cur['classes'][m.group(3)] = (float(m.group(1)), float(m.group(2)))
    agg = {}
    for b in blocks:
        k = (b['cls'], b['meth'], b['test'])
        a = agg.setdefault(k, {'iters':0,'clean_iters':0,'sum':defaultdict(lambda:[0.0,0.0])})
        a['iters'] += 1
        if not b['clean']: continue
        a['clean_iters'] += 1
        for c,(o,by) in b['classes'].items():
            a['sum'][c][0]+=o; a['sum'][c][1]+=by
    res = {}
    for k,a in agg.items():
        n = a['clean_iters'] or 1
        # normalise lambda class names (hidden-class suffix differs per run)
        classes = {}
        for c,(o,by) in a['sum'].items():
            cn = re.sub(r'/0x[0-9a-f]+', '', c)
            po, pb = classes.get(cn, (0,0)); classes[cn] = (po+o/n, pb+by/n)
        res[k] = {'iters':a['iters'],'clean_iters':a['clean_iters'],'classes':classes,
                  'objs':sum(o for o,_ in classes.values()),'bytes':sum(b for _,b in classes.values())}
    return res
