package engine;

public class Key {
    private long mark;
    private long mtu;
    private String device;
    private String networkInterface;
    private String logLevel;
    private String proxy;
    private String restAPI;
    private String tcpSendBufferSize;
    private String tcpReceiveBufferSize;
    private boolean tcpModerateReceiveBuffer;

    public void setMark(long mark) {
        this.mark = mark;
    }

    public void setMTU(long mtu) {
        this.mtu = mtu;
    }

    public void setDevice(String device) {
        this.device = device;
    }

    public void setInterface(String networkInterface) {
        this.networkInterface = networkInterface;
    }

    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }

    public void setProxy(String proxy) {
        this.proxy = proxy;
    }

    public void setRestAPI(String restAPI) {
        this.restAPI = restAPI;
    }

    public void setTCPSendBufferSize(String size) {
        this.tcpSendBufferSize = size;
    }

    public void setTCPReceiveBufferSize(String size) {
        this.tcpReceiveBufferSize = size;
    }

    public void setTCPModerateReceiveBuffer(boolean moderate) {
        this.tcpModerateReceiveBuffer = moderate;
    }

    public long getMark() {
        return mark;
    }

    public long getMTU() {
        return mtu;
    }

    public String getDevice() {
        return device;
    }

    public String getInterface() {
        return networkInterface;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public String getProxy() {
        return proxy;
    }

    public String getRestAPI() {
        return restAPI;
    }

    public String getTCPSendBufferSize() {
        return tcpSendBufferSize;
    }

    public String getTCPReceiveBufferSize() {
        return tcpReceiveBufferSize;
    }

    public boolean getTCPModerateReceiveBuffer() {
        return tcpModerateReceiveBuffer;
    }
}
