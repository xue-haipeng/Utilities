package com.example.service;

import java.net.InetAddress;
import java.net.SocketException;

/**
 * Created by hpxue on 09/30/16.
 */
public class LocalHostnameAquire {

    public static String getLocalHostName() {
        String hostName;
        try {
            /**返回本地主机。*/
            InetAddress addr = InetAddress.getLocalHost();
            /**获取此 IP 地址的主机名。*/
            hostName = addr.getHostName();
        }catch(Exception ex){
            hostName = "";
        }

        return hostName;
    }

}
