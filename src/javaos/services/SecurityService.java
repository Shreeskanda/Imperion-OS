package javaos.services;

import javaos.core.JavaOS;
import javaos.util.OSLogger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Properties;

/**
 * Enterprise security and authentication engine for IMPERION OS.
 * Manages salted SHA-256 cryptographic password hashing, PIN locks,
 * and biometric face recognition integration.
 */
public class SecurityService {

    public enum LockType {
        NONE, PIN, PASSWORD
    }

    private final JavaOS os;
    private final Path securityFile;
    private final Properties props = new Properties();

    private LockType currentLockType = LockType.NONE;
    private String passwordHash = "";
    private String passwordSalt = "";
    private boolean faceRecognitionEnabled = false;

    private int failedAttempts = 0;
    private long lockoutUntil = 0;

    public SecurityService(JavaOS os) {
        this.os = os;
        this.securityFile = Paths.get("data", "security", "security.properties");
        load();
    }

    public synchronized void load() {
        if (!Files.exists(securityFile)) {
            // Default: no lock
            currentLockType = LockType.NONE;
            passwordHash = "";
            passwordSalt = "";
            faceRecognitionEnabled = false;
            save();
            return;
        }

        try (FileInputStream in = new FileInputStream(securityFile.toFile())) {
            props.load(in);
            String typeStr = props.getProperty("lock.type", "NONE").toUpperCase();
            try {
                currentLockType = LockType.valueOf(typeStr);
            } catch (Exception e) {
                currentLockType = LockType.NONE;
            }
            passwordHash = props.getProperty("password.hash", "");
            passwordSalt = props.getProperty("password.salt", "");
            faceRecognitionEnabled = Boolean.parseBoolean(props.getProperty("face.enabled", "false"));

            // If lock type is set but no hash exists, revert to NONE
            if (currentLockType != LockType.NONE && (passwordHash.isEmpty() || passwordSalt.isEmpty())) {
                currentLockType = LockType.NONE;
            }
            OSLogger.info("SecuritySubsystem loaded: Lock=" + currentLockType + ", FaceAuth=" + faceRecognitionEnabled);
        } catch (Exception e) {
            OSLogger.error("Failed to load security properties: " + e.getMessage());
        }
    }

    public synchronized void save() {
        try {
            if (securityFile.getParent() != null && !Files.exists(securityFile.getParent())) {
                Files.createDirectories(securityFile.getParent());
            }
            props.setProperty("lock.type", currentLockType.name());
            props.setProperty("password.hash", passwordHash);
            props.setProperty("password.salt", passwordSalt);
            props.setProperty("face.enabled", String.valueOf(faceRecognitionEnabled));

            try (FileOutputStream out = new FileOutputStream(securityFile.toFile())) {
                props.store(out, "IMPERION OS Cryptographic Security State");
            }
        } catch (Exception e) {
            OSLogger.error("Failed to save security configuration: " + e.getMessage());
        }
    }

    public boolean isLockActive() {
        return currentLockType != LockType.NONE;
    }

    public LockType getLockType() {
        return currentLockType;
    }

    public boolean isFaceRecognitionEnabled() {
        return faceRecognitionEnabled;
    }

    public void setFaceRecognitionEnabled(boolean enabled) {
        this.faceRecognitionEnabled = enabled;
        save();
        if (os != null && os.getNotificationManager() != null) {
            os.getNotificationManager().postNotification(
                    "Biometric Security Updated",
                    "Face recognition " + (enabled ? "enabled (included in login)" : "disabled (excluded)"),
                    javaos.models.NotificationMessage.Level.INFO);
        }
    }

    public boolean isFaceEnrolled() {
        File enrolledImg = new File("data/security/face_enrolled.png");
        File profileJson = new File("data/security/face_profile.json");
        return enrolledImg.exists() && profileJson.exists() && profileJson.length() > 0;
    }

    /**
     * Authenticates a user attempt against the cryptographic hash.
     */
    public synchronized boolean authenticate(String attempt) {
        if (System.currentTimeMillis() < lockoutUntil) {
            return false;
        }

        if (currentLockType == LockType.NONE) {
            failedAttempts = 0;
            return true;
        }

        if (attempt == null || attempt.isEmpty()) {
            return false;
        }

        String computed = hashPassword(attempt, passwordSalt);
        if (computed.equalsIgnoreCase(passwordHash)) {
            failedAttempts = 0;
            return true;
        } else {
            failedAttempts++;
            if (failedAttempts >= 5) {
                // 30 seconds cooldown after 5 failed attempts
                lockoutUntil = System.currentTimeMillis() + 30000;
            }
            return false;
        }
    }

    /**
     * Sets or creates a new lock (PIN or Password).
     */
    public synchronized void createLock(LockType type, String password) {
        if (type == null || type == LockType.NONE || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Invalid password parameters");
        }

        this.passwordSalt = generateSalt();
        this.passwordHash = hashPassword(password, this.passwordSalt);
        this.currentLockType = type;
        this.failedAttempts = 0;
        this.lockoutUntil = 0;
        save();

        if (os != null && os.getNotificationManager() != null) {
            os.getNotificationManager().postNotification(
                    "Security Lock Activated",
                    "OS locked with " + type.name() + " authentication.",
                    javaos.models.NotificationMessage.Level.SUCCESS);
        }
    }

    /**
     * Changes existing password/PIN. Requires correct current password.
     */
    public synchronized boolean changeLock(String currentPassword, LockType newType, String newPassword) {
        if (!authenticate(currentPassword)) {
            return false;
        }
        createLock(newType, newPassword);
        return true;
    }

    /**
     * Completely removes password/PIN lock. Requires correct current password.
     */
    public synchronized boolean removeLock(String currentPassword) {
        if (currentLockType != LockType.NONE && !authenticate(currentPassword)) {
            return false;
        }

        this.currentLockType = LockType.NONE;
        this.passwordHash = "";
        this.passwordSalt = "";
        this.failedAttempts = 0;
        this.lockoutUntil = 0;
        save();

        if (os != null && os.getNotificationManager() != null) {
            os.getNotificationManager().postNotification(
                    "Security Lock Removed",
                    "Screen lock disabled. Workstation is now unlocked.",
                    javaos.models.NotificationMessage.Level.WARNING);
        }
        return true;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public boolean isLockedOut() {
        return System.currentTimeMillis() < lockoutUntil;
    }

    public long getLockoutRemainingSeconds() {
        return Math.max(0, (lockoutUntil - System.currentTimeMillis()) / 1000);
    }

    // ── BIOMETRIC FACE RECOGNITION BRIDGE ───────────────────────────────────

    public static class BiometricResult {
        private final boolean success;
        private final String status;
        private final String message;
        private final double confidence;

        public BiometricResult(boolean success, String status, String message, double confidence) {
            this.success = success;
            this.status = status != null ? status : "";
            this.message = message != null ? message : "";
            this.confidence = confidence;
        }

        public boolean isSuccess() { return success; }
        public String getStatus() { return status; }
        public String getMessage() { return message; }
        public double getConfidence() { return confidence; }
    }

    private String resolvePythonPath() {
        if (os != null && os.getSettingsService() != null) {
            String p = os.getSettingsService().getVictusPythonPath();
            if (p != null && !p.trim().isEmpty() && !p.equalsIgnoreCase("python")) {
                if (new File(p.trim()).exists()) {
                    return p.trim();
                }
            }
        }
        return "python";
    }

    private File getFaceScriptFile() {
        File f = new File("scripts/face_auth.py").getAbsoluteFile();
        return f;
    }

    public BiometricResult enrollFaceBiometricsDetailed() {
        Process p = null;
        try {
            File script = getFaceScriptFile();
            File workDir = script.getParentFile().getParentFile(); // JavaOS root

            ProcessBuilder pb = new ProcessBuilder(resolvePythonPath(), script.getAbsolutePath(), "--register");
            pb.directory(workDir);
            pb.redirectErrorStream(false);
            p = pb.start();

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("{") && line.endsWith("}")) {
                        sb.append(line);
                    }
                }
            }
            int code = p.waitFor();
            String output = sb.toString();

            String status = extractJsonField(output, "status");
            String message = extractJsonField(output, "message");
            boolean success = (code == 0) && "enrolled".equalsIgnoreCase(status);

            if (success) {
                OSLogger.info("Biometric registration succeeded: " + message);
            } else {
                OSLogger.warn("Biometric registration failed: " + message);
            }

            return new BiometricResult(success, status, message.isEmpty() ? "Registration failed" : message, 1.0);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            if (p != null && p.isAlive()) p.destroyForcibly();
            return new BiometricResult(false, "cancelled", "Face enrollment cancelled", 0.0);
        } catch (Exception e) {
            OSLogger.error("Failed to run face enrollment: " + e.getMessage());
            return new BiometricResult(false, "error", "Process execution failed: " + e.getMessage(), 0.0);
        } finally {
            if (p != null && p.isAlive()) {
                p.destroyForcibly();
            }
        }
    }

    public boolean enrollFaceBiometrics() {
        return enrollFaceBiometricsDetailed().isSuccess();
    }

    public BiometricResult verifyFaceBiometricsDetailed() {
        if (!isFaceEnrolled()) {
            return new BiometricResult(false, "error", "No enrolled face profile on record", 0.0);
        }
        Process p = null;
        try {
            File script = getFaceScriptFile();
            File workDir = script.getParentFile().getParentFile(); // JavaOS root

            ProcessBuilder pb = new ProcessBuilder(resolvePythonPath(), script.getAbsolutePath(), "--verify");
            pb.directory(workDir);
            pb.redirectErrorStream(false);
            p = pb.start();

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("{") && line.endsWith("}")) {
                        sb.append(line);
                    }
                }
            }
            int code = p.waitFor();
            String output = sb.toString();

            String status = extractJsonField(output, "status");
            String message = extractJsonField(output, "message");
            double confidence = extractJsonDouble(output, "confidence");
            boolean success = (code == 0) && "match".equalsIgnoreCase(status);

            return new BiometricResult(success, status, message.isEmpty() ? (success ? "Face verified" : "Face mismatch") : message, confidence);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            if (p != null && p.isAlive()) p.destroyForcibly();
            return new BiometricResult(false, "cancelled", "Face verification scan cancelled", 0.0);
        } catch (Exception e) {
            OSLogger.error("Failed to verify face biometrics: " + e.getMessage());
            return new BiometricResult(false, "error", "Verification process error: " + e.getMessage(), 0.0);
        } finally {
            if (p != null && p.isAlive()) {
                p.destroyForcibly();
            }
        }
    }

    public boolean verifyFaceBiometrics() {
        return verifyFaceBiometricsDetailed().isSuccess();
    }

    public void deleteFaceBiometrics() {
        try {
            File script = getFaceScriptFile();
            File workDir = script.getParentFile().getParentFile();
            ProcessBuilder pb = new ProcessBuilder(resolvePythonPath(), script.getAbsolutePath(), "--delete");
            pb.directory(workDir);
            Process p = pb.start();
            p.waitFor();
            setFaceRecognitionEnabled(false);
            OSLogger.info("Biometric profile wiped from system.");
        } catch (Exception ignored) {}
    }

    private String extractJsonField(String json, String field) {
        String pattern = "\"" + field + "\"\\s*:\\s*\"([^\"]+)\"";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (m.find()) return m.group(1);
        return "";
    }

    private double extractJsonDouble(String json, String field) {
        String pattern = "\"" + field + "\"\\s*:\\s*([0-9.]+)";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (m.find()) {
            try { return Double.parseDouble(m.group(1)); } catch (Exception ignored) {}
        }
        return 0.0;
    }

    // ── CRYPTOGRAPHIC HELPERS ──────────────────────────────────────────────

    private String generateSalt() {
        SecureRandom sr = new SecureRandom();
        byte[] salt = new byte[16];
        sr.nextBytes(salt);
        return bytesToHex(salt);
    }

    private String hashPassword(String password, String saltHex) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(saltHex.getBytes(StandardCharsets.UTF_8));
            byte[] hashed = md.digest(password.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashed);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 algorithm unavailable: " + e.getMessage());
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
