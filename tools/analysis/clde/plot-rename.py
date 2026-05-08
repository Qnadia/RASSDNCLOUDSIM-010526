import os

base = r'E:\Workspace\v2\cloudsimsdn-research\results\2026-05-02'

datasets = {
    'dataset-mini':   '_mini',
    'dataset-small':  '_small',
    'dataset-medium': '_medium',
}

for ds_name, suffix in datasets.items():
    plot_dir = os.path.join(base, ds_name, 'plot')
    if not os.path.exists(plot_dir):
        print(f'[SKIP] {plot_dir}')
        continue

    print(f'\n=== {ds_name} ===')
    for fname in os.listdir(plot_dir):
        # Extensions ciblées
        if not (fname.endswith('.png') or fname.endswith('.svg')):
            continue

        # Déjà renommé → skip
        if suffix in fname:
            print(f'  [SKIP] {fname}')
            continue

        # Construire nouveau nom
        if fname.endswith('.png'):
            new_name = fname[:-4] + suffix + '.png'
        else:
            new_name = fname[:-4] + suffix + '.svg'

        src = os.path.join(plot_dir, fname)
        dst = os.path.join(plot_dir, new_name)
        os.rename(src, dst)
        print(f'  {fname} → {new_name}')

print('\n✅ Done.')