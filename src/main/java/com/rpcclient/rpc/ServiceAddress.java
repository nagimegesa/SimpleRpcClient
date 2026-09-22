package com.rpcclient.rpc;

public class ServiceAddress {
    public String ip;
    public int port;
    public ServiceAddress(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }
}