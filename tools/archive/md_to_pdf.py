import os
import shutil
import sys
try:
    from markdown_pdf import MarkdownPdf
    from markdown_pdf import Section
except ImportError:
    print("markdown_pdf not yet ready")
    sys.exit(1)

base_dir = r"E:\Workspace\v2\cloudsimsdn-research"
res_dir = os.path.join(base_dir, 'results', '2026-04-18')

# --- Configuration: SMALL DATASET (Décommentez pour utiliser) ---
# md_path = os.path.join(res_dir, 'Simulation_Report.md')
# pdf_path = os.path.join(res_dir, 'Simulation_Report_Avec_Images.pdf')
# fig_src_dir = os.path.join(res_dir, 'datasetsdataset-small-congested', 'figures_consolidated')
# fig_rel_dir = 'datasetsdataset-small-congested/figures_consolidated/'

# --- Configuration: MEDIUM DATASET ---
md_path = os.path.join(res_dir, 'Simulation_Report_Medium.md')
pdf_path = os.path.join(res_dir, 'Simulation_Report_Medium_Avec_Images.pdf')
fig_src_dir = os.path.join(res_dir, 'datasetsdataset-medium-congested', 'figures_consolidated')

# 1. Copy images to the same folder as the MD file
for f in os.listdir(fig_src_dir):
    if f.endswith('.png'):
        shutil.copy(os.path.join(fig_src_dir, f), os.path.join(res_dir, f))

# 2. Read and modify the markdown content
with open(md_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Replace the subfolder path with just the filename
fig_rel_dir = 'datasetsdataset-medium-congested/figures_consolidated/'
content = content.replace(fig_rel_dir, '')

# Save the updated MD just in case
with open(md_path, 'w', encoding='utf-8') as f:
    f.write(content)

# 3. Generate the PDF
pdf = MarkdownPdf(toc_level=2)
pdf.add_section(Section(content))
pdf.save(pdf_path)
print(f"[SUCCESS] PDF successfully created at: {pdf_path}")
