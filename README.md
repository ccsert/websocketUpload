遇到一个上传文件的问题，老大说使用http太慢了，因为http包含大量的请求头，刚好项目本身又集成了websocket，想着就用websocket来做文件上传。

相关技术
- springboot
- websocket
- jdk1.8

# 创建springboot项目并集成websocket
先是创建一个spring boot项目
![](https://gitee.com/ccsert/image/raw/master/springboot%E9%9B%86%E6%88%90websocket%E5%AE%9E%E7%8E%B0%E5%A4%A7%E6%96%87%E4%BB%B6%E5%88%86%E5%9D%97%E4%B8%8A%E4%BC%A0/1cbf725a-36c2-44e0-ad5d-761205287bcc.png)

我们勾选了三个依赖
分别是 Lombok，web，websocket
![](https://gitee.com/ccsert/image/raw/master/springboot%E9%9B%86%E6%88%90websocket%E5%AE%9E%E7%8E%B0%E5%A4%A7%E6%96%87%E4%BB%B6%E5%88%86%E5%9D%97%E4%B8%8A%E4%BC%A0/0c53d46b-6bc5-4069-930b-24a0ac3a98eb.png)

这是当前完整的依赖
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>2.1.2.RELEASE</version>
        <relativePath/> <!-- lookup parent from repository -->
    </parent>
    <groupId>com.ccsert</groupId>
    <artifactId>websocketupload</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>websocketupload</name>
    <description>websocket Upload project</description>

    <properties>
        <java.version>1.8</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-websocket</artifactId>
        </dependency>

        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>

        <dependency>
            <groupId>com.alibaba</groupId>
            <artifactId>fastjson</artifactId>
            <version>1.2.58</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>

</project>
```

在`websocketupload`包下创建config包，然后建一个配置类`WebsocketConfig`

然后实现`ServletContextInitializer`接口
代码如下
```java
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
```

在`websocketupload`包下创建一个`web`包然后建立一个`WebSocketServer`类

我们需要实现它的三个方法：OnOpen，OnClose，OnMessage

他们都会自动调用，类似于事件触发，含义分别是，连接建立成功时调用的方法，连接关闭时调用的方法，最后一个是接收客户端发来的消息

其中OnMessage我们后面会使用多种实现

我们先来玩一个例子，讲websocket自然是喜闻乐见聊天室,贴一下WebSocketServer的代码

```java
package com.ccsert.websocketupload.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * ClassName: webSocketServer <br/>
 * Description: websocket服务处理类 <br/>
 * date: 2020/2/4 11:05<br/>
 *
 * @author ccsert<br />
 * @since JDK 1.8
 */
@ServerEndpoint("/websocket/{sid}")
@Component
public class WebSocketServer {

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketServer.class);
    /**
     * 静态变量，用来记录当前在线连接数。应该把它设计成线程安全的。
     */
    private static int onlineCount = 0;

    /**
     * concurrent包的线程安全Set，用来存放每个客户端对应的MyWebSocket对象。
     */
    private static CopyOnWriteArraySet<WebSocketServer> webSocketSet = new CopyOnWriteArraySet<>();

    /**
     * 与某个客户端的连接会话，需要通过它来给客户端发送数据
     */
    private Session session;

    /**
     * 连接建立成功时调用的方法
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("sid") String sid) {
        this.session = session;
        //加入set中
        webSocketSet.add(this);
        //在线人数加1
        addOnlineCount();
        LOG.info(sid + "连接成功" + "----当前在线人数为：" + onlineCount);
    }

    /**
     * 连接关闭时调用的方法
     */
    @OnClose
    public void onClose(@PathParam("sid") String sid) {
        //在线人数减1
        subOnlineCount();
        //从set中删除
        webSocketSet.remove(this);
        LOG.info(sid + "已关闭连接" + "----剩余在线人数为：" + onlineCount);
    }

    /**
     * 接收客户端发送的消息时调用的方法
     *
     * @param message 接收的字符串消息
     */
    @OnMessage
    public void onMessage(String message, @PathParam("sid") String sid) {
        LOG.info(sid + "发送消息为:" + message);
        sendInfo(message, sid);
    }

    /**
     * 服务器主动提推送消息
     *
     * @param message 消息内容
     * @throws IOException io异常抛出
     */
    public void sendMessage(String message) throws IOException {

        this.session.getBasicRemote().sendText(message);
    }

    /**
     * 群发消息功能
     *
     * @param message 消息内容
     * @param sid     房间号
     */
    public static void sendInfo(String message, @PathParam("sid") String sid) {
        LOG.info("推送消息到窗口" + sid + "，推送内容:" + message);
        for (WebSocketServer item : webSocketSet) {
            try {
                //这里可以设定只推送给这个sid的，为null则全部推送
                item.sendMessage(message);
            } catch (IOException e) {
                LOG.error("消息发送失败" + e.getMessage(), e);
                return;
            }
        }
    }

    /**
     * 原子性的++操作
     */
    public static synchronized void addOnlineCount() {
        WebSocketServer.onlineCount++;
    }

    /**
     * 原子性的--操作
     */
    public static synchronized void subOnlineCount() {
        WebSocketServer.onlineCount--;
    }
}

```

这里的代码挺简单没什么好讲的

然后在建立一个html
在static目录下建立一个`websocketDemo.html`
代码如下
```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>chat room websocket</title>
    <link rel="stylesheet" href="bootstrap.css">
    <script src="https://code.jquery.com/jquery-3.4.1.min.js"></script>
</head>
<body class="container" style="width: 60%">
<div class="form-group"></br>
    <h5>聊天室</h5>
    <textarea id="message_content" class="form-control" readonly="readonly" cols="50"
              rows="10"></textarea>
</div>
<div class="form-group">
    <label for="in_user_name">⽤户姓名 &nbsp;</label>
    <input id="in_user_name" value="" class="form-control"/></br>
    <label for="in_user_name">房间名 &nbsp;</label>
    <input id="in_room_id" value="" class="form-control">
    <button id="user_join" onclick="verificationValue()" class="btn btn-success">加入聊天室</button>
    <button id="user_exit" onclick="outRoom()" class="btn btn-warning">离开聊天室</button>
</div>
<div class="form-group">
    <label for="in_room_msg">群发消息 &nbsp;</label>
    <input id="in_room_msg" value="" class="form-control"/></br>
    <button id="user_send_all" onclick="sendInfo()" class="btn btn-info">发送消息</button>
</div>
<script>
    var socket;//websocket连接
    var webSocketUrl = 'ws://127.0.0.1:8033/websocket/';//websocketi连接地址
    var roomId = "";//房间号
    var userName = "";//用户名
    //创建websocket连接
    function createWebSocketConnect(roomId) {
        if (!socket) {//避免重复连接
            console.log(roomId);
            socket = new WebSocket(webSocketUrl + roomId);
            socket.onopen = function () {
                console.log("websocket已连接");
                socket.send(userName + "已经成功加入房间");
            };
            socket.onmessage = function (e) {
                //服务端发送的消息
                $("#message_content").append(e.data + '\n');
            };
            socket.onclose = function () {
                socket.send(userName + "已经退出房间");
            }
        }
    }

    //验证用户名和房间号是否填写
    function verificationValue() {
        roomId = $("#in_room_id").val();
        userName = $("#in_user_name").val();
        if (roomId === "" || userName === "") {
            alert("请填写用户名并填写要加入的房间号");
            return;
        }
        createWebSocketConnect(roomId, userName);
    }

    //群发消息
    function sendInfo() {
        let msg = $('#in_room_msg').val();
        if (socket) {
            socket.send(userName + ":" + msg)
        }
    }

    //离开房间
    function outRoom() {
        if (socket) {
            socket.send(userName + "已退出");
            socket.close();
            $("#message_content").append(userName + "已退出");
        }
    }

</script>
</body>
</html>

```

这个也挺简单，这里引用了bootstrap.css样式和jq

然后我们就可以通过这样的一个小demo测试下聊天室的功能了，文末附上代码地址

# 使用websocket实现文件上传功能
我们仿造刚才的`WebSocketServer`在写一个websocket类

还是在web包下建立一个类，类名为`WebSocketUploadServer`

可以将原来WebSocketServer的代码复制过来，然后稍微改造一下，其实我们实现文件上传也可以直接在原来WebSocketServer的代码里直接实现，但是现在为了让代码更清晰一些，我们先将就一下

原本聊天室的情况下，一个房间里是可以有多个客户端连接的，但是文件上传我们是不允许的，假如有多个人在同一个房间，那么消息就会传到每个客户端，因为我们要做分块上传，所以这里控制每个房间只能有一个人

我们把websocketDemo.html也复制一份，改名就叫`uploadFileDemo.html`

我们原先的房间号是手动输入的，现在，我们保证每次都是不同的房间号所以，这次房间号就用随机数

当然方法有很多种，我只是提供一种简单的实现方式，不同的业务场景当然也需要不同的实现方式。不用太过死板

后台的核心代码主要是接收字节流和json消息，我们将字符串消息格式化成了json消息

下面是接收字符串的OnMessage
```java
	/**
     * 接收客户端发送的消息时调用的方法
     *
     * @param message 接收的字符串消息。该消息应当为json字符串
     */
    @OnMessage
    public void onMessage(String message, @PathParam("sid") String sid) {
        //前端传过来的消息都是一个json
        JSONObject jsonObject = JSON.parseObject(message);
        //消息类型
        String type = jsonObject.getString("type");
        //消息内容
        String data = jsonObject.getString("data");
        //判断类型是否为文件名
        if ("fileName".equals(type)) {
            LOG.info("传输文件为:" + data);
            //此处的 “.”需要进行转义
            /*String[] split = data.split("\\.");*/
            try {
                Map<String, Object> map = saveFileI.docPath(data);
                docUrl = (HashMap) map;
                this.sendMessage("ok");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else if ("fileCount".equals(type)){
            LOG.info("传输第"+data+"份");
        }
        //判断是否结束
        else if (endupload.equals(type)) {
            LOG.info("===============>传输成功");
            //返回一个文件下载地址
            String path = (String) docUrl.get("nginxPath");
            //返回客户端文件地址
            try {
                this.sendMessage(path);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
```

然后是接收字节流的
```java
    /**
     * 该方法用于接收字节流数组
     *
     * @param message 文件字节流数组
     * @param session 会话
     */
    @OnMessage
    public void onMessage(byte[] message, Session session) {
        //群发消息
        try {
            //将流写入文件
            saveFileI.saveFileFromBytes(message,docUrl);
            //文件写入成功，返回一个ok
            this.sendMessage("ok");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
```
单独看肯定是看不懂的
业务逻辑我们放在了service层进行处理

在websocketupload包下建立service包
然后建立SaveFileI接口，接口里有两个方法，一个是创建文件路径的，一个是将流数据写入文件的

```java
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
```
具体的实现我直接贴出来

```java
package com.ccsert.websocketupload.service.impl;

import com.ccsert.websocketupload.service.SaveFileI;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * ClassName: SaveFileImpl <br/>
 * Description: <br/>
 * date: 2020/2/4 19:01<br/>
 *
 * @author ccsert<br />
 * @since JDK 1.8
 */
@Service
public class SaveFileImpl implements SaveFileI {
    @Override
    public Map<String, Object> docPath(String fileName) {
        HashMap<String, Object> map = new HashMap<>();
        //根据时间生成文件夹路径
        Date date = new Date();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy/MM/dd");
        String docUrl = simpleDateFormat.format(date);
        //文件保存地址
        String path = "/data/images/" + docUrl;
        //创建文件
        File dest = new File(path+"/" + fileName);
        //如果文件已经存在就先删除掉
        if (dest.getParentFile().exists()) {
            dest.delete();
        }
        map.put("dest", dest);
        map.put("path", path+"/" + fileName);
        map.put("nginxPath","/"+docUrl+"/"+fileName);
        return map;
    }

    @Override
    public boolean saveFileFromBytes(byte[] b, Map<String, Object> map) {
        //创建文件流对象
        FileOutputStream fstream = null;
        //从map中获取file对象
        File file = (File) map.get("dest");
        //判断路径是否存在，不存在就创建
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        try {
            fstream = new FileOutputStream(file, true);
            fstream.write(b);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            if (fstream != null) {
                try {
                    fstream.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }
        return true;
    }
}

```

我们在websocket服务里注入接口的时候要注意一点，因为spring是单例的，websocket在初始化的时候就实例化了spring的bean，但是当websocket创建一个新的连接的时候spring的bean会出现null的问题，也就是它只注入了一次。这里我们这样注入可以解决这个问题。

```java
    /**
     * 注入文件保存的接口
     */
    private static SaveFileI saveFileI;

    @Autowired
    public void setSaveFileI(SaveFileI saveFileI) {
        WebSocketUploadServer.saveFileI = saveFileI;
    }
```
我们贴一下WebSocketUploadServer完整的代码

```java
package com.ccsert.websocketupload.web;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.ccsert.websocketupload.service.SaveFileI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * ClassName: WebSocketUploadServer <br/>
 * Description: <br/>
 * date: 2020/2/4 14:51<br/>
 *
 * @author ccsert<br />
 * @since JDK 1.8
 */
@ServerEndpoint("/upload/{sid}")
@Component
public class WebSocketUploadServer {
    private static final Logger LOG = LoggerFactory.getLogger(WebSocketUploadServer.class);

    /**
     * 静态变量，用来记录当前在线连接数。应该把它设计成线程安全的。
     */
    private static int onlineCount = 0;

    /**
     * concurrent包的线程安全Set，用来存放每个客户端对应的MyWebSocket对象。
     */
    private static CopyOnWriteArraySet<WebSocketUploadServer> webSocketSet = new CopyOnWriteArraySet<>();

    /**
     * 与某个客户端的连接会话，需要通过它来给客户端发送数据
     */
    private Session session;

    /**
     * 注入文件保存的接口
     */
    private static SaveFileI saveFileI;

    @Autowired
    public void setSaveFileI(SaveFileI saveFileI) {
        WebSocketUploadServer.saveFileI = saveFileI;
    }

    /**
     * 保证文件对象和文件路径的唯一性
     */
    private HashMap docUrl;

    /**
     * 结束标识判断
     */
    private String endupload = "over";

    /**
     * 连接建立成功时调用的方法
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("sid") String sid) {
        this.session = session;
        //加入set中
        webSocketSet.add(this);
        //在线人数加1
        addOnlineCount();
        LOG.info(sid + "连接成功" + "----当前在线人数为：" + onlineCount);
    }

    /**
     * 连接关闭时调用的方法
     */
    @OnClose
    public void onClose(@PathParam("sid") String sid) {
        //在线人数减1
        subOnlineCount();
        //从set中删除
        webSocketSet.remove(this);
        LOG.info(sid + "已关闭连接" + "----剩余在线人数为：" + onlineCount);
    }

    /**
     * 接收客户端发送的消息时调用的方法
     *
     * @param message 接收的字符串消息。该消息应当为json字符串
     */
    @OnMessage
    public void onMessage(String message, @PathParam("sid") String sid) {
        //前端传过来的消息都是一个json
        JSONObject jsonObject = JSON.parseObject(message);
        //消息类型
        String type = jsonObject.getString("type");
        //消息内容
        String data = jsonObject.getString("data");
        //判断类型是否为文件名
        if ("fileName".equals(type)) {
            LOG.info("传输文件为:" + data);
            //此处的 “.”需要进行转义
            /*String[] split = data.split("\\.");*/
            try {
                Map<String, Object> map = saveFileI.docPath(data);
                docUrl = (HashMap) map;
                this.sendMessage("ok");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else if ("fileCount".equals(type)){
            LOG.info("传输第"+data+"份");
        }
        //判断是否结束
        else if (endupload.equals(type)) {
            LOG.info("===============>传输成功");
            //返回一个文件下载地址
            String path = (String) docUrl.get("nginxPath");
            //返回客户端文件地址
            try {
                this.sendMessage(path);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 该方法用于接收字节流数组
     *
     * @param message 文件字节流数组
     * @param session 会话
     */
    @OnMessage
    public void onMessage(byte[] message, Session session) {
        //群发消息
        try {
            //将流写入文件
            saveFileI.saveFileFromBytes(message,docUrl);
            //文件写入成功，返回一个ok
            this.sendMessage("ok");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 服务器主动提推送消息
     *
     * @param message 消息内容
     * @throws IOException io异常抛出
     */
    public void sendMessage(String message) throws IOException {
        this.session.getBasicRemote().sendText(message);
    }

    /**
     * 群发消息功能
     *
     * @param message 消息内容
     * @param sid     房间号
     */
    public static void sendInfo(String message, @PathParam("sid") String sid) {
        LOG.info("推送消息到窗口" + sid + "，推送内容:" + message);
        for (WebSocketUploadServer item : webSocketSet) {
            try {
                //这里可以设定只推送给这个sid的，为null则全部推送
                item.sendMessage(message);
            } catch (IOException e) {
                LOG.error("消息发送失败" + e.getMessage(), e);
                return;
            }
        }
    }

    /**
     * 原子性的++操作
     */
    public static synchronized void addOnlineCount() {
        WebSocketUploadServer.onlineCount++;
    }

    /**
     * 原子性的--操作
     */
    public static synchronized void subOnlineCount() {
        WebSocketUploadServer.onlineCount--;
    }

}

```

接着就是前端的一些处理了

前端我使用了很多打标记的思想

我就直接贴代码了
```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>chat room websocket</title>
    <link rel="stylesheet" href="bootstrap.css">
    <script src="https://code.jquery.com/jquery-3.4.1.min.js"></script>
</head>
<body class="container" style="width: 60%">
<div class="form-group"></br>
    <a href="javascript:;" class="file">选择文件
        <input type="file" name="" onchange="fileOnchange()" id="fileId"> <span id="filename" style="color: red"></span>
    </a>
    <a href="javascript:;" onclick="uploadFileFun()" class="file">
        上传
    </a>
</div>
<div class="progress">
    <div id="speedP" class="progress-bar" role="progressbar" aria-valuenow="60"
         aria-valuemin="0" aria-valuemax="100" style="width: 0%;">
    </div>
</div>

<div class="form-group"></br>
    <h5>传输信息</h5>
    <textarea id="message_content" class="form-control" readonly="readonly" cols="50"
              rows="10"></textarea>
</div>

<script>
    var socket;//websocket连接
    var webSocketUrl = 'ws://127.0.0.1:8033/upload/';//websocketi连接地址
    var roomId = Number(Math.random().toString().substr(3, 3) + Date.now()).toString(36);//房间号，生成唯一的id
    var SpeedOfProgress = "";//进度
    var fileObject;//文件对象
    var uploadFlag = true;//文件上传的标识
    var paragraph = 10485760;//文件分块上传大小
    var startSize, endSize = 0;//文件的起始大小和文件的结束大小
    var i = 0;//第几部分文件
    createWebSocketConnect(roomId);//自动调用
    //创建websocket连接
    function createWebSocketConnect(roomId) {
        if (!socket) {//避免重复连接
            console.log(roomId);
            socket = new WebSocket(webSocketUrl + roomId);
            socket.onopen = function () {
                console.log("websocket已连接");
            };
            socket.onmessage = function (e) {
                if (uploadFlag) {
                    //服务端发送的消息
                    $("#message_content").append(e.data + '\n');
                }
            };
            socket.onclose = function () {
                console.log("websocket已断开");
            }
        }
    }

    //文件上传核心方法
    function uploadFileFun() {
        //文件对象赋值
        let filedata = fileObject;
        //切换保存标识的状态
        uploadFlag = false;
        //先向后台传输文件名
        let fileName = fileObject.name;
        //后台只接收字符串类型，我们定义一个字符串的json对象给后台解析
        let fileJson = {
            type: "fileName",
            data: fileName
        };

        //后台接收到文件名以后会正式开始传输文件
        socket.send(JSON.stringify(fileJson));

        //此处为文件上传的核心中的核心，涉及分块上传
        socket.onmessage = function (msg) {
            if (uploadFlag === false) {
                //开始上传文件
                if (msg.data === 'ok') {
                    //判断结束大小是否大于文件大小
                    if (endSize < filedata.size) {
                        $("#message_content").append("file.size:" + filedata.size+ '\n');
                        startSize = endSize;
                        endSize += paragraph;
                        $("#message_content").append("file.size:" + filedata.size+ '\n');
                        $("#message_content").append("startSize:" + startSize+'\n');
                        $("#message_content").append("endSize:" + endSize+'\n');
                        SpeedOfProgress = Math.round(startSize / filedata.size * 10000) / 100.00 + "%";
                        $("#speedP").css("width",SpeedOfProgress);
                        $("#message_content").append("Slice--->"+'\n');
                        var blob = filedata.slice(startSize, endSize);
                        var reader = new FileReader();
                        reader.readAsArrayBuffer(blob);
                        reader.onload = function loaded(evt) {
                            var ArrayBuffer = evt.target.result;
                            $("#message_content").append("发送文件第" + (i++) + "部分"+'\n');
                            let fileObjJson={
                                type: "fileCount",
                                data:i
                            };
                            socket.send(JSON.stringify(fileObjJson));
                            socket.send(ArrayBuffer);
                        }
                    } else {
                        $("#speedP").css("width","100%");
                        $("#message_content").append("endSize >= file.size-->" + msg.data + "<---"+'\n');
                        $("#message_content").append("endSize >= file.size-->endSize:" + endSize+'\n');
                        $("#message_content").append("endSize >= file.size-->file.size:" + filedata.size+'\n');
                        startSize = endSize = 0;
                        i = 0;
                        $("#message_content").append("发送" + filedata.name + "完毕"+'\n');
                        $("#message_content").append("发送文件完毕"+'\n');
                        socketmess={
                            type:"over",
                            message:filedata.name
                        };
                        socket.send(JSON.stringify(socketmess));//告诉socket文件传输完毕，清空计数器
                    }
                } else {
                    //此处获取
                    $("#message_content").append("文件路径为："+msg.data+'\n');
                }
            }
        }


    }

    //监听file域对象的变化，然后用于回显文件名
    function fileOnchange() {
        //从file域对象获取文件对象
        let files = $("#fileId")[0].files;
        //存储文件对象
        fileObject = files[0];
        //回显文件名
        $("#filename").html(fileObject.name);
    }

</script>
<style>
    .file {
        position: relative;
        display: inline-block;
        background: #D0EEFF;
        border: 1px solid #99D3F5;
        border-radius: 4px;
        padding: 4px 12px;
        overflow: hidden;
        color: #1E88C7;
        text-decoration: none;
        text-indent: 0;
        line-height: 20px;
    }

    .file input {
        position: absolute;
        font-size: 100px;
        right: 0;
        top: 0;
        opacity: 0;
    }

    .file:hover {
        background: #AADFFD;
        border-color: #78C3F3;
        color: #004974;
        text-decoration: none;
    }
</style>
</body>
</html>
```

加了点样式加了个进度条，大部分代码都有注释所以也不多做解释了

最后返回的这个文件地址，你可以在后台存放到文件服务器后返回给前端展示，这里我就不做过多的操作了，我没加网络地址那么文件就存在项目所在盘符的根路径下的日期格式目录下。