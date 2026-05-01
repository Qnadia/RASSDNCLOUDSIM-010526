import os
import argparse
import shutil
from markdown_pdf import MarkdownPdf, Section

def convert_md_to_pdf(results_dir):
    md_path = os.path.join(results_dir, "GLOBAL_SCIENTIFIC_REPORT.md")
    if not os.path.exists(md_path):
        md_path = os.path.join(results_dir, "SUMMARY_SCIENTIFIC_REPORT.md")
    
    pdf_path = md_path.replace(".md", ".pdf")
    fig_dir = os.path.join(results_dir, "CONSOLIDATED_GLOBAL")
    if not os.path.exists(fig_dir):
        # On cherche dans dataset-mini/plot/ si on est dans un sous-dossier
        fig_dir = os.path.join(results_dir, "plot")

    if not os.path.exists(md_path):
        print(f"Error: No report (.md) found in {results_dir}.")
        return

    print(f"--- Converting Report to PDF (Robust Mode): {pdf_path} ---")

    # 1. Read Markdown
    with open(md_path, "r", encoding="utf-8") as f:
        content = f.read()

    # 2. Copy ALL images to the results_dir (same folder as MD)
    # This is the most compatible way for PDF generation on Windows
    images_to_copy = []
    if os.path.exists(fig_dir):
        images_to_copy = [f for f in os.listdir(fig_dir) if f.endswith(".png")]
        for img in images_to_copy:
            shutil.copy(os.path.join(fig_dir, img), os.path.join(results_dir, img))
            print(f"  Copied: {img}")

    # 3. Strip all path prefixes in the content
    # Handle both forward and backward slashes, and relative ../plot/
    fixed_content = content.replace("CONSOLIDATED_GLOBAL/", "")
    fixed_content = fixed_content.replace("CONSOLIDATED_GLOBAL\\", "")
    fixed_content = fixed_content.replace("../plot/", "")
    fixed_content = fixed_content.replace("..\\plot\\", "")

    # 4. Generate PDF with CSS styling
    abs_results_dir = os.path.abspath(results_dir)
    pdf = MarkdownPdf(toc_level=2, optimize=True)
    
    # Premium CSS with fix for ghost headers
    css = """
    @page {
        margin: 15mm;
    }
    body { font-family: 'Helvetica', 'Arial', sans-serif; font-size: 11pt; color: #333; line-height: 1.5; margin: 20px; }
    h1 { color: #1f4e79; font-size: 22pt; text-align: center; margin-bottom: 20px; border-bottom: 2px solid #1f4e79; padding-bottom: 10px; }
    h2 { color: #2c7bb6; font-size: 16pt; margin-top: 30px; margin-bottom: 15px; border-bottom: 1px solid #ddd; padding-bottom: 5px; }
    h3 { color: #333; font-size: 13pt; margin-top: 25px; margin-bottom: 10px; }
    table { border-collapse: collapse; width: 100%; margin: 15px 0; font-size: 10pt; }
    th, td {
        border: 1px solid #ccc !important;
        padding: 6px;
        text-align: left;
    }
    tr:nth-child(even) {
        background-color: #f9f9f9;
    }
    img {
        max-width: 85%;
        height: auto;
        display: block;
        margin: 10px auto;
    }
    """
    
    # Aggressive split: Split by level 2 OR level 3 headers
    # This isolates each sub-section (3.1, 3.2, etc.) into its own block
    import re
    sections = re.split(r'\n(#{2,3} )', fixed_content)
    
    # Reassemble sections (re.split keeps the separators)
    assembled_sections = []
    if sections[0].strip():
        assembled_sections.append(sections[0])
    
    for i in range(1, len(sections), 2):
        assembled_sections.append(sections[i] + sections[i+1])

    for sec in assembled_sections:
        if sec.strip():
            pdf.add_section(Section(sec, root=abs_results_dir), user_css=css)
    
    try:
        pdf.save(pdf_path)
        print(f"  [SUCCESS] PDF generated: {pdf_path}")
    except Exception as e:
        print(f"  [ERROR] PDF generation failed: {e}")

    # 5. Cleanup temporary images in results_dir (but keep fig0_topology.png)
    for img in images_to_copy:
        try:
            temp_path = os.path.join(results_dir, img)
            if os.path.exists(temp_path):
                os.remove(temp_path)
        except:
            pass

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--results-dir", required=True)
    args = parser.parse_args()
    convert_md_to_pdf(args.results_dir)
