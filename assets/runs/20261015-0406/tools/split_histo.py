# Split HISTO lines from the run's console output into one file per histo config.
# Each histo config runs all 26 benchmarks x 3 params x 3 iterations = 234 blocks, coh-on first.
import sys
src, out_on, out_off = sys.argv[1:4]
blocks=[]; cur=None
for line in open(src, encoding='utf-8', errors='replace'):
    if line.startswith('HISTO ') and ' test=' in line and ' clean=' in line:
        cur=[line]; blocks.append(cur)
    elif line.startswith('HISTO') and cur is not None:
        cur.append(line)
print(len(blocks), 'blocks')
assert len(blocks)==468, len(blocks)
open(out_on,'w').writelines(l for b in blocks[:234] for l in b)
open(out_off,'w').writelines(l for b in blocks[234:] for l in b)
