package javaos.services;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * ToolRegistry — detects and catalogues cybersecurity tools installed on the host.
 */
public class ToolRegistry {

    // ─────────────────────────── Department enum ───────────────────────────
    public enum Department {
        NETWORKING, VULNERABILITY, PENTEST, WEB_SECURITY,
        WIRELESS, PASSWORD, FORENSICS, MALWARE, DEFENSIVE, OSINT
    }

    // ─────────────────────────── ToolInfo record ───────────────────────────
    public static class ToolInfo {
        public final String id;
        public final String name;
        public final String description;
        public final String executable;
        public boolean installed;
        public String version;
        public String resolvedPath;
        public final Department dept;
        public final String osNote;

        public ToolInfo(String id, String name, String description,
                        String executable, Department dept, String osNote) {
            this.id          = id;
            this.name        = name;
            this.description = description;
            this.executable  = executable;
            this.dept        = dept;
            this.osNote      = osNote;
            this.installed   = false;
            this.version     = "";
            this.resolvedPath = executable;
        }
    }

    // ─────────────────────────── Fields ────────────────────────────────────
    private final Map<String, ToolInfo>       toolById = new LinkedHashMap<>();
    private final Map<Department, List<ToolInfo>> byDept = new EnumMap<>(Department.class);
    private static ToolRegistry instance;

    // ─────────────────────────── Singleton ─────────────────────────────────
    public static synchronized ToolRegistry getInstance() {
        if (instance == null) instance = new ToolRegistry();
        return instance;
    }

    private ToolRegistry() {
        for (Department d : Department.values()) byDept.put(d, new ArrayList<>());
        registerAll();
        Thread detectorThread = new Thread(this::detectAll, "ToolRegistry-Detector");
        detectorThread.setDaemon(true);
        detectorThread.start();
    }

    // ─────────────────────────── Registration ──────────────────────────────
    private void reg(String id, String name, String exe, Department dept,
                     String desc, String osNote) {
        ToolInfo t = new ToolInfo(id, name, desc, exe, dept, osNote);
        toolById.put(id, t);
        byDept.get(dept).add(t);
    }

    private void registerAll() {
        // NETWORKING
        reg("nmap",      "Nmap",       "nmap",         Department.NETWORKING,    "Network mapper and port scanner",                   "");
        reg("wireshark", "Wireshark",  "wireshark",    Department.NETWORKING,    "Network protocol analyzer (GUI launcher)",           "");
        reg("tshark",    "TShark",     "tshark",       Department.NETWORKING,    "Terminal-based Wireshark",                           "");
        reg("netcat",    "Netcat",     "ncat",         Department.NETWORKING,    "Network utility for TCP/UDP connections",             "Fallback: nc");

        // VULNERABILITY
        reg("openvas",   "Greenbone OpenVAS", "gvm-cli",   Department.VULNERABILITY, "Vulnerability management scanner",             "Linux/WSL");
        reg("nuclei",    "Nuclei",     "nuclei",       Department.VULNERABILITY, "Fast vulnerability scanner using templates",          "");
        reg("nikto",     "Nikto",      "nikto",        Department.VULNERABILITY, "Web server vulnerability scanner",                   "");

        // PENTEST
        reg("metasploit",    "Metasploit",    "msfconsole",   Department.PENTEST, "Penetration testing framework",                     "");
        reg("searchsploit",  "SearchSploit",  "searchsploit", Department.PENTEST, "Exploit database search",                           "");

        // WEB_SECURITY
        reg("burpsuite", "Burp Suite", "burpsuite",    Department.WEB_SECURITY,  "Web proxy and security testing (launcher)",          "");
        reg("owaspzap",  "OWASP ZAP",  "zap.sh",       Department.WEB_SECURITY,  "Web application security scanner",                   "");
        reg("sqlmap",    "SQLmap",     "sqlmap",       Department.WEB_SECURITY,  "SQL injection detection and exploitation",            "");
        reg("ffuf",      "ffuf",       "ffuf",         Department.WEB_SECURITY,  "Fast web fuzzer",                                    "");

        // WIRELESS
        reg("aircrack",  "Aircrack-ng","aircrack-ng",  Department.WIRELESS,      "Wireless security assessment",                       "Linux/WSL");
        reg("kismet",    "Kismet",     "kismet",       Department.WIRELESS,      "Wireless network detector",                          "Linux/WSL");

        // PASSWORD
        reg("john",      "John the Ripper", "john",    Department.PASSWORD,      "Password cracking and auditing",                     "");
        reg("hashcat",   "Hashcat",    "hashcat",      Department.PASSWORD,      "Advanced password recovery",                         "");
        reg("keepassxc", "KeePassXC",  "keepassxc",   Department.PASSWORD,      "Password manager (GUI launcher)",                    "");

        // FORENSICS
        reg("autopsy",   "Autopsy",    "autopsy",      Department.FORENSICS,     "Digital forensics platform",                         "Linux/WSL");
        reg("volatility","Volatility", "vol.py",       Department.FORENSICS,     "Memory forensics framework",                         "");
        reg("exiftool",  "ExifTool",   "exiftool",     Department.FORENSICS,     "Metadata extraction tool",                           "");

        // MALWARE
        reg("ghidra",    "Ghidra",     "ghidraRun",    Department.MALWARE,       "Reverse engineering platform (GUI launcher)",         "");
        reg("yara",      "YARA",       "yara",         Department.MALWARE,       "Malware pattern matching engine",                    "");
        reg("clamav",    "ClamAV",     "clamscan",     Department.MALWARE,       "Antivirus and malware scanner",                      "");
        reg("cyberchef", "CyberChef",  "N/A",          Department.MALWARE,       "Cyber operations utility (web-based)",               "Web-based");

        // DEFENSIVE
        reg("suricata",  "Suricata",   "suricata",     Department.DEFENSIVE,     "IDS/IPS network threat detection",                   "Linux/WSL");
        reg("zeek",      "Zeek",       "zeek",         Department.DEFENSIVE,     "Network analysis framework",                         "Linux/WSL");
        reg("wazuh",     "Wazuh",      "wazuh-agent",  Department.DEFENSIVE,     "Security monitoring platform",                       "Linux/WSL");
        reg("sysmon",    "Sysmon",     "Sysmon64",     Department.DEFENSIVE,     "Windows system monitoring (Windows native)",          "Windows");

        // OSINT
        reg("amass",     "Amass",      "amass",        Department.OSINT,         "Attack surface discovery and mapping",               "");
        reg("spiderfoot","SpiderFoot", "sf",           Department.OSINT,         "OSINT automation framework",                         "");
        reg("maltego",   "Maltego",    "maltego",      Department.OSINT,         "Visual link analysis (GUI launcher)",                 "");
    }

    // ─────────────────────────── Detection ─────────────────────────────────
    private void detectAll() {
        for (ToolInfo t : toolById.values()) {
            if ("N/A".equals(t.executable)) {
                t.installed = true;   // web-based — always "available"
                t.version   = "web";
                continue;
            }
            String resolved = resolveExecutable(t.executable);
            if (resolved != null) {
                t.installed = true;
                t.resolvedPath = resolved;
                t.version = detectVersion(resolved);
            } else {
                t.installed = false;
                t.resolvedPath = t.executable;
            }
        }
    }

    /** Resolves the executable path via PATH or standard Windows directories. */
    public String resolveExecutable(String executable) {
        if (detectTool(executable)) {
            return executable;
        }
        if (isWindows()) {
            List<String> candidates = new ArrayList<>();
            String exeLower = executable.toLowerCase();
            if (exeLower.equals("wireshark")) {
                candidates.add("C:\\Program Files\\Wireshark\\Wireshark.exe");
            } else if (exeLower.equals("tshark")) {
                candidates.add("C:\\Program Files\\Wireshark\\tshark.exe");
            } else if (exeLower.equals("nmap")) {
                candidates.add("C:\\Program Files (x86)\\Nmap\\nmap.exe");
                candidates.add("C:\\Program Files\\Nmap\\nmap.exe");
            } else if (exeLower.equals("ncat") || exeLower.equals("nc")) {
                candidates.add("C:\\Program Files (x86)\\Nmap\\ncat.exe");
                candidates.add("C:\\Program Files\\Nmap\\ncat.exe");
            } else if (exeLower.contains("sysmon")) {
                candidates.add("C:\\Windows\\System32\\Sysmon64.exe");
                candidates.add("C:\\Windows\\Sysmon64.exe");
            } else if (exeLower.equals("keepassxc")) {
                candidates.add("C:\\Program Files\\KeePassXC\\KeePassXC.exe");
                candidates.add("C:\\Program Files (x86)\\KeePassXC\\KeePassXC.exe");
            }
            for (String cand : candidates) {
                if (new java.io.File(cand).exists()) {
                    return cand;
                }
            }
        }
        return null;
    }

    /** Returns true if the executable can be located by the OS. */
    public boolean detectTool(String executable) {
        try {
            List<String> cmd = isWindows()
                    ? Arrays.asList("cmd", "/c", "where", executable)
                    : Arrays.asList("which", executable);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean finished = p.waitFor(5, TimeUnit.SECONDS);
            if (!finished) { p.destroyForcibly(); return false; }
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** Runs `executable --version` and returns the first output line. */
    public String detectVersion(String executable) {
        // Some tools use -V or -version
        for (String flag : new String[]{"--version", "-V", "-v", "-version", "version"}) {
            try {
                List<String> cmd = new ArrayList<>();
                if (isWindows()) { cmd.add("cmd"); cmd.add("/c"); }
                cmd.add(executable);
                cmd.add(flag);
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectErrorStream(true);
                Process p = pb.start();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(p.getInputStream()))) {
                    String line = br.readLine();
                    p.waitFor(4, TimeUnit.SECONDS);
                    if (line != null && !line.isBlank()) return line.trim();
                }
            } catch (Exception ignored) {}
        }
        return "unknown";
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    // ─────────────────────────── Accessors ─────────────────────────────────
    public List<ToolInfo> getToolsByDepartment(Department d) {
        return Collections.unmodifiableList(byDept.getOrDefault(d, Collections.emptyList()));
    }

    public ToolInfo getTool(String id) {
        return toolById.get(id);
    }

    public int getInstalledCount() {
        return (int) toolById.values().stream().filter(t -> t.installed).count();
    }

    public int getTotalCount() {
        return toolById.size();
    }

    public Map<String, ToolInfo> getAllTools() {
        return Collections.unmodifiableMap(toolById);
    }
}
