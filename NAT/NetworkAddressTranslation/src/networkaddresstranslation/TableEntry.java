package networkaddresstranslation;

class TableEntry {

    private String internalIP;
    private String externalIP;
    private long time;

    public TableEntry(String internalIP, String externalIP) {
        this.internalIP = internalIP;
        this.externalIP = externalIP;
    }

    public TableEntry(String internalIP, String externalIP, long time) {
        this.internalIP = internalIP;
        this.externalIP = externalIP;
        this.time = time;
    }

    public String getInternalIP() {
        return this.internalIP;
    }

    public String getExternalIP() {
        return this.externalIP;
    }

    public long getTime() {
        return this.time;
    }

    public void setExternalIP(String externalIP) {
        this.externalIP = externalIP;
    }

    @Override
    public String toString() {
        return "internalIP = " + internalIP + "; externalIP = " + externalIP;
    }
}
