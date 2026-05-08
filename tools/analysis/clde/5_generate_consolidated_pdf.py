import os, re, base64
from markdown_pdf import MarkdownPdf, Section
from PIL import Image
import io

def generate_optimized_pdf():
    base_dir = r"E:\Workspace\v2\cloudsimsdn-research"
    report_md = os.path.join(base_dir, "docs", "reports", "2026-05-07_consolidated_analysis_report.md")
    plot_dir = os.path.join(base_dir, "results", "2026-05-02", "global_analysis", "plot_consolidated")
    out_pdf = os.path.join(base_dir, "results", "2026-05-02", "global_analysis", "Consolidated_Analysis_Report.pdf")

    with open(report_md, "r", encoding="utf-8") as f:
        content = f.read()

    def md_to_html(md):
        h = md.replace("<", "&lt;").replace(">", "&gt;")
        h = re.sub(r'^# (.*)', r'<h1>\1</h1>', h, flags=re.M)
        h = re.sub(r'^## (.*)', r'<h2>\1</h2>', h, flags=re.M)
        h = re.sub(r'^### (.*)', r'<h3>\1</h3>', h, flags=re.M)
        h = re.sub(r'\*\*(.*?)\*\*', r'<b>\1</b>', h)
        
        lines = h.split("\n")
        in_table = False
        new_lines = []
        for line in lines:
            if "|" in line:
                if not in_table:
                    new_lines.append('<table style="width:100%; border-collapse:collapse; border:1px solid #ccc; font-size:9pt; margin:10px 0">')
                    in_table = True
                if ":---" in line or "---:" in line: continue
                cols = [c.strip() for c in line.split("|") if c.strip()]
                row = "<tr>" + "".join([f'<td style="border:1px solid #ccc; padding:5px">{c}</td>' for c in cols]) + "</tr>"
                new_lines.append(row)
            else:
                if in_table:
                    new_lines.append('</table>')
                    in_table = False
                new_lines.append(line + "<br>")
        h = "\n".join(new_lines)
        
        def repl_img(match):
            alt = match.group(1)
            fname = os.path.basename(match.group(2))
            p = os.path.join(plot_dir, fname)
            if os.path.exists(p):
                img = Image.open(p)
                w, h_img = img.size
                if w > 1000:
                    new_h = int(h_img * (1000 / w))
                    img = img.resize((1000, new_h), Image.Resampling.LANCZOS)
                
                buf = io.BytesIO()
                img.save(buf, format="PNG", optimize=True)
                b64 = base64.b64encode(buf.getvalue()).decode()
                return f'<div style="text-align:center; margin:20px 0"><img src="data:image/png;base64,{b64}" style="max-width:95%"><br><i style="font-size:9pt">{alt}</i></div>'
            return f'<b>[Image Manquante: {fname}]</b>'
        
        h = re.sub(r'!\[(.*?)\]\((.*?)\)', repl_img, h)
        return h

    html_content = f"<html><body>{md_to_html(content)}</body></html>"

    pdf = MarkdownPdf(toc_level=0)
    css = """
    body { font-family: 'Segoe UI', sans-serif; line-height: 1.5; color: #333; }
    h1 { color: #1a5276; border-bottom: 2px solid #1a5276; text-align: center; }
    h2 { color: #1f618d; border-left: 5px solid #1a5276; padding-left: 10px; background: #f4f6f7; margin-top: 25px; }
    h3 { color: #21618c; margin-top: 15px; }
    table { border-collapse: collapse; width: 100%; margin: 15px 0; }
    td { text-align: center; border: 1px solid #ccc; padding: 6px; }
    tr:nth-child(even) { background: #f2f2f2; }
    """
    
    pdf.add_section(Section(html_content), user_css=css)
    pdf.save(out_pdf)
    print(f"DONE Optimized: {out_pdf}")

if __name__ == "__main__":
    generate_optimized_pdf()
