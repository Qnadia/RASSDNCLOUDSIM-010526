import os
import sys
try:
    from markdown_pdf import MarkdownPdf
    from markdown_pdf import Section
except ImportError:
    print("markdown_pdf not found")
    sys.exit(1)

base_dir = r"E:\Workspace\v2\cloudsimsdn-research"
md_path = os.path.join(base_dir, 'docs', 'tracking', '2026-04-18', 'it31_parallel_dp_routing.md')
pdf_path = os.path.join(base_dir, 'docs', 'tracking', '2026-04-18', 'it31_parallel_dp_routing.pdf')

with open(md_path, 'r', encoding='utf-8') as f:
    content = f.read()

pdf = MarkdownPdf(toc_level=2)
pdf.add_section(Section(content))
pdf.save(pdf_path)
print(f"[SUCCESS] PDF successfully created at: {pdf_path}")
