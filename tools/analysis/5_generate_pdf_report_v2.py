import os
import argparse
import shutil
from markdown_pdf import MarkdownPdf, Section

def convert_md_to_pdf(synthese_dir):
    md_path = os.path.join(synthese_dir, "SUMMARY_SCIENTIFIC_REPORT.md")
    pdf_path = os.path.join(synthese_dir, "SUMMARY_SCIENTIFIC_REPORT.pdf")
    # Les images sont dans le dossier parent 'plot'
    ds_path = os.path.dirname(synthese_dir)
    plot_dir = os.path.join(ds_path, "plot")

    if not os.path.exists(md_path):
        print(f"Error: {md_path} not found.")
        return

    print(f"--- Converting Report to PDF: {pdf_path} ---")

    # 1. Read Markdown
    with open(md_path, "r", encoding="utf-8") as f:
        content = f.read()

    # 2. Copy ALL images from plot/ to synthese/ temporarily
    # This ensures paths like ![fig](../plot/fig.png) work or can be simplified
    images_to_copy = []
    if os.path.exists(plot_dir):
        images_to_copy = [f for f in os.listdir(plot_dir) if f.endswith(".png") or f.endswith(".jpg")]
        for img in images_to_copy:
            shutil.copy(os.path.join(plot_dir, img), os.path.join(synthese_dir, img))
    
    # Also check if there's a topology image in the parent or plot dir
    topo_img = "fig0_topology.png"
    if os.path.exists(os.path.join(ds_path, topo_img)):
        shutil.copy(os.path.join(ds_path, topo_img), os.path.join(synthese_dir, topo_img))
        images_to_copy.append(topo_img)

    # 3. Strip path prefixes for internal compatibility during conversion
    fixed_content = content.replace("../plot/", "")
    fixed_content = fixed_content.replace("..\\plot\\", "")
    # If the MD uses direct filenames, it will find them in synthese_dir now

    # 4. Generate PDF with Premium CSS
    abs_synthese_dir = os.path.abspath(synthese_dir)
    pdf = MarkdownPdf(toc_level=2, optimize=True)
    
    css = """
    @page { margin: 20mm; }
    body { font-family: 'Segoe UI', Arial, sans-serif; line-height: 1.6; color: #333; }
    h1 { color: #1f77b4; border-bottom: 2px solid #1f77b4; padding-bottom: 10px; }
    h2 { color: #2c3e50; margin-top: 30px; border-left: 5px solid #1f77b4; padding-left: 10px; }
    h3 { color: #34495e; margin-top: 20px; }
    table {
        border-collapse: collapse;
        width: 100%;
        margin: 20px 0;
        font-size: 0.9em;
    }
    th {
        background-color: #1f77b4;
        color: white;
        padding: 10px 12px;
        text-align: center;
        border: 1px solid #1565a0;
    }
    td {
        padding: 8px 12px;
        border: 1px solid #ccc;
        text-align: left;
    }
    tr:nth-child(even) { background-color: #f5f8fc; }
    tr:nth-child(odd)  { background-color: #ffffff; }
    img {
        max-width: 90%;
        height: auto;
        display: block;
        margin: 20px auto;
        border: 1px solid #ddd;
    }
    blockquote {
        border-left: 4px solid #1f77b4;
        background: #f0f7ff;
        margin: 15px 0;
        padding: 10px 15px;
        color: #2c3e50;
    }
    """
    
    pdf.add_section(Section(fixed_content, root=abs_synthese_dir), user_css=css)
    
    try:
        pdf.save(pdf_path)
        print(f"  [SUCCESS] PDF generated: {pdf_path}")
    except Exception as e:
        print(f"  [ERROR] PDF generation failed: {e}")

    # 5. Cleanup temporary images in synthese_dir
    for img in images_to_copy:
        try:
            temp_path = os.path.join(synthese_dir, img)
            if os.path.exists(temp_path):
                os.remove(temp_path)
        except:
            pass

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--synthese-dir", required=True)
    args = parser.parse_args()
    convert_md_to_pdf(args.synthese_dir)
