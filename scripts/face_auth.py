import os
import sys
import json
import time
import warnings

# ── 1. SUPPRESS ALL TENSORFLOW, GLOG, AND WARNING OUTPUT ────────────────────
os.environ['TF_CPP_MIN_LOG_LEVEL'] = '3'
os.environ['GLOG_minloglevel'] = '3'
warnings.filterwarnings('ignore')

# Temporarily redirect stderr to devnull while importing heavy libraries
original_stderr = sys.stderr
try:
    with open(os.devnull, 'w') as fnull:
        sys.stderr = fnull
        import cv2
        import numpy as np
        import mediapipe as mp
finally:
    sys.stderr = original_stderr

# ── 2. PATHS CONFIGURATION ──────────────────────────────────────────────────
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
BASE_DIR = os.path.abspath(os.path.join(SCRIPT_DIR, ".."))
PROFILE_DIR = os.path.join(BASE_DIR, "data", "security")
PROFILE_PATH = os.path.join(PROFILE_DIR, "face_profile.json")
MODEL_PATH = os.path.join(PROFILE_DIR, "face_model.xml")
ENROLLED_IMG = os.path.join(PROFILE_DIR, "face_enrolled.png")
LIVE_IMG = os.path.join(PROFILE_DIR, "live_scan.png")

def ensure_profile_dir():
    os.makedirs(PROFILE_DIR, exist_ok=True)

def emit_json(obj):
    """Outputs pure single-line JSON to stdout and flushes."""
    sys.stdout.write(json.dumps(obj) + "\n")
    sys.stdout.flush()

def check_webcam():
    try:
        cap = cv2.VideoCapture(0, cv2.CAP_DSHOW if sys.platform == "win32" else cv2.CAP_ANY)
        if not cap.isOpened():
            cap = cv2.VideoCapture(0)
        if not cap.isOpened():
            emit_json({"status": "error", "webcam": False, "message": "Webcam not detected"})
            return False
        # Read a test frame
        ret, frame = cap.read()
        cap.release()
        emit_json({"status": "ok", "webcam": bool(ret), "message": "Webcam operational"})
        return ret
    except Exception as e:
        emit_json({"status": "error", "webcam": False, "message": str(e)})
        return False

# ── 3. AFFINE FACE ALIGNMENT & NORMALIZATION ────────────────────────────────
def align_and_crop_face(frame, keypoints, target_size=(100, 100)):
    """
    Given detected eye keypoints, rotates and scales the face so both eyes
    are horizontally level with fixed inter-pupillary distance.
    Applies histogram equalization to eliminate lighting discrepancies.
    """
    h, w = frame.shape[:2]
    # keypoints: [right_eye, left_eye, nose, mouth]
    re_x, re_y = keypoints[0]
    le_x, le_y = keypoints[1]

    # Calculate angle to rotate face so eyes are horizontal
    d_y = le_y - re_y
    d_x = le_x - re_x
    angle = np.degrees(np.arctan2(d_y, d_x))

    # Desired eye position in target aligned image (approx 30% from top, 35% and 65% from sides)
    desired_dist = target_size[0] * 0.35
    actual_dist = np.sqrt((d_x ** 2) + (d_y ** 2))
    if actual_dist < 5:
        return None

    scale = desired_dist / actual_dist
    eyes_center = ((re_x + le_x) * 0.5, (re_y + le_y) * 0.5)

    # Rotation matrix around eye midpoint
    M = cv2.getRotationMatrix2D(eyes_center, angle, scale)
    # Adjust translation so eyes center sits at target location (x=50%, y=35%)
    t_x = target_size[0] * 0.5
    t_y = target_size[1] * 0.35
    M[0, 2] += (t_x - eyes_center[0])
    M[1, 2] += (t_y - eyes_center[1])

    # Warp image to normalized coordinate space
    aligned_color = cv2.warpAffine(frame, M, target_size, flags=cv2.INTER_CUBIC, borderMode=cv2.BORDER_REPLICATE)
    aligned_gray = cv2.cvtColor(aligned_color, cv2.COLOR_BGR2GRAY)
    
    # Histogram equalization for illumination invariance
    equalized = cv2.equalizeHist(aligned_gray)
    return equalized, aligned_color

def extract_landmarks_and_face(frame, detector):
    """
    Detects face and keypoints using MediaPipe FaceDetection.
    Returns: (aligned_gray_100x100, aligned_color_100x100, geom_ratios, bbox)
    """
    h, w, _ = frame.shape
    rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
    results = detector.process(rgb)
    if not results or not results.detections:
        return None, None, None, None

    detection = results.detections[0]
    bbox_rel = detection.location_data.relative_bounding_box
    kps_rel = detection.location_data.relative_keypoints

    # Convert coordinates
    bw = int(bbox_rel.width * w)
    bh = int(bbox_rel.height * h)
    if bw < 35 or bh < 35:
        return None, None, None, None

    # Keypoints: 0=right eye, 1=left eye, 2=nose, 3=mouth, 4=right ear, 5=left ear
    kps = []
    for kp in kps_rel:
        kps.append([kp.x * w, kp.y * h])

    re = np.array(kps[0])
    le = np.array(kps[1])
    nose = np.array(kps[2])
    mouth = np.array(kps[3])

    d_eyes = float(np.linalg.norm(re - le))
    if d_eyes < 5.0:
        return None, None, None, None

    mid_eyes = (re + le) * 0.5
    d_eye_nose = float(np.linalg.norm(mid_eyes - nose))
    d_eye_mouth = float(np.linalg.norm(mid_eyes - mouth))
    d_nose_mouth = float(np.linalg.norm(nose - mouth))
    d_re_mouth = float(np.linalg.norm(re - mouth))
    d_le_mouth = float(np.linalg.norm(le - mouth))

    # Normalized invariant geometric proportions (independent of distance and scale)
    geom_ratios = [
        float(d_eye_nose / d_eyes),
        float(d_eye_mouth / d_eyes),
        float(d_nose_mouth / d_eyes),
        float(d_re_mouth / d_eyes),
        float(d_le_mouth / d_eyes),
        float(bw / float(bh))
    ]

    aligned_gray, aligned_color = align_and_crop_face(frame, kps, target_size=(100, 100))
    if aligned_gray is None:
        return None, None, None, None

    xmin = max(0, int(bbox_rel.xmin * w))
    ymin = max(0, int(bbox_rel.ymin * h))
    return aligned_gray, aligned_color, geom_ratios, [xmin, ymin, bw, bh]

# ── 4. REGISTRATION / ENROLLMENT ─────────────────────────────────────────────
def register_face():
    ensure_profile_dir()
    mp_face = mp.solutions.face_detection

    # Open camera with DirectShow for faster startup on Windows
    cap = cv2.VideoCapture(0, cv2.CAP_DSHOW if sys.platform == "win32" else cv2.CAP_ANY)
    if not cap.isOpened():
        cap = cv2.VideoCapture(0)
    if not cap.isOpened():
        emit_json({"status": "error", "message": "Cannot open webcam. Please verify device permissions."})
        return

    # Warm up camera sensor for 6 frames so auto-exposure stabilizes
    for _ in range(6):
        cap.read()
        time.sleep(0.04)

    samples_gray = []
    samples_geom = []
    best_color_crop = None
    start_time = time.time()
    max_duration = 5.0  # Allow up to 5 seconds to collect clean samples

    with mp_face.FaceDetection(min_detection_confidence=0.65) as detector:
        while time.time() - start_time < max_duration:
            ret, frame = cap.read()
            if not ret or frame is None:
                continue

            gray_face, color_face, geom, bbox = extract_landmarks_and_face(frame, detector)
            if gray_face is not None:
                samples_gray.append(gray_face)
                samples_geom.append(geom)
                if best_color_crop is None:
                    best_color_crop = color_face

                # Collect up to 12 distinct training frames
                if len(samples_gray) >= 12:
                    break
            time.sleep(0.05)

    cap.release()

    if len(samples_gray) < 3:
        emit_json({
            "status": "error",
            "message": "Insufficient face detection samples. Please face the camera directly in good lighting."
        })
        return

    # Compute averaged geometric template
    mean_geom = np.mean(np.array(samples_geom), axis=0).tolist()

    # Train OpenCV LBPH Face Recognizer
    lbph = cv2.face.LBPHFaceRecognizer_create(radius=1, neighbors=8, grid_x=8, grid_y=8)
    labels = np.zeros(len(samples_gray), dtype=np.int32)
    lbph.train(samples_gray, labels)
    lbph.write(MODEL_PATH)

    # Save highest quality color image for UI preview
    if best_color_crop is not None:
        cv2.imwrite(ENROLLED_IMG, best_color_crop)

    # Save profile metadata and template
    profile_data = {
        "created_at": time.time(),
        "sample_count": len(samples_gray),
        "mean_geom": mean_geom,
        "enrolled_image": ENROLLED_IMG,
        "model_file": MODEL_PATH
    }
    with open(PROFILE_PATH, "w") as f:
        json.dump(profile_data, f, indent=2)

    emit_json({
        "status": "enrolled",
        "message": f"Biometric face model enrolled ({len(samples_gray)} multi-angle samples trained)",
        "image": ENROLLED_IMG,
        "samples": len(samples_gray)
    })

# ── 5. VERIFICATION / RECOGNITION ───────────────────────────────────────────
def verify_face():
    ensure_profile_dir()
    if not os.path.exists(PROFILE_PATH) or not os.path.exists(MODEL_PATH):
        emit_json({"status": "error", "message": "No enrolled face biometric profile found"})
        return

    try:
        with open(PROFILE_PATH, "r") as f:
            enrolled = json.load(f)
        ref_geom = np.array(enrolled["mean_geom"])

        # Load trained LBPH model
        lbph = cv2.face.LBPHFaceRecognizer_create()
        lbph.read(MODEL_PATH)
    except Exception as e:
        emit_json({"status": "error", "message": f"Failed to load biometric model: {e}"})
        return

    mp_face = mp.solutions.face_detection
    cap = cv2.VideoCapture(0, cv2.CAP_DSHOW if sys.platform == "win32" else cv2.CAP_ANY)
    if not cap.isOpened():
        cap = cv2.VideoCapture(0)
    if not cap.isOpened():
        emit_json({"status": "error", "message": "Cannot open webcam device"})
        return

    # Warm up camera sensor (4 frames)
    for _ in range(4):
        cap.read()
        time.sleep(0.03)

    matched = False
    best_score = 0.0
    start_time = time.time()
    max_duration = 4.0

    with mp_face.FaceDetection(min_detection_confidence=0.55) as detector:
        while time.time() - start_time < max_duration:
            ret, frame = cap.read()
            if not ret or frame is None:
                continue

            gray_face, color_face, live_geom_list, bbox = extract_landmarks_and_face(frame, detector)
            if gray_face is not None:
                live_geom = np.array(live_geom_list)

                # 1. Geometric distance comparison
                geo_diff = float(np.mean(np.abs(ref_geom - live_geom) / (ref_geom + 1e-6)))
                geo_score = max(0.0, 1.0 - (geo_diff * 2.2))

                # 2. LBPH prediction confidence (distance: 0 is exact match, < 75 is strong match)
                label, lbph_dist = lbph.predict(gray_face)
                lbph_score = max(0.0, min(1.0, 1.0 - (lbph_dist / 115.0)))

                # Combined confidence score (weighted 50% LBPH texture + 50% geometric landmarks)
                combined_score = (lbph_score * 0.55) + (geo_score * 0.45)
                if combined_score > best_score:
                    best_score = combined_score

                # Threshold: strong match if combined >= 0.52 (LBPH distance < 75 and geom close)
                if combined_score >= 0.52 or (lbph_dist < 68.0 and geo_score > 0.45):
                    matched = True
                    cv2.imwrite(LIVE_IMG, color_face)
                    break

            time.sleep(0.04)

    cap.release()

    if matched:
        emit_json({
            "status": "match",
            "confidence": round(best_score, 3),
            "message": f"Biometric verified successfully ({int(best_score * 100)}% match)"
        })
    else:
        emit_json({
            "status": "no_match",
            "confidence": round(best_score, 3),
            "message": "Face did not match enrolled profile"
        })

# ── 6. DELETE BIOMETRICS ────────────────────────────────────────────────────
def delete_profile():
    for f in [PROFILE_PATH, MODEL_PATH, ENROLLED_IMG, LIVE_IMG]:
        if os.path.exists(f):
            try:
                os.remove(f)
            except Exception:
                pass
    emit_json({"status": "deleted", "message": "Biometric face profile and model removed from system"})

# ── 7. MAIN ENTRYPOINT ──────────────────────────────────────────────────────
if __name__ == "__main__":
    if len(sys.argv) < 2:
        emit_json({"status": "error", "message": "Missing command argument"})
        sys.exit(1)

    cmd = sys.argv[1].lower()
    if cmd in ["--register", "-r", "register"]:
        register_face()
    elif cmd in ["--verify", "-v", "verify"]:
        verify_face()
    elif cmd in ["--check", "-c", "check"]:
        check_webcam()
    elif cmd in ["--delete", "-d", "delete"]:
        delete_profile()
    else:
        emit_json({"status": "error", "message": f"Unknown command: {cmd}"})
