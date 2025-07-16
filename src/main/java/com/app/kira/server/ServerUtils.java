package com.app.kira.server;

import lombok.experimental.UtilityClass;
import lombok.extern.java.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

@UtilityClass
@Log
public class ServerUtils {
    public static String getModule(String module) {
        return module.replace("/", "");
    }

    public static String getInstanceName() {
        try {
            return Collections.list(NetworkInterface.getNetworkInterfaces()).stream().map(NetworkInterface::getInetAddresses).map(Collections::list).flatMap(List::stream)
                    .filter(ip -> ip.isSiteLocalAddress() && !ip.isLoopbackAddress() && !ip.isLinkLocalAddress() && !ip.getHostAddress().contains(":"))
                    .findFirst().map(InetAddress::getHostAddress).orElseThrow(() -> new RuntimeException("Cannot determine server IP address"));
        } catch (IOException ex) {
            log.log(Level.WARNING, "ServerInfoService >> getServerIP >> Cannot determine server IP address", ex);
            return "";
        }
    }

    public static String getServerHostName() {
        try {
            return Collections.list(NetworkInterface.getNetworkInterfaces()).stream().map(NetworkInterface::getInetAddresses).map(Collections::list).flatMap(List::stream)
                    .filter(ip -> ip.isSiteLocalAddress() && !ip.isLoopbackAddress() && !ip.isLinkLocalAddress() && !ip.getHostAddress().contains(":"))
                    .findFirst().map(InetAddress::getHostName).orElse(getInstanceName());
        } catch (IOException ex) {
            log.log(Level.WARNING, "ServerInfoService >> getServerIP >> Cannot determine server IP address", ex);
            return "";
        }
    }
}
