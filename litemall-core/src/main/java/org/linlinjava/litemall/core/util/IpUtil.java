package org.linlinjava.litemall.core.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * IP地址相关工具类
 */
public class IpUtil {

    private static final String UNKNOWN_IP = "unknown";

    private static final Log logger = LogFactory.getLog(IpUtil.class);

    public static String getIpAddr(HttpServletRequest request) {
        String ipAddress;
        try {
            ipAddress = request.getHeader("x-forwarded-for");
            if (isBlankIp(ipAddress)) {
                ipAddress = request.getHeader("Proxy-Client-IP");
            }
            if (isBlankIp(ipAddress)) {
                ipAddress = request.getHeader("WL-Proxy-Client-IP");
            }
            if (isBlankIp(ipAddress)) {
                ipAddress = resolveRemoteAddr(request);
            }
            // 经过多级代理时，取第一个 IP
            return firstIp(ipAddress);
        } catch (Exception e) {
            return "";
        }
    }

    private static boolean isBlankIp(String ip) {
        return ip == null || ip.isEmpty() || UNKNOWN_IP.equalsIgnoreCase(ip);
    }

    private static String resolveRemoteAddr(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (!"127.0.0.1".equals(remoteAddr)) {
            return remoteAddr;
        }
        try {
            // 根据网卡取本机配置的IP
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            logger.error(e.getMessage(), e);
            return "";
        }
    }

    private static String firstIp(String ipAddress) {
        if (ipAddress == null || ipAddress.length() <= 15) {
            return ipAddress;
        }
        int commaIndex = ipAddress.indexOf(",");
        return commaIndex >= 0 ? ipAddress.substring(0, commaIndex) : ipAddress;
    }
}
