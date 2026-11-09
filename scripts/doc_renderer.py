import os
import sys
import json
import hashlib
import traceback

def get_file_hash(filepath):
    h = hashlib.md5()
    h.update(filepath.encode("utf-8"))
    try:
        h.update(str(os.path.getmtime(filepath)).encode("utf-8"))
    except Exception:
        pass
    return h.hexdigest()[:12]

def render_document(input_path, output_dir):
    os.makedirs(output_dir, exist_ok=True)
    input_path = os.path.abspath(input_path)
    output_dir = os.path.abspath(output_dir)
    
    ext = os.path.splitext(input_path)[1].lower()
    manifest = {
        "status": "success",
        "type": ext.replace(".", ""),
        "filename": os.path.basename(input_path),
        "page_count": 0,
        "pages": []
    }

    if ext == ".pdf":
        render_pdf(input_path, output_dir, manifest)
    elif ext in [".pptx", ".ppt"]:
        render_presentation(input_path, output_dir, manifest)
    elif ext in [".docx", ".doc"]:
        render_word(input_path, output_dir, manifest)
    else:
        manifest["status"] = "unsupported"
        manifest["error"] = f"Unsupported extension: {ext}"

    manifest_path = os.path.join(output_dir, "manifest.json")
    with open(manifest_path, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2, ensure_ascii=False)
    print(f"[SUCCESS] Processed {len(manifest['pages'])} pages into {output_dir}")

def render_pdf(input_path, output_dir, manifest):
    try:
        import pymupdf
        doc = pymupdf.open(input_path)
        manifest["page_count"] = len(doc)
        
        for i, page in enumerate(doc):
            num = i + 1
            img_name = f"page_{num}.png"
            img_path = os.path.join(output_dir, img_name)
            
            # High-DPI rendering for razor-sharp text & diagrams
            pix = page.get_pixmap(dpi=140)
            pix.save(img_path)
            
            text = page.get_text("text").strip()
            lines = [l.strip() for l in text.split("\n") if l.strip()]
            title = lines[0][:40] if lines else f"Page {num}"
            
            manifest["pages"].append({
                "number": num,
                "title": title,
                "image": img_name,
                "text": text
            })
    except Exception as e:
        manifest["status"] = "error"
        manifest["error"] = str(e)
        traceback.print_exc()

def render_presentation(input_path, output_dir, manifest):
    # Try PowerPoint COM for pixel-perfect slide rendering
    exported_slides = False
    try:
        import win32com.client
        ppt = win32com.client.Dispatch("PowerPoint.Application")
        pres = ppt.Presentations.Open(input_path, WithWindow=False)
        # 17 = ppSaveAsJPG
        pres.SaveAs(output_dir, 17)
        slide_count = pres.Slides.Count
        pres.Close()
        ppt.Quit()
        exported_slides = True
        manifest["page_count"] = slide_count
    except Exception as e:
        print("[WARN] PowerPoint COM export failed, falling back to python-pptx:", e)

    # Extract slide titles and text with python-pptx
    slide_texts = []
    try:
        import pptx
        prs = pptx.Presentation(input_path)
        for i, slide in enumerate(prs.slides):
            title = slide.shapes.title.text.strip() if (slide.shapes.title and slide.shapes.title.text) else f"Slide {i+1}"
            bullets = []
            for shape in slide.shapes:
                if shape.has_text_frame and shape.text.strip():
                    for p in shape.text_frame.paragraphs:
                        txt = p.text.strip()
                        if txt and txt != title:
                            bullets.append(txt)
            slide_texts.append({"title": title, "bullets": bullets, "full_text": "\n".join(bullets)})
    except Exception as ex:
        print("[WARN] python-pptx extraction error:", ex)

    count = max(manifest["page_count"], len(slide_texts))
    manifest["page_count"] = count

    for i in range(count):
        num = i + 1
        jpg_name = f"Slide{num}.JPG"
        has_jpg = os.path.exists(os.path.join(output_dir, jpg_name))
        
        info = slide_texts[i] if i < len(slide_texts) else {"title": f"Slide {num}", "bullets": [], "full_text": ""}
        manifest["pages"].append({
            "number": num,
            "title": info["title"],
            "image": jpg_name if has_jpg else "",
            "text": info["full_text"],
            "bullets": info.get("bullets", [])
        })

def render_word(input_path, output_dir, manifest):
    temp_pdf = os.path.join(output_dir, "_word_temp.pdf")
    converted_pdf = False
    try:
        import win32com.client
        word = win32com.client.Dispatch("Word.Application")
        word.Visible = False
        doc = word.Documents.Open(input_path, ReadOnly=True, Visible=False)
        # 17 = wdExportFormatPDF
        doc.ExportAsFixedFormat(temp_pdf, 17)
        doc.Close(False)
        word.Quit()
        converted_pdf = True
    except Exception as e:
        print("[WARN] Word COM export failed:", e)

    if converted_pdf and os.path.exists(temp_pdf):
        render_pdf(temp_pdf, output_dir, manifest)
        manifest["type"] = "docx"
        manifest["filename"] = os.path.basename(input_path)
        try:
            os.remove(temp_pdf)
        except Exception:
            pass
    else:
        manifest["status"] = "error"
        manifest["error"] = "Word conversion failed"

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Usage: python doc_renderer.py <input_file> <output_dir>")
        sys.exit(1)
    render_document(sys.argv[1], sys.argv[2])
