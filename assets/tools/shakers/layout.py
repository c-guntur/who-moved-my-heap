"""Where the shakers files live (instructions2.md, section S2). Every shakers tool imports this.

Paths are relative to the project root. SHAKERS_RUN_DIR overrides the raw run folder, for
regenerating a run made before the shakers/movers layout (e.g. assets/runs/20261015-0406).
"""
import os

RUNS_REL = 'assets/runs/shakers'      # raw JMH output and the Results2 files
DOCS_REL = 'assets/docs/shakers'      # analysis docs, Summary, JDK Stream ops doc
MERMAID_REL = 'assets/docs/shakers/mermaid'
IMAGES_REL = 'assets/images/shakers'  # every PNG

# Links written into the docs, relative to DOCS_REL
IMG_LINK = '../../images/shakers'
MMD_LINK = 'mermaid'
RUNS_LINK = '../../runs/shakers'


def paths(root, run_ts):
    run_dir = os.environ.get('SHAKERS_RUN_DIR') or f'{RUNS_REL}/{run_ts}'
    p = {
        'run_dir_rel': run_dir,
        'run_dir': f'{root}/{run_dir}',
        'runs': f'{root}/{RUNS_REL}',
        'docs': f'{root}/{DOCS_REL}',
        'mermaid': f'{root}/{MERMAID_REL}',
        'images': f'{root}/{IMAGES_REL}',
    }
    for k in ('runs', 'docs', 'mermaid', 'images'):
        os.makedirs(p[k], exist_ok=True)
    return p


def environment(meta):
    """The 'environment:' line of meta.txt (section S3.1.3), or a plain fallback."""
    for line in meta.splitlines():
        if line.startswith('environment:'):
            return line.split(':', 1)[1].strip()
    return 'not recorded in meta.txt'


def java_version(meta):
    for line in meta.splitlines():
        if 'version "' in line:
            return line.strip()
    return 'JDK version not recorded'


def embed(caption, png, mmd):
    """AsciiDoc lines for one chart: the PNG from assets/images/shakers, a link to the Mermaid source."""
    return [f'.{caption}', f'image::{IMG_LINK}/{png}[{caption}]',
            f'Mermaid source: link:{MMD_LINK}/{mmd}[{mmd}]', '']
