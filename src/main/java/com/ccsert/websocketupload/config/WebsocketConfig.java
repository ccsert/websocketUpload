package com.ccsert.websocketupload.config;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;
import org.springframework.web.util.WebAppRootListener;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;

/**
 * ClassName: WebsocketConfig <br/>
 * Description: 开启websocket支持 <br/>
 * date: 2020/2/4 10:58<br/>
 *
 * @author ccsert<br />
 * @since JDK 1.8
 */
@Configuration
@ComponentScan
@EnableAutoConfiguration
public class WebsocketConfig implements ServletContextInitializer {
    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }

    /**
     * 配置websocket文件接受的文件最大容量
     * @param servletContext    context域对象
     * @throws ServletException 抛出异常
     */
    @Override
    public void onStartup(ServletContext servletContext) throws ServletException {
        servletContext.addListener(WebAppRootListener.class);
        servletContext.setInitParameter("org.apache.tomcat.websocket.textBufferSize","51200000");
        servletContext.setInitParameter("org.apache.tomcat.websocket.binaryBufferSize","51200000");
    }
}
