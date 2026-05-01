import os
import sys
from markdown_pdf import MarkdownPdf, Section

def generate_pdf(md_file_path, output_pdf_path):
    # 1. Lire le fichier MD
    with open(md_file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # 2. Se déplacer dans le dossier du fichier MD pour que les images relatives soient trouvées
    original_cwd = os.getcwd()
    report_dir = os.path.dirname(os.path.abspath(md_file_path))
    os.chdir(report_dir)
    
    try:
        # 3. Créer le PDF
        pdf = MarkdownPdf(toc_level=2)
        pdf.add_section(Section(content))
        
        # Le nom du fichier de sortie doit être relatif ou absolu
        output_name = os.path.basename(output_pdf_path)
        pdf.save(output_name)
        
        # Si le fichier a été sauvegardé dans le dossier du rapport, on le déplace vers la destination finale si besoin
        # Mais ici on va juste le laisser là.
        print(f"[SUCCESS] PDF successfully created at: {output_pdf_path}")
    finally:
        os.chdir(original_cwd)

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Usage: python gen_pdf.py <input_md> <output_pdf>")
    else:
        generate_pdf(sys.argv[1], sys.argv[2])
