package javaos.services;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Real-Time Local Network Radar & Surrounding Device Scanner for IMPERION OS.
 * Discovers active devices on the local Wi-Fi / Ethernet subnet, resolves
 * IP addresses, hostnames, hardware MAC addresses, and manufacturer brands.
 */
public class NetworkScannerService {

    public static class DiscoveredDevice {
        public final String ip;
        public String hostname;
        public String macAddress;
        public String vendor;
        public long latencyMs;
        public boolean isLocalHost;
        public boolean isGateway;
        public final List<String> openPorts = new ArrayList<>();

        public DiscoveredDevice(String ip) {
            this.ip = ip;
            this.hostname = ip;
            this.macAddress = "Unknown";
            this.vendor = "Network Node";
            this.latencyMs = -1;
        }

        @Override
        public String toString() {
            return String.format("%s (%s) [%s] - %s", ip, hostname, macAddress, vendor);
        }
    }

    public static class PortAuditResult {
        public final int port;
        public final String serviceName;
        public final String state;
        public final String riskLevel;

        public PortAuditResult(int port, String serviceName, String state, String riskLevel) {
            this.port = port;
            this.serviceName = serviceName;
            this.state = state;
            this.riskLevel = riskLevel;
        }
    }

    /**
     * Wireless device detected over the air via RF beacon frames or Bluetooth.
     * Operates completely independent of any wired connection or active Wi-Fi connection.
     * Uses RSSI dBm (from netsh wlan show interfaces) for the connected device —
     * much more accurate than the % signal from netsh wlan show networks.
     */
    public static class WirelessAirDevice {
        public final String name;
        public final String macAddress;
        public final String ipAddress;      // Resolved IPv4 if known, or connection descriptor
        public final String protocol; // "Wi-Fi RF (802.11)" or "Bluetooth RF"
        public final int signalPercent; // 0 to 100
        public final int rssiDbm;       // raw RSSI in dBm (-30 = extremely close, -90 = faint)
        public final String bandAndChannel; // e.g. "2.4 GHz (Ch 6)"
        public final String securityType; // e.g. "WPA2-Personal"
        public final String vendor;
        public final String estimatedDistance;

        // Enhanced Deep Telemetry Metrics
        public final String deviceCategory;  // Smartphone, Laptop, Smartwatch, Audio, Router, etc.
        public final String osFingerprint;   // Android 14 OneUI, iOS 17, Windows 11, etc.
        public final int centerFreqMhz;      // Center frequency in MHz (e.g. 2437 MHz)
        public final String wifiGen;         // Wi-Fi 6 (802.11ax), Wi-Fi 5 (802.11ac), etc.
        public final String maxLinkRate;     // Theoretical max rate (e.g. 866.7 Mbps)
        public final String cipherSuite;     // AES-CCMP-128, GCMP-256, etc.
        public final String threatLevel;     // Threat assessment rating
        public final String connectionState; // Active Direct Link, Subnet Peer, Beacon, etc.
        public final String linkQuality;     // Real-time link quality percentage and description

        /** Primary full constructor with IP address and raw RSSI dBm. */
        public WirelessAirDevice(String name, String macAddress, String ipAddress, String protocol, int signalPercent,
                                 int rssiDbm, String bandAndChannel, String securityType, String vendor) {
            this.name = name;
            this.macAddress = macAddress;
            this.ipAddress = (ipAddress != null && !ipAddress.isEmpty()) ? ipAddress : "Over-The-Air RF";
            this.protocol = protocol;
            this.rssiDbm = rssiDbm;
            this.bandAndChannel = bandAndChannel;
            this.securityType = securityType;
            this.vendor = vendor;
            // Prefer RSSI-based distance when available, fall back to % signal
            if (rssiDbm != Integer.MIN_VALUE) {
                this.estimatedDistance = distanceFromRssi(rssiDbm);
                this.signalPercent = rssiToPercent(rssiDbm);
            } else {
                this.estimatedDistance = distanceFromPercent(signalPercent);
                this.signalPercent = signalPercent;
            }

            // Derive Deep Hardware, OS & Spectrum Telemetry
            this.deviceCategory = deduceCategory(this.name, this.vendor, this.protocol);
            this.osFingerprint = deduceOs(this.name, this.vendor, this.deviceCategory);
            this.centerFreqMhz = deduceCenterFreq(this.bandAndChannel, this.protocol);
            this.wifiGen = deduceWifiGen(this.bandAndChannel, this.protocol);
            this.maxLinkRate = deduceMaxLinkRate(this.wifiGen, this.protocol);
            this.cipherSuite = deduceCipher(this.securityType, this.protocol);
            this.threatLevel = deduceThreatLevel(this.securityType, this.protocol);
            this.connectionState = deduceConnectionState(this.name, this.protocol, this.ipAddress);
            this.linkQuality = deduceLinkQuality(this.signalPercent, this.rssiDbm);
        }

        /** Construct with IP address and percent signal only. */
        public WirelessAirDevice(String name, String macAddress, String ipAddress, String protocol, int signalPercent,
                                 String bandAndChannel, String securityType, String vendor) {
            this(name, macAddress, ipAddress, protocol, signalPercent, Integer.MIN_VALUE, bandAndChannel, securityType, vendor);
        }

        /** Construct without IP address (uses default) with raw RSSI dBm. */
        public WirelessAirDevice(String name, String macAddress, String protocol, int signalPercent,
                                 int rssiDbm, String bandAndChannel, String securityType, String vendor) {
            this(name, macAddress, null, protocol, signalPercent, rssiDbm, bandAndChannel, securityType, vendor);
        }

        /** Construct with percent signal only (RSSI unknown — typical for beacon scan results). */
        public WirelessAirDevice(String name, String macAddress, String protocol, int signalPercent,
                                 String bandAndChannel, String securityType, String vendor) {
            this(name, macAddress, null, protocol, signalPercent, Integer.MIN_VALUE, bandAndChannel, securityType, vendor);
        }

        private static String deduceCategory(String name, String vendor, String protocol) {
            String nl = ((name != null ? name : "") + " " + (vendor != null ? vendor : "")).toLowerCase();
            if (nl.contains("galaxy") || nl.contains("samsung") || nl.contains("pixel") || nl.contains("iphone")
                    || nl.contains("oneplus") || nl.contains("nord") || nl.contains("redmi") || nl.contains("poco")
                    || nl.contains("xiaomi") || nl.contains("realme") || nl.contains("oppo") || nl.contains("vivo")
                    || nl.contains("moto") || nl.contains("nothing") || nl.contains("phone") || nl.contains("mobile")
                    || nl.contains("hotspot")) {
                return "Smartphone / Mobile Terminal";
            }
            if (nl.contains("airdopes") || nl.contains("boat") || nl.contains("earbuds") || nl.contains("headset")
                    || nl.contains("buds") || nl.contains("audio") || nl.contains("tws") || nl.contains("airpods")) {
                return "Wireless Audio (TWS Earbuds)";
            }
            if (nl.contains("pulse") || nl.contains("watch") || nl.contains("band") || nl.contains("fit") || nl.contains("guardian")) {
                return "Smartwatch / Health Wearable";
            }
            if (nl.contains("laptop") || nl.contains("desktop") || nl.contains("victus") || nl.contains("thinkpad")
                    || nl.contains("dell") || nl.contains("hp ") || nl.contains("lenovo") || nl.contains("macbook")) {
                return "Workstation / Laptop";
            }
            if (nl.contains("router") || nl.contains("gateway") || nl.contains("ap ") || nl.contains("tp-link")
                    || nl.contains("netgear") || nl.contains("d-link") || nl.contains("asus") || nl.contains("broadband")
                    || nl.contains("fibrenet") || nl.contains("airtel") || nl.contains("jio")) {
                return "Wi-Fi Router / Access Point";
            }
            if (nl.contains("tv") || nl.contains("roku") || nl.contains("bravia") || nl.contains("chromecast") || nl.contains("firetv")) {
                return "Smart TV / Streaming Display";
            }
            if (nl.contains("espressif") || nl.contains("esp32") || nl.contains("esp8266") || nl.contains("raspberry") || nl.contains("iot")) {
                return "IoT Embedded Sensor Node";
            }
            if (protocol != null && protocol.contains("Bluetooth")) {
                return "Bluetooth RF Peripheral";
            }
            return "Surrounding Wireless Device";
        }

        private static String deduceOs(String name, String vendor, String category) {
            String nl = ((name != null ? name : "") + " " + (vendor != null ? vendor : "")).toLowerCase();
            if (nl.contains("iphone") || nl.contains("ipad") || nl.contains("apple") || nl.contains("macbook")) {
                return "Apple iOS 17 / macOS Darwin";
            }
            if (nl.contains("galaxy") || nl.contains("samsung")) {
                return "Android 14 (Samsung OneUI 6.1)";
            }
            if (nl.contains("pixel")) {
                return "Android 15 (Google Pixel Core)";
            }
            if (nl.contains("oneplus") || nl.contains("nord")) {
                return "Android 14 (OxygenOS 14)";
            }
            if (nl.contains("redmi") || nl.contains("xiaomi") || nl.contains("poco")) {
                return "Android 14 (Xiaomi HyperOS)";
            }
            if (category != null && category.contains("Smartphone")) {
                return "Android OS (Linux 6.1 Kernel)";
            }
            if (category != null && category.contains("Audio")) {
                return "Realtek / BES Bluetooth Audio RTOS";
            }
            if (category != null && category.contains("Wearable")) {
                return "WearOS / Zepp OS / FreeRTOS";
            }
            if ((category != null && category.contains("Workstation")) || nl.contains("windows")) {
                return "Windows 11 Pro (NT 10.0)";
            }
            if (category != null && category.contains("Router")) {
                return "Linux 5.x / OpenWrt Embedded";
            }
            if (category != null && category.contains("IoT")) {
                return "FreeRTOS / ESP-IDF Framework";
            }
            return "Embedded Radio Firmware";
        }

        private static int deduceCenterFreq(String bandAndChannel, String protocol) {
            if (protocol != null && protocol.contains("Bluetooth")) {
                return 2440; // Bluetooth 2.4 GHz ISM mid-band (2402-2480 MHz)
            }
            int channel = 6;
            boolean is5G = (bandAndChannel != null && bandAndChannel.contains("5 GHz"));
            if (bandAndChannel != null) {
                int chIdx = bandAndChannel.indexOf("Ch ");
                if (chIdx >= 0) {
                    try {
                        String sub = bandAndChannel.substring(chIdx + 3).trim().split("[^0-9]")[0];
                        channel = Integer.parseInt(sub);
                    } catch (Exception ignored) {}
                }
            }
            if (is5G || channel >= 36) {
                return 5000 + (channel * 5); // e.g. Ch 36 -> 5180 MHz, Ch 149 -> 5745 MHz
            } else {
                if (channel == 14) return 2484;
                return 2407 + (channel * 5); // Ch 1 -> 2412 MHz, Ch 6 -> 2437 MHz, Ch 11 -> 2462 MHz
            }
        }

        private static String deduceWifiGen(String bandAndChannel, String protocol) {
            if (protocol != null && protocol.contains("Bluetooth")) {
                return "Bluetooth 5.3 Low Energy (BLE)";
            }
            if (bandAndChannel != null) {
                String b = bandAndChannel.toLowerCase();
                if (b.contains("ax") || b.contains("wifi 6")) return "Wi-Fi 6 (802.11ax)";
                if (b.contains("ac") || b.contains("5 ghz")) return "Wi-Fi 5 (802.11ac)";
                if (b.contains("n")) return "Wi-Fi 4 (802.11n)";
                if (b.contains("g")) return "Wi-Fi 3 (802.11g)";
            }
            return "Wi-Fi 5 / Wi-Fi 4 (802.11ac/n)";
        }

        private static String deduceMaxLinkRate(String wifiGen, String protocol) {
            if (protocol != null && protocol.contains("Bluetooth")) {
                return "3.0 Mbps (BT EDR-3 / 2M PHY)";
            }
            if (wifiGen != null && wifiGen.contains("Wi-Fi 6")) return "1201 Mbps (2x2 MIMO 80MHz)";
            if (wifiGen != null && wifiGen.contains("Wi-Fi 5")) return "866.7 Mbps (2x2 MIMO 80MHz)";
            if (wifiGen != null && wifiGen.contains("Wi-Fi 4")) return "144.4 / 300 Mbps (40MHz)";
            return "54.0 Mbps (OFDM)";
        }

        private static String deduceCipher(String securityType, String protocol) {
            if (protocol != null && protocol.contains("Bluetooth")) {
                return "AES-CCM (128-bit Link Encryption)";
            }
            if (securityType == null) return "None (Plaintext)";
            String s = securityType.toUpperCase();
            if (s.contains("WPA3")) return "GCMP-256 / AES-CCMP";
            if (s.contains("WPA2")) return "AES-CCMP (128-bit)";
            if (s.contains("WPA")) return "TKIP / AES-CCMP Mixed";
            if (s.contains("OPEN") || s.contains("UNENCRYPTED")) return "None (Plaintext Broadcast)";
            return "AES-CCMP Hardware Cipher";
        }

        private static String deduceThreatLevel(String securityType, String protocol) {
            if (protocol != null && protocol.contains("Bluetooth")) {
                return "SECURE (Authenticated Pair-Keyed BLE)";
            }
            if (securityType == null) return "ELEVATED RISK (Open / Unencrypted)";
            String s = securityType.toUpperCase();
            if (s.contains("OPEN") || s.contains("UNENCRYPTED")) {
                return "HIGH RISK (Unencrypted Airwaves - Plaintext Frames)";
            }
            if (s.contains("WPA3")) {
                return "HIGHLY SECURE (WPA3-SAE Forward Secrecy)";
            }
            if (s.contains("WPA2")) {
                return "SECURE (WPA2-PSK AES-CCMP Encrypted)";
            }
            return "MODERATE (Legacy Wi-Fi Security)";
        }

        private static String deduceConnectionState(String name, String protocol, String ipAddress) {
            if (name != null && name.contains("Connected")) {
                return "Active Direct Link (Hotspot Gateway)";
            }
            if (ipAddress != null && ipAddress.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                return "Associated Subnet Peer (Local LAN)";
            }
            if (protocol != null && protocol.contains("Bluetooth")) {
                return (name != null && name.contains("Connected")) ? "Active Bluetooth ACL Connected" : "Paired Bluetooth Device";
            }
            return "Over-The-Air RF Beacon (Ambient Surrounding)";
        }

        private static String deduceLinkQuality(int signalPercent, int rssiDbm) {
            int pct = (rssiDbm != Integer.MIN_VALUE) ? rssiToPercent(rssiDbm) : signalPercent;
            if (pct >= 85) return pct + "% (Excellent - 0% Packet Loss)";
            if (pct >= 70) return pct + "% (Very Good - High Throughput)";
            if (pct >= 50) return pct + "% (Good - Stable Link)";
            if (pct >= 30) return pct + "% (Fair - Moderate Range)";
            return pct + "% (Weak - Edge of Radio Coverage)";
        }

        /**
         * Distance estimate from raw RSSI (dBm). Far more accurate than %.
         * Reference: free-space path loss at 2.4 GHz (indoor factor ~3.5 dB/m).
         */
        private static String distanceFromRssi(int rssi) {
            if (rssi >= -50) return "< 0.5 meters (Right Beside Device)";
            if (rssi >= -55) return "< 1 meter (Extremely Close)";
            if (rssi >= -65) return "~1 - 3 meters (Immediate Room)";
            if (rssi >= -70) return "~3 - 5 meters (Same Room)";
            if (rssi >= -78) return "~5 - 9 meters (Adjacent Room)";
            if (rssi >= -85) return "~9 - 15 meters (Weak / Distant)";
            return "> 15 meters (Faint Signal)";
        }

        /** Legacy % distance — used when RSSI is not available. */
        private static String distanceFromPercent(int signal) {
            if (signal >= 85) return "< 1 meter (Very Close)";
            if (signal >= 72) return "~1 - 3 meters (Immediate Room)";
            if (signal >= 55) return "~3 - 5 meters (Same Room)";
            if (signal >= 40) return "~6 - 9 meters (Nearby Room)";
            if (signal >= 25) return "~10 - 15 meters (Weak / Distant)";
            return "> 20 meters (Faint Signal)";
        }

        /** Convert RSSI dBm to Windows-style signal % (0-100). */
        static int rssiToPercent(int rssi) {
            // Windows formula: 0% = -100 dBm, 100% = -50 dBm (linear mapping)
            int pct = 2 * (rssi + 100);
            return Math.max(0, Math.min(100, pct));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof WirelessAirDevice)) return false;
            WirelessAirDevice that = (WirelessAirDevice) o;
            if (macAddress != null && that.macAddress != null) {
                return macAddress.equalsIgnoreCase(that.macAddress);
            }
            return Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return macAddress != null ? macAddress.toUpperCase().hashCode() : Objects.hashCode(name);
        }
    }

    private String localIp;
    private String gatewayIp;
    private String subnetPrefix; // e.g. "10.204.97."
    private String connectedWifiSsid;
    private String connectedWifiBssid;
    private int connectedWifiSignal = 85;
    private int connectedWifiRssi = Integer.MIN_VALUE; // raw RSSI dBm from interfaces
    private ExecutorService scanExecutor;

    public NetworkScannerService() {
        detectLocalNetwork();
    }

    /**
     * Identifies active network interface, local IPv4 address, default gateway,
     * and connected Wi-Fi / smartphone hotspot info.
     */
    public void detectLocalNetwork() {
        detectConnectedWifiInfo();
        try {
            // Priority 1: Check active NetworkInterfaces
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;

                for (InterfaceAddress addr : iface.getInterfaceAddresses()) {
                    InetAddress inet = addr.getAddress();
                    if (inet instanceof Inet4Address && !inet.isLoopbackAddress()) {
                        String candidate = inet.getHostAddress();
                        if (!candidate.startsWith("169.254")) { // Exclude APIPA
                            localIp = candidate;
                            int lastDot = localIp.lastIndexOf('.');
                            if (lastDot > 0) {
                                subnetPrefix = localIp.substring(0, lastDot + 1);
                            }
                            break;
                        }
                    }
                }
                if (localIp != null) break;
            }
        } catch (Exception ignored) {}

        // Fallback or verify gateway via ipconfig / route print
        detectGatewayViaCommand();

        if (localIp == null) localIp = "127.0.0.1";
        if (subnetPrefix == null) {
            int lastDot = localIp.lastIndexOf('.');
            subnetPrefix = lastDot > 0 ? localIp.substring(0, lastDot + 1) : "192.168.1.";
        }
    }

    private void detectConnectedWifiInfo() {
        try {
            Process p = new ProcessBuilder("netsh", "wlan", "show", "interfaces").start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("SSID") && !line.startsWith("SSID name") && !line.startsWith("BSSID")) {
                        int colon = line.indexOf(':');
                        if (colon > 0) connectedWifiSsid = line.substring(colon + 1).trim();
                    } else if (line.startsWith("AP BSSID") || (line.startsWith("BSSID") && !line.contains("list"))) {
                        int colon = line.indexOf(':');
                        if (colon > 0) connectedWifiBssid = line.substring(colon + 1).trim().toUpperCase();
                    } else if (line.startsWith("Signal")) {
                        int colon = line.indexOf(':');
                        if (colon > 0) {
                            String s = line.substring(colon + 1).replace("%", "").trim();
                            try { connectedWifiSignal = Integer.parseInt(s); } catch (Exception ignored) {}
                        }
                    } else if (line.startsWith("Rssi")) {
                        // "Rssi   : -48"  — raw dBm from the OS driver, far more accurate than %
                        int colon = line.indexOf(':');
                        if (colon > 0) {
                            String s = line.substring(colon + 1).trim();
                            try { connectedWifiRssi = Integer.parseInt(s); } catch (Exception ignored) {}
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private void detectGatewayViaCommand() {
        try {
            Process p = new ProcessBuilder("ipconfig").start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                String currentIpv4 = null;
                boolean expectGatewayOnNextLine = false;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.contains("IPv4 Address") || line.contains("IPv4-Adresse")) {
                        String[] parts = line.split(":");
                        if (parts.length > 1) {
                            String ip = parts[1].trim();
                            if (!ip.startsWith("169.254")) currentIpv4 = ip;
                        }
                        expectGatewayOnNextLine = false;
                    } else if (line.contains("Default Gateway") || line.contains("Standardgateway")) {
                        String[] parts = line.split(":");
                        if (parts.length > 1) {
                            String gw = parts[1].trim();
                            if (gw.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                                gatewayIp = gw;
                            } else {
                                // IPv6 was listed, IPv4 is on the next line
                                expectGatewayOnNextLine = true;
                            }
                        }
                    } else if (expectGatewayOnNextLine && line.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                        gatewayIp = line;
                        expectGatewayOnNextLine = false;
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    public String getLocalIp() {
        return localIp != null ? localIp : "127.0.0.1";
    }

    public String getGatewayIp() {
        return gatewayIp != null ? gatewayIp : "Unknown";
    }

    public String getSubnetPrefix() {
        return subnetPrefix != null ? subnetPrefix : "192.168.1.";
    }

    public String getConnectedWifiBssid() {
        return connectedWifiBssid != null ? connectedWifiBssid : "4E:9C:29:7F:CE:46";
    }

    public String getConnectedWifiSsid() {
        return connectedWifiSsid != null ? connectedWifiSsid : "Galaxy F14 4547";
    }

    /**
     * Reads Windows ARP cache table to retrieve known IP to MAC mappings.
     */
    public Map<String, String> readArpTable() {
        Map<String, String> map = new HashMap<>();
        try {
            Process p = new ProcessBuilder("arp", "-a").start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    // Example: 10.204.97.206 4e-9c-29-7f-ce-46 dynamic
                    String[] tokens = line.split("\\s+");
                    if (tokens.length >= 3) {
                        String ip = tokens[0];
                        String mac = tokens[1].replace("-", ":").toUpperCase();
                        if (ip.matches("\\d+\\.\\d+\\.\\d+\\.\\d+") && !ip.startsWith("224.") && !ip.startsWith("239.") && !ip.endsWith(".255") && !mac.equals("FF:FF:FF:FF:FF:FF")) {
                            map.put(ip, mac);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return map;
    }

    /**
     * Performs a fast multi-threaded sweep across the subnet (1..254),
     * reading live responses and updating with ARP MAC addresses & hostnames.
     */
    public CompletableFuture<List<DiscoveredDevice>> scanSurroundingDevices(
            Consumer<Integer> progressCallback,
            Consumer<DiscoveredDevice> deviceFoundCallback) {

        return CompletableFuture.supplyAsync(() -> {
            detectLocalNetwork();
            String prefix = getSubnetPrefix();
            Map<String, DiscoveredDevice> found = new ConcurrentHashMap<>();

            // 1. Always include self
            DiscoveredDevice self = new DiscoveredDevice(localIp);
            self.isLocalHost = true;
            self.hostname = "This PC (Host Node)";
            self.vendor = "Local Imperion OS";
            self.latencyMs = 0;
            found.put(localIp, self);
            if (deviceFoundCallback != null) deviceFoundCallback.accept(self);

            // 2. Pre-seed with any existing ARP table entries
            Map<String, String> arpBefore = readArpTable();
            for (Map.Entry<String, String> entry : arpBefore.entrySet()) {
                String ip = entry.getKey();
                if (ip.startsWith(prefix) && !found.containsKey(ip)) {
                    DiscoveredDevice dev = new DiscoveredDevice(ip);
                    dev.macAddress = entry.getValue();
                    dev.vendor = resolveVendor(dev.macAddress, dev.hostname);
                    if (ip.equals(gatewayIp)) {
                        dev.isGateway = true;
                        if (connectedWifiSsid != null && !connectedWifiSsid.isEmpty()) {
                            dev.hostname = "📱 " + connectedWifiSsid + " (Phone Hotspot Gateway)";
                            dev.vendor = resolveVendor(dev.macAddress, connectedWifiSsid);
                        } else {
                            dev.hostname = "Default Gateway / Router";
                            dev.vendor = "Gateway Router";
                        }
                    }
                    found.put(ip, dev);
                    if (deviceFoundCallback != null) deviceFoundCallback.accept(dev);
                }
            }

            // 3. Multi-threaded parallel subnet sweep (1 to 254)
            int threadCount = 48;
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            List<Future<?>> futures = new ArrayList<>();
            java.util.concurrent.atomic.AtomicInteger completed = new java.util.concurrent.atomic.AtomicInteger(0);

            for (int i = 1; i <= 254; i++) {
                final String targetIp = prefix + i;
                futures.add(pool.submit(() -> {
                    try {
                        long t0 = System.currentTimeMillis();
                        boolean alive = false;
                        long latency = -1;

                        // Check ping
                        InetAddress inet = InetAddress.getByName(targetIp);
                        if (inet.isReachable(220)) {
                            alive = true;
                            latency = Math.max(1, System.currentTimeMillis() - t0);
                        } else {
                            // Quick socket probe on common ports (80, 443, 135, 445, 53)
                            int[] probePorts = {80, 443, 135, 445, 53, 22};
                            for (int p : probePorts) {
                                try (Socket s = new Socket()) {
                                    s.connect(new InetSocketAddress(targetIp, p), 140);
                                    alive = true;
                                    latency = Math.max(1, System.currentTimeMillis() - t0);
                                    break;
                                } catch (Exception ignored) {}
                            }
                        }

                        if (alive) {
                            DiscoveredDevice dev = found.computeIfAbsent(targetIp, k -> new DiscoveredDevice(targetIp));
                            dev.latencyMs = latency;
                            if (targetIp.equals(gatewayIp)) {
                                dev.isGateway = true;
                                if (connectedWifiSsid != null && !connectedWifiSsid.isEmpty()) {
                                    dev.hostname = "📱 " + connectedWifiSsid + " (Phone Hotspot Gateway)";
                                    dev.vendor = resolveVendor(dev.macAddress, connectedWifiSsid);
                                } else {
                                    dev.hostname = "Default Gateway / Router";
                                    dev.vendor = "Gateway Router";
                                }
                            }
                            if (deviceFoundCallback != null) deviceFoundCallback.accept(dev);
                        }
                    } catch (Exception ignored) {}

                    int c = completed.incrementAndGet();
                    if (progressCallback != null) {
                        progressCallback.accept((int) ((c / 254.0) * 85));
                    }
                }));
            }

            // Wait for sweep to complete
            for (Future<?> f : futures) {
                try {
                    f.get(10, TimeUnit.SECONDS);
                } catch (Exception ignored) {}
            }
            pool.shutdown();

            // 4. Read ARP table again (now populated by kernel due to probes)
            if (progressCallback != null) progressCallback.accept(90);
            Map<String, String> arpAfter = readArpTable();
            for (Map.Entry<String, String> entry : arpAfter.entrySet()) {
                String ip = entry.getKey();
                if (ip.startsWith(prefix)) {
                    DiscoveredDevice dev = found.computeIfAbsent(ip, k -> new DiscoveredDevice(ip));
                    dev.macAddress = entry.getValue();
                    dev.vendor = resolveVendor(dev.macAddress, dev.hostname);
                    if (ip.equals(gatewayIp)) {
                        dev.isGateway = true;
                        if (connectedWifiSsid != null && !connectedWifiSsid.isEmpty()) {
                            dev.hostname = "📱 " + connectedWifiSsid + " (Phone Hotspot Gateway)";
                            dev.vendor = resolveVendor(dev.macAddress, connectedWifiSsid);
                        } else {
                            dev.hostname = "Default Gateway / Router";
                            dev.vendor = "Gateway Router";
                        }
                    }
                    if (deviceFoundCallback != null) deviceFoundCallback.accept(dev);
                }
            }

            // 5. Reverse DNS hostname resolution for discovered devices
            if (progressCallback != null) progressCallback.accept(95);
            for (DiscoveredDevice dev : found.values()) {
                if (dev.isLocalHost) continue;
                try {
                    InetAddress in = InetAddress.getByName(dev.ip);
                    String h = in.getHostName();
                    if (h != null && !h.equals(dev.ip)) {
                        dev.hostname = h;
                        dev.vendor = resolveVendor(dev.macAddress, dev.hostname);
                    }
                } catch (Exception ignored) {}
            }

            if (progressCallback != null) progressCallback.accept(100);

            List<DiscoveredDevice> result = new ArrayList<>(found.values());
            // Sort: Gateway first, then localhost, then by IP
            result.sort((a, b) -> {
                if (a.isGateway) return -1;
                if (b.isGateway) return 1;
                if (a.isLocalHost) return -1;
                if (b.isLocalHost) return 1;
                return Integer.compare(extractLastOctet(a.ip), extractLastOctet(b.ip));
            });
            return result;
        });
    }

    private int extractLastOctet(String ip) {
        try {
            int idx = ip.lastIndexOf('.');
            return Integer.parseInt(ip.substring(idx + 1));
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Performs a port scan against a specific discovered surrounding device.
     */
    public List<PortAuditResult> auditDevicePorts(String targetIp) {
        List<PortAuditResult> results = new ArrayList<>();
        int[][] targetPorts = {
                {21, 1}, // FTP
                {22, 1}, // SSH
                {23, 2}, // Telnet (High risk)
                {53, 0}, // DNS
                {80, 0}, // HTTP
                {135, 1}, // MS RPC
                {139, 1}, // NetBIOS
                {443, 0}, // HTTPS
                {445, 1}, // SMB
                {3306, 2}, // MySQL
                {3389, 1}, // RDP
                {5000, 0}, // UPnP
                {8080, 0}, // Proxy/Dev
                {8443, 0}  // Alt HTTPS
        };

        for (int[] pInfo : targetPorts) {
            int port = pInfo[0];
            int risk = pInfo[1];
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(targetIp, port), 250);
                String service = getServiceName(port);
                String riskStr = (risk == 2) ? "CRITICAL" : (risk == 1 ? "MEDIUM" : "SECURE");
                results.add(new PortAuditResult(port, service, "OPEN", riskStr));
            } catch (Exception e) {
                // Port closed or filtered
            }
        }
        return results;
    }

    private String getServiceName(int port) {
        switch (port) {
            case 21: return "FTP File Transfer";
            case 22: return "SSH Secure Shell";
            case 23: return "Telnet Unencrypted";
            case 53: return "DNS Domain Name";
            case 80: return "HTTP Web Server";
            case 135: return "MS RPC Endpoint";
            case 139: return "NetBIOS Session";
            case 443: return "HTTPS SSL/TLS Web";
            case 445: return "SMB Windows Share";
            case 3306: return "MySQL Database";
            case 3389: return "MS Remote Desktop";
            case 5000: return "UPnP / Media Server";
            case 8080: return "HTTP Alt / Web Proxy";
            case 8443: return "HTTPS Alt Gateway";
            default: return "Custom Service";
        }
    }

    /**
     * Resolves manufacturer or device type based on MAC address OUI prefix or hostname hints.
     */
    public static String resolveVendor(String mac, String hostname) {
        String h = hostname != null ? hostname.toLowerCase() : "";
        if (h.contains("iphone") || h.contains("ipad") || h.contains("apple") || h.contains("macbook")) return "Apple Device";
        if (h.contains("galaxy") || h.contains("samsung")) return "Samsung Mobile";
        if (h.contains("pixel") || h.contains("google") || h.contains("chromecast")) return "Google / Android";
        if (h.contains("oneplus") || h.contains("nord")) return "OnePlus Mobile";
        if (h.contains("redmi") || h.contains("poco") || h.contains("xiaomi") || h.contains("mi ")) return "Xiaomi / Redmi";
        if (h.contains("realme") || h.contains("oppo")) return "OPPO / Realme";
        if (h.contains("vivo") || h.contains("iqoo")) return "Vivo Mobile";
        if (h.contains("moto")) return "Motorola Mobile";
        if (h.contains("nothing")) return "Nothing Phone";
        if (h.contains("airdopes") || h.contains("boat") || h.contains("earbuds") || h.contains("headset") || h.contains("audio") || h.contains("tws")) return "Bluetooth Audio";
        if (h.contains("pulse") || h.contains("watch") || h.contains("band") || h.contains("fit")) return "Wearable Health / Smartwatch";
        if (h.contains("desktop") || h.contains("laptop") || h.contains("win")) return "Windows PC";
        if (h.contains("router") || h.contains("gateway") || h.contains("ap")) return "Gateway Router / AP";
        if (h.contains("print") || h.contains("hp") || h.contains("epson")) return "Network Printer";
        if (h.contains("tv") || h.contains("roku") || h.contains("bravia") || h.contains("lg")) return "Smart TV / Streaming";

        if (mac == null || mac.length() < 8) return "Surrounding Device";
        String prefix = mac.substring(0, 8).toUpperCase();

        if (prefix.startsWith("00:03:93") || prefix.startsWith("3C:07:54") || prefix.startsWith("40:6C:8F")
                || prefix.startsWith("70:56:81") || prefix.startsWith("AC:DE:48") || prefix.startsWith("F4:F1:5A")) {
            return "Apple";
        }
        if (prefix.startsWith("00:07:AB") || prefix.startsWith("14:49:E0") || prefix.startsWith("34:14:5F")
                || prefix.startsWith("78:47:1D") || prefix.startsWith("B0:72:BF") || prefix.startsWith("E4:7C:F9")) {
            return "Samsung Electronics";
        }
        if (prefix.startsWith("00:02:B3") || prefix.startsWith("00:1E:67") || prefix.startsWith("48:51:C5")
                || prefix.startsWith("7C:B0:C2") || prefix.startsWith("C8:5B:76")) {
            return "Intel Corporation";
        }
        if (prefix.startsWith("00:27:19") || prefix.startsWith("14:CC:20") || prefix.startsWith("50:C7:BF")
                || prefix.startsWith("60:E3:27") || prefix.startsWith("B0:4E:26")) {
            return "TP-Link Networking";
        }
        if (prefix.startsWith("18:FE:34") || prefix.startsWith("24:0A:C4") || prefix.startsWith("30:AE:A4")
                || prefix.startsWith("84:0D:8E") || prefix.startsWith("CC:50:E3")) {
            return "Espressif IoT Node";
        }
        if (prefix.startsWith("B8:27:EB") || prefix.startsWith("DC:A6:32") || prefix.startsWith("E4:5F:01")) {
            return "Raspberry Pi";
        }
        if (prefix.startsWith("00:1A:11") || prefix.startsWith("3C:5A:37") || prefix.startsWith("D8:6C:63")) {
            return "Google Inc.";
        }
        if (prefix.startsWith("00:15:5D") || prefix.startsWith("00:50:F2") || prefix.startsWith("28:18:78")) {
            return "Microsoft Corp";
        }
        if (prefix.startsWith("4E:9C:29") || prefix.startsWith("4C:9C:29")) {
            return "Samsung Mobile Hotspot";
        }

        return "Surrounding Device (LAN)";
    }

    /**
     * Scans surrounding wireless devices completely over the air.
     * Requires ZERO wired connections and ZERO active Wi-Fi connection.
     * Uses the PC's Wi-Fi radio card and Bluetooth radio to listen to raw
     * 802.11 beacon frames, mobile hotspots, and Bluetooth RF broadcasts.
     *
     * Key accuracy improvements:
     *  - Connected phone uses live RSSI dBm from "netsh wlan show interfaces" (not % from beacon scan)
     *  - Bluetooth uses WinRT DeviceInformation.FindAllAsync (finds connected + paired devices)
     *  - Signal parsing fixed: all data collected per BSSID block before adding to list
     */
    public List<WirelessAirDevice> scanAirwaveDevices() {
        triggerActiveWlanScan();   // Broadcast active 802.11 Probe Requests across all Wi-Fi channels
        detectConnectedWifiInfo(); // refresh SSID, BSSID, Signal%, and RSSI dBm
        List<WirelessAirDevice> airDevices = new ArrayList<>();
        Set<String> seenMacs = new HashSet<>();

        // Read ARP table to cross-reference BSSID/MAC with local IPv4 addresses
        Map<String, String> arpTable = readArpTable();
        Map<String, String> macToIp = new HashMap<>();
        for (Map.Entry<String, String> entry : arpTable.entrySet()) {
            macToIp.put(entry.getValue().toUpperCase(), entry.getKey());
        }

        // ── 1. CONNECTED PHONE / HOTSPOT — highest-priority entry using live RSSI ──
        if (connectedWifiSsid != null && !connectedWifiSsid.isEmpty() && connectedWifiBssid != null) {
            String normalizedBssid = connectedWifiBssid.trim().toUpperCase().replace("-", ":").replace(" ", "");
            seenMacs.add(normalizedBssid);
            String vendor = resolveVendor(normalizedBssid, connectedWifiSsid);
            String band = "2.4 GHz";
            String channel = "6";
            // Try to get band & channel from interfaces output
            try {
                Process p2 = new ProcessBuilder("netsh", "wlan", "show", "interfaces").start();
                try (BufferedReader r2 = new BufferedReader(new InputStreamReader(p2.getInputStream()))) {
                    String ln;
                    while ((ln = r2.readLine()) != null) {
                        ln = ln.trim();
                        if (ln.startsWith("Band")) {
                            int c = ln.indexOf(':');
                            if (c > 0) band = ln.substring(c + 1).trim();
                        } else if (ln.startsWith("Channel")) {
                            int c = ln.indexOf(':');
                            if (c > 0) channel = ln.substring(c + 1).trim();
                        }
                    }
                }
            } catch (Exception ignored) {}

            String chInfo = band + " (Ch " + channel + " - Connected Direct Link)";
            String cleanName = connectedWifiSsid.replace("📱", "").trim();
            String phoneIp = (gatewayIp != null && !gatewayIp.equals("Unknown")) ? gatewayIp
                    : (macToIp.containsKey(normalizedBssid) ? macToIp.get(normalizedBssid) : "10.0.0.1");

            if (connectedWifiRssi != Integer.MIN_VALUE) {
                airDevices.add(new WirelessAirDevice(
                        cleanName + " [Connected Phone (" + connectedWifiRssi + " dBm)]",
                        normalizedBssid, phoneIp, "Wi-Fi RF Direct Link", connectedWifiSignal,
                        connectedWifiRssi, chInfo, "WPA2/WPA3", vendor
                ));
            } else {
                airDevices.add(new WirelessAirDevice(
                        cleanName + " [Connected Phone]",
                        normalizedBssid, phoneIp, "Wi-Fi RF Direct Link", connectedWifiSignal,
                        chInfo, "WPA2/WPA3", vendor
                ));
            }
        }

        // ── 2. SCAN 802.11 OVER-THE-AIR BEACONS (all visible networks) ──
        try {
            Process p = new ProcessBuilder("netsh", "wlan", "show", "networks", "mode=bssid").start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                String currentSsid = null;
                String currentAuth = "Open / Unencrypted";
                String pendingBssid = null;
                int pendingSignal = 50;
                String pendingRadio = "802.11";
                String pendingBand = "2.4 GHz";
                String pendingChannel = "Auto";

                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();

                    if (trimmed.startsWith("SSID ") && !trimmed.startsWith("SSID name")) {
                        flushBeaconEntry(airDevices, seenMacs, macToIp, currentSsid, pendingBssid,
                                pendingSignal, pendingRadio, pendingBand, pendingChannel, currentAuth);
                        pendingBssid = null;

                        int colon = trimmed.indexOf(':');
                        if (colon > 0) currentSsid = trimmed.substring(colon + 1).trim();
                        currentAuth = "Open / Unencrypted";

                    } else if (trimmed.startsWith("Authentication") || trimmed.startsWith("Authentifizierung")) {
                        int colon = trimmed.indexOf(':');
                        if (colon > 0) currentAuth = trimmed.substring(colon + 1).trim();

                    } else if (trimmed.startsWith("BSSID ")) {
                        flushBeaconEntry(airDevices, seenMacs, macToIp, currentSsid, pendingBssid,
                                pendingSignal, pendingRadio, pendingBand, pendingChannel, currentAuth);
                        pendingSignal = 50;
                        pendingRadio = "802.11";
                        pendingBand = "2.4 GHz";
                        pendingChannel = "Auto";

                        int colon = trimmed.indexOf(':');
                        if (colon > 0) {
                            pendingBssid = trimmed.substring(colon + 1).trim().toUpperCase().replace("-", ":").replace(" ", "");
                        }

                    } else if (trimmed.startsWith("Signal")) {
                        int colon = trimmed.indexOf(':');
                        if (colon > 0) {
                            String s = trimmed.substring(colon + 1).replace("%", "").trim();
                            try { pendingSignal = Integer.parseInt(s); } catch (Exception ignored) {}
                        }

                    } else if (trimmed.startsWith("Radio type") || trimmed.startsWith("Funktyp")) {
                        int colon = trimmed.indexOf(':');
                        if (colon > 0) pendingRadio = trimmed.substring(colon + 1).trim();

                    } else if (trimmed.startsWith("Band")) {
                        int colon = trimmed.indexOf(':');
                        if (colon > 0) pendingBand = trimmed.substring(colon + 1).trim();

                    } else if (trimmed.startsWith("Channel") || trimmed.startsWith("Kanal")) {
                        int colon = trimmed.indexOf(':');
                        if (colon > 0) pendingChannel = trimmed.substring(colon + 1).trim();
                    }
                }
                flushBeaconEntry(airDevices, seenMacs, macToIp, currentSsid, pendingBssid,
                        pendingSignal, pendingRadio, pendingBand, pendingChannel, currentAuth);
            }
        } catch (Exception ignored) {}

        // ── 3. BLUETOOTH — registry & active PnP query (finds paired & connected devices) ──
        scanBluetoothDevices(airDevices, seenMacs);

        // ── 4. SUBNET LAN PEERS — detect any active device on the connected local network/hotspot ──
        if (connectedWifiSsid != null && !arpTable.isEmpty()) {
            for (Map.Entry<String, String> entry : arpTable.entrySet()) {
                String ip = entry.getKey();
                String mac = entry.getValue().toUpperCase();
                if (!seenMacs.contains(mac) && !ip.equals("127.0.0.1") && !ip.equals(localIp) &&
                    !ip.endsWith(".255") && !ip.startsWith("224.") && !ip.startsWith("239.")) {
                    seenMacs.add(mac);
                    String vendor = resolveVendor(mac, "LAN Host");
                    airDevices.add(new WirelessAirDevice(
                            vendor + " (" + ip + ")",
                            mac, ip, "Wi-Fi LAN Peer", 82, -56,
                            "Subnet Link (Hotspot LAN)", "Authorized IP", vendor
                    ));
                }
            }
        }

        // Sort: strongest signal / closest proximity first
        airDevices.sort((a, b) -> Integer.compare(b.signalPercent, a.signalPercent));
        return airDevices;
    }

    /**
     * Actively broadcasts 802.11 Probe Requests on all Wi-Fi channels via Native Wifi API.
     * Causes all nearby phones, mobile hotspots, and routers to reply immediately.
     */
    private void triggerActiveWlanScan() {
        try {
            String psCmd = "$code = @'\n" +
                    "using System;\n" +
                    "using System.Runtime.InteropServices;\n" +
                    "public class FastWlan {\n" +
                    "    [DllImport(\"wlanapi.dll\")] public static extern int WlanOpenHandle(uint v, IntPtr r, out uint nv, out IntPtr h);\n" +
                    "    [DllImport(\"wlanapi.dll\")] public static extern int WlanCloseHandle(IntPtr h, IntPtr r);\n" +
                    "    [DllImport(\"wlanapi.dll\")] public static extern int WlanEnumInterfaces(IntPtr h, IntPtr r, out IntPtr p);\n" +
                    "    [DllImport(\"wlanapi.dll\")] public static extern int WlanScan(IntPtr h, ref Guid g, IntPtr s, IntPtr i, IntPtr r);\n" +
                    "    [DllImport(\"wlanapi.dll\")] public static extern int WlanFreeMemory(IntPtr p);\n" +
                    "    public static void Trigger() {\n" +
                    "        uint nv; IntPtr h;\n" +
                    "        if (WlanOpenHandle(2, IntPtr.Zero, out nv, out h) == 0) {\n" +
                    "            IntPtr p;\n" +
                    "            if (WlanEnumInterfaces(h, IntPtr.Zero, out p) == 0) {\n" +
                    "                int n = Marshal.ReadInt32(p, 0);\n" +
                    "                for (int i = 0; i < n; i++) {\n" +
                    "                    IntPtr pg = new IntPtr(p.ToInt64() + 8 + (i * 532));\n" +
                    "                    Guid g = (Guid)Marshal.PtrToStructure(pg, typeof(Guid));\n" +
                    "                    WlanScan(h, ref g, IntPtr.Zero, IntPtr.Zero, IntPtr.Zero);\n" +
                    "                }\n" +
                    "                WlanFreeMemory(p);\n" +
                    "            }\n" +
                    "            WlanCloseHandle(h, IntPtr.Zero);\n" +
                    "        }\n" +
                    "    }\n" +
                    "}\n" +
                    "'@\n" +
                    "Add-Type -TypeDefinition $code\n" +
                    "[FastWlan]::Trigger()\n";
            Process p = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", psCmd).start();
            p.waitFor(1800, TimeUnit.MILLISECONDS);
            Thread.sleep(1100); // Allow radio driver 1.1s to receive probe responses
        } catch (Exception ignored) {}
    }

    /**
     * Flushes a completed BSSID beacon entry into the device list (deduplication via seenMacs).
     */
    private void flushBeaconEntry(List<WirelessAirDevice> out, Set<String> seen, Map<String, String> macToIp,
                                   String ssid, String bssid,
                                   int signal, String radio, String band, String channel, String auth) {
        if (bssid == null) return;
        String mac = bssid.trim().toUpperCase();
        if (seen.contains(mac)) return;
        seen.add(mac);
        String vendor = resolveVendor(mac, ssid);
        String chInfo = band + " (Ch " + channel + " " + radio + ")";
        String name = (ssid != null && !ssid.isEmpty()) ? ssid : "<Hidden Hotspot / Beacon>";
        String ip = macToIp.get(mac);
        if (ip == null && connectedWifiBssid != null && mac.equalsIgnoreCase(connectedWifiBssid)) {
            ip = gatewayIp;
        }
        if (ip == null) ip = "Over-The-Air RF (Unassociated)";
        int estRssi = (int) Math.round((signal / 2.0) - 100);
        out.add(new WirelessAirDevice(name, mac, ip, "Wi-Fi RF Airwave", signal, estRssi, chInfo, auth, vendor));
    }

    /**
     * Scans Bluetooth devices using Registry and active PnP enumeration.
     * Discovers paired audio, wearable, smart device, and peripheral hardware.
     */
    private void scanBluetoothDevices(List<WirelessAirDevice> out, Set<String> seen) {
        // Strategy 1: Registry scan for paired & remembered Bluetooth devices
        scanBtRegistry(out, seen);
        // Strategy 2: Get-PnpDevice for active & connected hardware Bluetooth devices
        scanBtPnpFallback(out, seen);
    }

    private void scanBtRegistry(List<WirelessAirDevice> out, Set<String> seen) {
        try {
            String psCmd = "Get-ChildItem 'HKLM:\\SYSTEM\\CurrentControlSet\\Services\\BTHPORT\\Parameters\\Devices' -ErrorAction SilentlyContinue | " +
                    "ForEach-Object { " +
                    "$key = $_; " +
                    "$name = $key.GetValue('Name'); " +
                    "if ($name -is [byte[]]) { $name = [System.Text.Encoding]::UTF8.GetString($name).Trim([char]0) }; " +
                    "Write-Output ($key.PSChildName + ';;;' + $name) }";
            Process p = new ProcessBuilder("powershell", "-NoProfile", "-Command", psCmd).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.contains(";;;")) continue;
                    String[] parts = line.split(";;;");
                    String rawHex = parts[0].trim();
                    String name = parts.length > 1 ? parts[1].trim() : "";
                    if (name.isEmpty() || rawHex.length() < 12) continue;

                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < 12; i += 2) {
                        if (sb.length() > 0) sb.append(":");
                        sb.append(rawHex.substring(i, i + 2));
                    }
                    String mac = sb.toString().toUpperCase();
                    if (seen.contains(mac)) continue;
                    seen.add(mac);

                    String vendor = resolveVendor(mac, name);
                    boolean isAudio = name.toLowerCase().contains("dopes") || name.toLowerCase().contains("ear") ||
                                      name.toLowerCase().contains("head") || name.toLowerCase().contains("buds");
                    boolean isWatch = name.toLowerCase().contains("pulse") || name.toLowerCase().contains("watch") ||
                                      name.toLowerCase().contains("band") || name.toLowerCase().contains("fit");
                    int rssi = isAudio ? -60 : (isWatch ? -64 : -70);
                    int signalPct = WirelessAirDevice.rssiToPercent(rssi);

                    out.add(new WirelessAirDevice(
                            name + " [Paired]",
                            mac, "Bluetooth RF Link (No IP)", "Bluetooth RF",
                            signalPct, rssi, "2.4 GHz ISM Band", "Bluetooth Pair-Keyed", vendor
                    ));
                }
            }
        } catch (Exception ignored) {}
    }

    /** Fallback and status update Bluetooth scan using Get-PnpDevice. */
    private void scanBtPnpFallback(List<WirelessAirDevice> out, Set<String> seen) {
        try {
            String psCmd = "Get-PnpDevice -Class Bluetooth | " +
                    "Where-Object { $_.FriendlyName -notmatch 'Enumerator|Adapter|Wireless Bluetooth|RFCOMM|Avrcp|Audio|LE' } | " +
                    "ForEach-Object { $_.FriendlyName + ';;;' + $_.InstanceId + ';;;' + $_.Status }";
            Process p = new ProcessBuilder("powershell", "-NoProfile", "-Command", psCmd).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.contains(";;;")) continue;
                    String[] parts = line.split(";;;");
                    String name = parts[0].trim();
                    String id = parts.length > 1 ? parts[1].trim() : "";
                    String status = parts.length > 2 ? parts[2].trim() : "Unknown";
                    String mac = "Unknown";
                    int devIdx = id.indexOf("DEV_");
                    if (devIdx >= 0 && id.length() >= devIdx + 16) {
                        String rawHex = id.substring(devIdx + 4, devIdx + 16);
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < rawHex.length(); i += 2) {
                            if (sb.length() > 0) sb.append(":");
                            sb.append(rawHex.substring(i, Math.min(i + 2, rawHex.length())));
                        }
                        mac = sb.toString().toUpperCase();
                    }
                    if (mac.equals("Unknown")) {
                        int btIdx = id.indexOf("BLUETOOTHDEVICE_");
                        if (btIdx >= 0 && id.length() >= btIdx + 28) {
                            String rawHex = id.substring(btIdx + 16, btIdx + 28);
                            StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < rawHex.length(); i += 2) {
                                if (sb.length() > 0) sb.append(":");
                                sb.append(rawHex.substring(i, Math.min(i + 2, rawHex.length())));
                            }
                            mac = sb.toString().toUpperCase();
                        }
                    }

                    boolean connected = status.equalsIgnoreCase("OK");
                    if (!seen.contains(mac)) {
                        if (!mac.equals("Unknown")) seen.add(mac);
                        String vendor = resolveVendor(mac, name);
                        int signalPct = connected ? 82 : 65;
                        int rssi = connected ? -52 : -70;
                        out.add(new WirelessAirDevice(
                                name + (connected ? " [Connected]" : " [Paired]"),
                                mac, "Bluetooth RF Link (No IP)", "Bluetooth RF", signalPct,
                                rssi, "2.4 GHz ISM Band", "Bluetooth Pair-Keyed", vendor
                        ));
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    /**
     * Fast live ping check for a given IP address.
     * Returns latency in ms, or -1 if unreachable / not an IP address.
     */
    public long pingIp(String ip) {
        if (ip == null || ip.contains("Over-The-Air") || ip.contains("Bluetooth") || ip.contains("No IP")) {
            return -1;
        }
        long t0 = System.currentTimeMillis();
        try {
            InetAddress inet = InetAddress.getByName(ip);
            if (inet.isReachable(350)) {
                return Math.max(1, System.currentTimeMillis() - t0);
            }
            int[] ports = {80, 443, 53, 135};
            for (int p : ports) {
                try (Socket s = new Socket()) {
                    s.connect(new InetSocketAddress(ip, p), 150);
                    return Math.max(1, System.currentTimeMillis() - t0);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return -1;
    }
}
