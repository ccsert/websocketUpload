package com.ccsert.websocketupload.service;

import java.util.Map;

/**
 * ClassName: saveFileI <br/>
 * Description: 保存文件接口 <br/>
 * date: 2020/2/4 17:39<br/>
 *
 * @author ccsert<br />
 * @since JDK 1.8
 */
public interface SaveFileI {
    /**
     * 生成文件路径
     * @param fileName  接收文件名
     * @return  返回文件路径
     */
    Map<String,Object> docPath(String fileName);

    /**
     * 将字节流写入文件
     * @param b 字节流数组
     * @param map  文件路径
     * @return  返回是否成功
     */
    boolean saveFileFromBytes(byte[] b, Map<String, Object> map);
}
